import {timingSafeEqual} from 'node:crypto';
import {Readable} from 'node:stream';
import {handleRequest} from '../server.mjs';

// This audit entrypoint requires a token. The ordinary local app server is unchanged.
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
        groceryQuotes: 'POST /grocery-quotes'}});
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
  // The public audit tunnel must not expose retailer OAuth/cart or personal memory.
  if (!(req.method === 'GET' && pathname === '/health') &&
      !(req.method === 'POST' && ['/plan','/suggest','/recommend','/grocery-quotes'].includes(pathname))) {
    return send(404, {error:'This route is not available through the audit server.'});
  }
  // Limit the audit surface to caller-supplied planning data. In particular,
  // never let an audit token look up the app's saved purchase memory.
  let input = req;
  if (req.method === 'POST') {
    let body;
    if (req.body !== undefined) {
      body = Buffer.isBuffer(req.body) || typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
    } else {
      const chunks=[];let size=0;
      for await (const chunk of req) {
        const bytes=Buffer.from(chunk);size+=bytes.length;
        if(size>8192) return send(413,{error:'Request too large'});
        chunks.push(bytes);
      }
      body=Buffer.concat(chunks).toString('utf8');
    }
    if(Buffer.byteLength(body)>8192) return send(413,{error:'Request too large'});
    let value;
    try {value=JSON.parse(body);} catch {return send(400,{error:'Invalid JSON'});}
    if(value&&Object.hasOwn(value,'userId')) {
      return send(400,{error:'Personal account lookups are not available through the audit server.'});
    }
    input = Readable.from([body]);
    input.method = req.method;
  }
  input.url = pathname;
  return handleRequest(input, res);
}

export default hostedRequest;
