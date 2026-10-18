// Official Kroger catalog access. Credentials and OAuth tokens stay on the server.
export class RetailerFailure extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

function inputText(value, pattern, message) {
  if (typeof value !== 'string' || !pattern.test(value.trim())) throw new RetailerFailure(400, message);
  return value.trim();
}

function cents(value) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return null;
  const result = Math.round(value * 100);
  return Number.isSafeInteger(result) && result > 0 ? result : null;
}

export function normalizeProducts(rows) {
  return rows.flatMap(product => {
    if (!product?.productId || !product.description || !Array.isArray(product.items)) return [];
    return product.items.flat().filter(item => item?.itemId).map(item => ({
      productId: String(product.productId), itemId: String(item.itemId),
      name: product.description, brand: product.brand || null, packageSize: item.size || null,
      regularPriceCents: cents(item.price?.regular), promotionalPriceCents: cents(item.price?.promo),
      // Missing inventory is unknown, never interpreted as in stock.
      stockLevel: item.inventory?.stockLevel || null,
      soldBy: item.soldBy || null,
      pickupAvailable: typeof item.fulfillment?.curbside === 'boolean' ? item.fulfillment.curbside : null,
      deliveryAvailable: typeof item.fulfillment?.delivery === 'boolean' ? item.fulfillment.delivery : null
    }));
  });
}

export function createRetailer({clientId = process.env.KROGER_CLIENT_ID || '',
  clientSecret = process.env.KROGER_CLIENT_SECRET || '', fetcher = fetch, now = Date.now} = {}) {
  let token = null, expires = 0, pendingToken = null;
  const configured = Boolean(clientId.trim() && clientSecret.trim());
  async function checkedFetch(path, options) {
    try {
      return await fetcher(`https://api.kroger.com/v1/${path}`, {
        ...options, redirect: 'error', signal: AbortSignal.timeout(15000)
      });
    } catch { throw new RetailerFailure(502, 'Kroger could not be reached. Please try again.'); }
  }
  async function json(response) {
    try { return await response.json(); }
    catch { throw new RetailerFailure(502, 'Kroger returned an unreadable response. Please try again.'); }
  }
  function failure(response) {
    if (response.status === 429) return new RetailerFailure(429, 'Kroger request limit reached. Please try again later.');
    if ([401, 403].includes(response.status)) return new RetailerFailure(503, 'Kroger access was denied. Check the backend credentials and Products/Locations API access.');
    return new RetailerFailure(502, 'Kroger could not complete this search. Please try again.');
  }
  async function getToken() {
    if (!configured) throw new RetailerFailure(503, 'Kroger is not connected yet. Add KROGER_CLIENT_ID and KROGER_CLIENT_SECRET to backend/.env, then restart the backend.');
    if (token && now() < expires) return token;
    if (pendingToken) return pendingToken;
    pendingToken = (async () => {
      const response = await checkedFetch('connect/oauth2/token', {method: 'POST', headers: {
        Authorization: `Basic ${Buffer.from(`${clientId.trim()}:${clientSecret.trim()}`).toString('base64')}`,
        'Content-Type': 'application/x-www-form-urlencoded'
      }, body: new URLSearchParams({grant_type: 'client_credentials', scope: 'product.compact'}).toString()});
      if (!response.ok) throw failure(response);
      const body = await json(response);
      if (typeof body.access_token !== 'string' || !body.access_token || !Number.isFinite(body.expires_in) || body.expires_in <= 0) {
        throw new RetailerFailure(502, 'Kroger did not return a valid access token.');
      }
      token = body.access_token;
      expires = now() + Math.max(0, Math.min(body.expires_in, 86400) - 60) * 1000;
      return token;
    })();
    try { return await pendingToken; } finally { pendingToken = null; }
  }
  async function get(path, params, list = true) {
    const url = `${path}?${new URLSearchParams(params)}`;
    let usedToken = await getToken();
    let response = await checkedFetch(url, {headers: {Authorization: `Bearer ${usedToken}`}});
    if (response.status === 401) {
      if (token === usedToken) { token = null; expires = 0; }
      usedToken = await getToken();
      response = await checkedFetch(url, {headers: {Authorization: `Bearer ${usedToken}`}});
    }
    if (!response.ok) throw failure(response);
    const body = await json(response);
    if (list ? !Array.isArray(body.data) : !body.data || typeof body.data !== 'object' || Array.isArray(body.data)) throw new RetailerFailure(502, 'Kroger returned an unexpected catalog response.');
    return body.data;
  }
  return {
    configured,
    async stores(input) {
      const postalCode = inputText(input?.postalCode, /^\d{5}$/, 'Enter a five-digit US ZIP code.');
      const rows = await get('locations', {'filter.zipCode.near': postalCode, 'filter.radiusInMiles': '10', 'filter.limit': '5'});
      return {provider: 'kroger', demo: false, fetchedAt: new Date(now()).toISOString(), stores: rows
        .filter(row => row?.locationId && row.name).map(row => ({id: String(row.locationId), name: row.name,
          address: [row.address?.addressLine1, row.address?.city, row.address?.state, row.address?.zipCode].filter(Boolean).join(', ')
        }))};
    },
    async products(input) {
      const storeId = inputText(input?.storeId, /^[A-Za-z0-9]{1,32}$/, 'Choose a valid Kroger store.');
      const query = inputText(input?.query, /^[^\x00-\x1f\x7f]{2,80}$/, 'Enter a product search of 2–80 characters.');
      const rows = await get('products', {'filter.locationId': storeId, 'filter.term': query, 'filter.limit': '10'});
      return {provider: 'kroger', demo: false, storeId, query, currency: 'usd', fetchedAt: new Date(now()).toISOString(),
        pricingScope: 'retailer-listed-product-prices', products: normalizeProducts(rows)};
    },
    async basket(input) {
      const storeId = inputText(input?.storeId, /^[A-Za-z0-9]{1,32}$/, 'Choose a valid Kroger store.');
      if (!Array.isArray(input.items) || input.items.length < 1 || input.items.length > 12) {
        throw new RetailerFailure(400, 'Choose 1–12 distinct product variants.');
      }
      const seen = new Set();
      const selections = input.items.map(item => {
        const productId = inputText(item?.productId, /^[A-Za-z0-9]{1,32}$/, 'Choose a valid product.');
        const itemId = inputText(item?.itemId, /^[A-Za-z0-9]{1,32}$/, 'Choose a valid product variant.');
        if (!Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 20) throw new RetailerFailure(400, 'Choose 1–20 packages per product variant.');
        const key = `${productId}:${itemId}`;
        if (seen.has(key)) throw new RetailerFailure(400, 'Combine duplicate product variants before requesting a basket.');
        seen.add(key);
        return {productId, itemId, quantity: item.quantity};
      });
      // Re-fetch by product ID at this location. Client-supplied prices are never trusted.
      const details = new Map();
      const productIds = [...new Set(selections.map(item => item.productId))];
      for (let offset = 0; offset < productIds.length; offset += 3) {
        await Promise.all(productIds.slice(offset, offset + 3).map(async productId => {
          const row = await get(`products/${encodeURIComponent(productId)}`, {'filter.locationId': storeId}, false);
          if (String(row.productId) !== productId) throw new RetailerFailure(502, 'Kroger returned a different product. Please search again.');
          details.set(productId, normalizeProducts([row]));
        }));
      }
      let subtotalCents = 0;
      const items = selections.map(selection => {
        const product = details.get(selection.productId).find(item => item.itemId === selection.itemId);
        if (!product || product.regularPriceCents === null || product.soldBy !== 'UNIT' || !product.packageSize) {
          throw new RetailerFailure(409, 'A selected product no longer has a confirmed unit price and package size. Choose another product.');
        }
        if (/OUT.?OF.?STOCK|UNAVAILABLE/i.test(product.stockLevel || '')) {
          throw new RetailerFailure(409, `${product.name} is reported unavailable. Choose another product.`);
        }
        const lineSubtotalCents = product.regularPriceCents * selection.quantity;
        subtotalCents += lineSubtotalCents;
        if (!Number.isSafeInteger(lineSubtotalCents) || !Number.isSafeInteger(subtotalCents)) throw new RetailerFailure(502, 'The retailer price is outside the supported range.');
        return {...product, quantity: selection.quantity, lineSubtotalCents};
      });
      return {provider: 'kroger', demo: false, storeId, currency: 'usd', fetchedAt: new Date(now()).toISOString(),
        pricingScope: 'item-subtotal-only', subtotalCents, items, taxCents: null, feesCents: null,
        finalChargeCents: null, rewardsCents: null, orderPlaced: false, stockQuantitiesVerified: false};
    }
  };
}
