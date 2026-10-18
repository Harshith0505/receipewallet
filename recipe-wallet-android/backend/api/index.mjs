import {timingSafeEqual} from 'node:crypto';
import {Readable} from 'node:stream';
import {handleRequest} from '../server.mjs';

// Only the hosted entrypoint requires this token. Local npm start is unchanged.
export async function hostedRequest(req, res) {
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 'no-store');
  const send = (status, body) => { res.writeHead(status); res.end(JSON.stringify(body)); };
  const url = new URL(req.url || '/', 'http://localhost');
  const route = url.pathname === '/api/index' ? url.searchParams.get('path') : null;
  const pathname = route === null ? url.pathname : '/' + route.replace(/^\/+/, '');
  if (req.method === 'GET' && pathname === '/') {
    return send(200, {service: 'Recipe Wallet test backend', demo: true, auth: 'Bearer',
      routes: {health: 'GET /health', plan: 'POST /plan {message, sessionId?}',
        suggest: 'POST /suggest {pantryIds}', recommend: 'POST /recommend {preference}',
        groceryQuotes: 'POST /grocery-quotes', paymentIntent: 'POST /payment-intent', verify: 'POST /verify'}});
  }
  const token = process.env.AUDIT_BEARER_TOKEN || '';
  if (token.length < 32) return send(503, {error: 'Hosted audit authentication is not configured.'});
  if (!(req.method === 'GET' && pathname === '/health')) {
    const expected = Buffer.from('Bearer ' + token);
    const actual = Buffer.from(typeof req.headers?.authorization === 'string' ? req.headers.authorization : '');
    if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
      return send(401, {error: 'A valid audit bearer token is required.'});
    }
  }
  // Vercel may parse JSON before invoking a Node handler. Rebuild the stream
  // when necessary so the same body-size and JSON checks run in server.mjs.
  let input = req;
  if (req.body !== undefined) {
    const body = Buffer.isBuffer(req.body) || typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
    input = Readable.from([body]);
    input.method = req.method;
  }
  input.url = pathname;
  return handleRequest(input, res);
}

export default hostedRequest;
