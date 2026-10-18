import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';

// The test runner isolates this file in its own process. Never use real credentials.
process.env.STRIPE_SECRET_KEY = '';
process.env.STRIPE_PUBLISHABLE_KEY = '';
process.env.GEMINI_API_KEY = '';
process.env.KROGER_CLIENT_ID = '';
process.env.KROGER_CLIENT_SECRET = '';
const {handleRequest} = await import('./server.mjs');

async function request(body, {method = 'POST', url = '/grocery-quotes', raw = false} = {}) {
  const req = Readable.from([raw ? body : JSON.stringify(body)]);
  req.method = method;
  req.url = url;
  const response = {headers: {}};
  const res = {
    setHeader(name, value) { response.headers[name] = value; },
    writeHead(status) { response.status = status; },
    end(text) { response.body = JSON.parse(text); }
  };
  await handleRequest(req, res);
  return response;
}

test('grocery endpoint returns whole-package quotes without payment or AI credentials', async () => {
  const result = await request({requirements: [{ingredientId: 'pasta', quantity: 600, pantryQuantity: 100, unit: 'g'}]});
  assert.equal(result.status, 200);
  assert.equal(result.headers['Content-Type'], 'application/json');
  assert.equal(result.headers['Cache-Control'], 'no-store');
  assert.equal(result.body.demo, true);
  assert.equal(result.body.pricingScope, 'groceries-only');
  assert.deepEqual(result.body.quotes.map(q => [q.storeId, q.grocerySubtotalCents]), [
    ['market', 349], ['fresh', 418], ['pantry', 599]
  ]);
});

test('grocery endpoint reports invalid input as a client error', async () => {
  const invalid = await request({requirements: [{ingredientId: 'pasta', quantity: -1, unit: 'g'}]});
  assert.equal(invalid.status, 400);
  assert.match(invalid.body.error, /quantity/i);
  assert.equal((await request(null)).status, 400);
  assert.equal((await request('{invalid-json', {raw: true})).status, 400);
  assert.equal((await request('x'.repeat(8193), {raw: true})).status, 413);
});

test('grocery endpoint returns unavailable baskets explicitly', async () => {
  const result = await request({requirements: [{ingredientId: 'not-in-demo', quantity: 1, unit: 'each'}]});
  assert.equal(result.status, 200);
  assert.ok(result.body.quotes.every(q => !q.complete && q.grocerySubtotalCents === null));
});

test('adding grocery quotes preserves health, route, and payment-configuration behavior', async () => {
  const health = await request({}, {method: 'GET', url: '/health'});
  assert.equal(health.status, 200);
  assert.equal(health.body.stripeConfigured, false);
  assert.equal(health.body.aiConfigured, false);
  assert.equal((await request({}, {method: 'GET'})).status, 404);
  assert.equal((await request({}, {url: '/unknown'})).status, 404);
  assert.equal((await request({}, {url: '/payment-intent'})).status, 503);
  assert.equal((await request({}, {url: '/verify'})).status, 503);
});
test('pantry suggestions coexist with grocery quotes without Stripe keys',async()=>{
 const result=await request({pantryIds:['rice','avo']},{url:'/suggest'});
 assert.equal(result.status,200);
 assert.equal(result.body.suggestions[0].recipeId,'bowl');
 assert.equal((await request({pantryIds:['invented']},{url:'/suggest'})).status,400);
});

test('retailer routes distinguish unavailable credentials from invalid requests without demo fallback', async () => {
 const missing = await request({postalCode:'98105'}, {url:'/retailers/stores'});
 assert.equal(missing.status,503);
 assert.match(missing.body.error,/Kroger is not connected/);
 assert.equal(missing.body.stores,undefined);
 assert.equal((await request({postalCode:'bad'}, {url:'/retailers/stores'})).status,400);
 assert.equal((await request(null, {url:'/retailers/products'})).status,400);
 assert.equal((await request({storeId:'1',items:[]}, {url:'/retailers/basket'})).status,400);
 assert.equal((await request({storeId:'1',items:[{productId:'1',itemId:'1',quantity:1}]}, {url:'/retailers/basket'})).status,503);
 assert.equal((await request({}, {method:'GET',url:'/health'})).body.krogerConfigured,false);
});
