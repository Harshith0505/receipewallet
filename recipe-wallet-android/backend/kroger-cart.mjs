import {randomBytes, createHash} from 'node:crypto';
import {RetailerFailure} from './retailer.mjs';

const CART_URL = 'https://www.kroger.com/cart';
const digest = value => createHash('sha256').update(value).digest('hex');
const wrap = data => ({provider: 'kroger', demo: false, ...data});
class CartFailure extends RetailerFailure {
  constructor(status, message, transferStatus) { super(status, message); this.transferStatus = transferStatus; }
}
function transferError(status) {
  return new CartFailure(409, status === 'sending' ? 'The cart transfer is still in progress. Check the connection status.' :
    'The cart transfer outcome is unknown. Open your Kroger cart and check it before reconnecting or adding these products again.', status);
}

export function createKrogerCart({retailer, clientId = process.env.KROGER_CLIENT_ID || '',
  clientSecret = process.env.KROGER_CLIENT_SECRET || '', redirectUri = process.env.KROGER_REDIRECT_URI || '',
  fetcher = fetch, now = Date.now} = {}) {
  const sessions = new Map(), states = new Map();
  let validRedirect = false;
  try {
    const uri = new URL(redirectUri);
    validRedirect = !uri.username && !uri.password && !uri.search && !uri.hash && uri.pathname === '/kroger/callback' &&
      (uri.protocol === 'https:' || (uri.origin === 'http://127.0.0.1:4242'));
  } catch { /* A missing callback is reported as setup, never guessed from request headers. */ }
  const configured = Boolean(clientId.trim() && clientSecret.trim() && validRedirect);
  function clean() {
    for (const [id, session] of sessions) if (session.expiresAt <= now()) { sessions.delete(id); states.delete(session.state); }
  }
  function sessionFor(input) {
    clean();
    if (typeof input?.sessionToken !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(input.sessionToken)) throw new CartFailure(401, 'Connect your Kroger account again.');
    const session = sessions.get(digest(input.sessionToken));
    if (!session) throw new CartFailure(401, 'This Kroger connection expired. Connect again.');
    return session;
  }
  const transferView = record => record ? {attemptId: record.attemptId, status: record.status, itemCount: record.itemCount} : null;
  return {
    configured,
    connect() {
      clean();
      if (!configured) throw new CartFailure(503, 'Kroger cart setup is incomplete. Enable the public Cart API, register the callback URL, and set KROGER_REDIRECT_URI in backend/.env.');
      if (sessions.size >= 200) throw new CartFailure(503, 'Too many pending Kroger connections. Please try again later.');
      const sessionToken = randomBytes(32).toString('base64url');
      const state = randomBytes(32).toString('base64url');
      const verifier = randomBytes(32).toString('base64url');
      const id = digest(sessionToken), expiresAt = now() + 20 * 60 * 1000;
      sessions.set(id, {state, verifier, expiresAt, status: 'pending', token: null, tokenExpiresAt: 0,
        records: new Map(), transferredQuotes: new Set(), locked: false, lastTransfer: null});
      states.set(state, id);
      const params = new URLSearchParams({response_type: 'code', client_id: clientId.trim(), redirect_uri: redirectUri,
        scope: 'cart.basic:write', state, code_challenge: createHash('sha256').update(verifier).digest('base64url'), code_challenge_method: 'S256'});
      return wrap({status: 'pending', sessionToken, expiresAt, authorizationUrl: `https://api.kroger.com/v1/connect/oauth2/authorize?${params}`});
    },
    async callback(params) {
      clean();
      const state = params.get('state');
      if (params.getAll('state').length !== 1 || typeof state !== 'string') throw new CartFailure(400, 'Invalid authorization callback.');
      const id = states.get(state), session = sessions.get(id);
      if (!session || session.status !== 'pending') throw new CartFailure(400, 'Authorization expired or already handled.');
      states.delete(state);
      session.status = 'failed'; // One-time callback, including cancellations and failed exchanges.
      if (params.has('error')) return {connected: false};
      const code = params.get('code');
      if (params.getAll('code').length !== 1 || typeof code !== 'string' || !code || code.length > 2048) throw new CartFailure(400, 'Invalid authorization code.');
      let response;
      const verifier = session.verifier;
      session.verifier = null;
      try {
        response = await fetcher('https://api.kroger.com/v1/connect/oauth2/token', {method: 'POST', redirect: 'error',
          headers: {Authorization: `Basic ${Buffer.from(`${clientId.trim()}:${clientSecret.trim()}`).toString('base64')}`, 'Content-Type': 'application/x-www-form-urlencoded'},
          body: new URLSearchParams({grant_type: 'authorization_code', code, redirect_uri: redirectUri, code_verifier: verifier}).toString(), signal: AbortSignal.timeout(15000)});
      } catch { throw new CartFailure(502, 'Kroger sign-in could not be completed. Connect again.'); }
      if (!response.ok) throw new CartFailure(503, 'Kroger sign-in was denied. Check Cart API access and the registered callback URL.');
      let token;
      try { token = await response.json(); } catch { throw new CartFailure(502, 'Kroger returned an invalid sign-in response.'); }
      if (!token.access_token || typeof token.access_token !== 'string' || token.token_type?.toLowerCase() !== 'bearer' ||
          !Number.isFinite(token.expires_in) || token.expires_in <= 15 ||
          (token.scope !== undefined && (typeof token.scope !== 'string' || !token.scope.split(' ').includes('cart.basic:write')))) {
        throw new CartFailure(503, 'Kroger did not grant the required cart permission.');
      }
      session.token = token.access_token;
      session.tokenExpiresAt = Math.min(session.expiresAt, now() + (token.expires_in - 15) * 1000);
      session.status = 'connected';
      return {connected: true};
    },
    status(input) {
      const session = sessionFor(input);
      if (session.status === 'connected' && session.tokenExpiresAt <= now()) { session.status = 'expired'; session.token = null; }
      return wrap({status: session.status, expiresAt: session.expiresAt, transfer: transferView(session.lastTransfer), cartUrl: CART_URL});
    },
    disconnect(input) {
      const session = sessionFor(input);
      states.delete(session.state);
      sessions.delete(digest(input.sessionToken));
      return wrap({status: 'disconnected'});
    },
    async add(input) {
      const session = sessionFor(input);
      if (session.status !== 'connected' || session.tokenExpiresAt <= now()) throw new CartFailure(401, 'Connect your Kroger account before adding products.');
      if (typeof input.attemptId !== 'string' || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(input.attemptId)) throw new CartFailure(400, 'Invalid cart transfer attempt.');
      if (!['PICKUP', 'DELIVERY'].includes(input.fulfillment)) throw new CartFailure(400, 'Choose pickup or delivery.');
      const previous = session.records.get(input.attemptId);
      if (previous) {
        if (previous.quoteToken !== input.quoteToken || previous.fulfillment !== input.fulfillment) throw new CartFailure(409, 'This transfer attempt belongs to a different basket.');
        if (['sending', 'unknown'].includes(previous.status)) throw transferError(previous.status);
        if (previous.status === 'failed') throw new CartFailure(409, 'The earlier transfer was rejected. Reconnect before trying again.');
        return wrap({status: 'added', itemCount: previous.itemCount, cartUrl: CART_URL, orderPlaced: false, paymentMade: false});
      }
      if (session.lastTransfer?.status === 'unknown') throw transferError('unknown');
      if (session.lastTransfer?.status === 'added') throw new CartFailure(409, 'This connection already transferred a basket. Open your Kroger cart to review it.');
      if (session.locked) throw transferError('sending');
      if (session.records.size >= 20) throw new CartFailure(409, 'Reconnect before starting another transfer.');
      const reviewed = retailer.readQuote(input.quoteToken);
      const quoteHash = digest(input.quoteToken);
      if (session.transferredQuotes.has(quoteHash)) throw new CartFailure(409, 'This reviewed basket has already been submitted. Check your Kroger cart.');
      session.locked = true;
      try {
        const fresh = await retailer.basket({storeId: reviewed.storeId, items: reviewed.items.map(({productId, itemId, quantity}) => ({productId, itemId, quantity}))});
        if (fresh.subtotalCents !== reviewed.subtotalCents || fresh.items.some((item, index) =>
          item.productId !== reviewed.items[index].productId || item.itemId !== reviewed.items[index].itemId || item.quantity !== reviewed.items[index].quantity ||
          item.regularPriceCents !== reviewed.items[index].price || item.upc !== reviewed.items[index].upc)) {
          throw new CartFailure(409, 'Retailer prices or products changed. Refresh and review the live basket before transferring it.');
        }
        if (fresh.items.some(item => !/^\d{8,14}$/.test(item.upc || '') || item.itemId !== item.upc)) {
          throw new CartFailure(409, 'A selected variant cannot be matched to an exact retailer UPC. Choose another product.');
        }
        const availability = input.fulfillment === 'PICKUP' ? 'pickupAvailable' : 'deliveryAvailable';
        if (fresh.items.some(item => item[availability] !== true)) throw new CartFailure(409, 'The retailer has not confirmed this fulfillment option for every product. Review the products or choose another option.');
        const items = fresh.items.map(item => ({upc: item.upc, quantity: item.quantity, modality: input.fulfillment}));
        if (new Set(items.map(item => item.upc)).size !== items.length) throw new CartFailure(409, 'Several selected products share a UPC. Match those ingredients to the same product and review the combined package count.');
        const record = {attemptId: input.attemptId, quoteToken: input.quoteToken, fulfillment: input.fulfillment,
          status: 'sending', itemCount: items.reduce((total, item) => total + item.quantity, 0)};
        session.records.set(input.attemptId, record); session.lastTransfer = record;
        let response;
        try {
          response = await fetcher('https://api.kroger.com/v1/cart/add', {method: 'PUT', redirect: 'error',
            headers: {Authorization: `Bearer ${session.token}`, 'Content-Type': 'application/json'},
            body: JSON.stringify({items}), signal: AbortSignal.timeout(15000)});
        } catch { record.status = 'unknown'; session.transferredQuotes.add(quoteHash); throw transferError('unknown'); }
        if (response.status !== 204) {
          if (response.status >= 400 && response.status < 500) {
            record.status = 'failed';
            if ([401, 403].includes(response.status)) { session.status = 'failed'; session.token = null; }
            throw new CartFailure(409, 'Kroger rejected the cart transfer. Check Cart API permission, products and fulfillment before reconnecting.');
          }
          record.status = 'unknown'; session.transferredQuotes.add(quoteHash); throw transferError('unknown');
        }
        record.status = 'added'; session.transferredQuotes.add(quoteHash);
        return wrap({status: 'added', itemCount: record.itemCount, cartUrl: CART_URL, orderPlaced: false, paymentMade: false});
      } finally { session.locked = false; }
    }
  };
}

export const krogerReturnHtml = () => '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Recipe Wallet</title><body><h1>Return to Recipe Wallet</h1><p>Check your connection in the app, then review your products before transferring them. Signing in does not add products to your cart.</p><p><a href="com.recipewallet.app://kroger/return">Return to Recipe Wallet</a></p><p>No order or payment has been placed by this sign-in.</p></body></html>';
