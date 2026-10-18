import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRetailer, normalizeProducts} from './retailer.mjs';

const response = (body, status = 200) => new Response(JSON.stringify(body), {status});
const credentials = {clientId: 'test-client', clientSecret: 'test-secret'};
function fixture() {
  const calls = [];
  const client = createRetailer({...credentials, fetcher: async (url, options) => {
    calls.push({url, options});
    if (url.includes('/token')) return response({access_token: 'private-token', expires_in: 1800});
    if (url.includes('/locations?')) return response({data: [{locationId: '70500123', name: 'QFC', address: {addressLine1: '123 Test St', city: 'Seattle', state: 'WA', zipCode: '98105'}}]});
    return response({data: [{productId: '0001', description: 'Pasta', items: [{itemId: '1', size: '16 oz', price: {regular: 2.49, promo: 1.99}}]}]});
  }});
  return {client, calls};
}

test('real catalog access requires backend credentials and rejects invalid input before network use', async () => {
  const client = createRetailer({clientId: '', clientSecret: '', fetcher: () => assert.fail('network called')});
  await assert.rejects(client.stores({postalCode: '98105'}), e => e.status === 503 && /not connected/.test(e.message));
  for (const value of [null, {}, {postalCode: '98105&x=y'}, {postalCode: 98105}]) {
    await assert.rejects(client.stores(value), e => e.status === 400);
  }
  for (const value of [null, {}, {storeId: '../token', query: 'milk'}, {storeId: '123', query: 'x'}, {storeId: '123', query: 'x'.repeat(81)}]) {
    await assert.rejects(client.products(value), e => e.status === 400);
  }
});

test('searches stores by ZIP and products by the selected location; shares OAuth token', async () => {
  const {client, calls} = fixture();
  const [stores, products] = await Promise.all([client.stores({postalCode: '98105'}), client.products({storeId: '70500123', query: 'pasta & rice'})]);
  assert.equal(calls.filter(c => c.url.includes('/token')).length, 1);
  assert.equal(new URLSearchParams(calls[0].options.body).get('scope'), 'product.compact');
  assert.equal(stores.demo, false);
  assert.equal(stores.stores[0].address, '123 Test St, Seattle, WA, 98105');
  assert.equal(stores.stores[0].rating, undefined);
  const productCall = calls.find(c => c.url.includes('/products?'));
  assert.equal(new URL(productCall.url).searchParams.get('filter.locationId'), '70500123');
  assert.equal(new URL(productCall.url).searchParams.get('filter.term'), 'pasta & rice');
  assert.equal(products.products[0].regularPriceCents, 249);
  assert.equal(products.products[0].promotionalPriceCents, 199);
  assert.equal(products.products[0].stockLevel, null);
  assert.ok(!JSON.stringify(products).includes('private-token'));
});

test('does not invent price, stock, package size, or fulfillment and preserves variants', () => {
  const result = normalizeProducts([{productId: '1', description: 'Apples', items: [
    {itemId: 'a', price: {regular: 0, promo: -1}},
    {itemId: 'b', size: '1 lb', soldBy: 'WEIGHT', inventory: {stockLevel: 'LOW'}, price: {regular: 2.1}, fulfillment: {curbside: false}}
  ]}]);
  assert.equal(result.length, 2);
  assert.equal(result[0].regularPriceCents, null);
  assert.equal(result[0].promotionalPriceCents, null);
  assert.equal(result[0].packageSize, null);
  assert.equal(result[0].deliveryAvailable, null);
  assert.equal(result[1].regularPriceCents, 210);
  assert.equal(result[1].soldBy, 'WEIGHT');
  assert.equal(result[1].pickupAvailable, false);
});

test('expired tokens refresh and rejected tokens retry only once', async () => {
  let time = 0, tokens = 0, products = 0;
  const client = createRetailer({...credentials, now: () => time, fetcher: async url => {
    if (url.includes('/token')) { tokens++; return response({access_token: `token-${tokens}`, expires_in: 120}); }
    products++;
    return products === 2 ? response({}, 401) : response({data: []});
  }});
  await client.products({storeId: '1', query: 'milk'});
  await client.products({storeId: '1', query: 'milk'});
  assert.equal(tokens, 2);
  assert.equal(products, 3);
  time = 61000;
  await client.products({storeId: '1', query: 'milk'});
  assert.equal(tokens, 3);
});

test('provider failures are sanitized, no demo fallback, and failed authentication can retry', async () => {
  for (const status of [401, 403, 429, 500]) {
    const client = createRetailer({...credentials, fetcher: async () => response({error: 'private-details'}, status)});
    await assert.rejects(client.stores({postalCode: '98105'}), e => !e.message.includes('private-details') && e.status === (status === 429 ? 429 : status === 500 ? 502 : 503));
  }
  let calls = 0;
  const client = createRetailer({...credentials, fetcher: async url => {
    calls++;
    if (calls === 1) throw new Error('test-secret');
    return url.includes('/token') ? response({access_token: 'token', expires_in: 1800}) : response({data: []});
  }});
  await assert.rejects(client.stores({postalCode: '98105'}), e => e.status === 502 && !e.message.includes('test-secret'));
  assert.deepEqual((await client.stores({postalCode: '98105'})).stores, []);
});

test('malformed provider responses fail clearly instead of fabricating a catalog', async () => {
  for (const bad of [{access_token: ''}, {access_token: 'token', expires_in: 'bad'}]) {
    const client = createRetailer({...credentials, fetcher: async () => response(bad)});
    await assert.rejects(client.stores({postalCode: '98105'}), e => e.status === 502);
  }
  const client = createRetailer({...credentials, fetcher: async url => url.includes('/token') ? response({access_token: 'token', expires_in: 1800}) : response({data: null})});
  await assert.rejects(client.stores({postalCode: '98105'}), e => e.status === 502);
});
