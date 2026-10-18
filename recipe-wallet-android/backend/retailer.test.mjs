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

const packageProduct = (overrides = {}) => ({productId: '0001', description: 'Penne', items: [{
  itemId: 'v1', size: '16 oz', soldBy: 'UNIT', price: {regular: 1.33, promo: 0.99}, inventory: {stockLevel: 'HIGH'}, ...overrides
}]});
function basketFixture(product = packageProduct()) {
  const calls = [];
  const client = createRetailer({...credentials, fetcher: async url => {
    calls.push(url);
    return url.includes('/token') ? response({access_token: 'token', expires_in: 1800}) : response({data: product});
  }});
  return {client, calls};
}
const selection = {storeId: '70500123', items: [{productId: '0001', itemId: 'v1', quantity: 3}]};

test('basket re-fetches store-specific unit prices and ignores client totals and promotions', async () => {
  const {client, calls} = basketFixture();
  const result = await client.basket({...selection, subtotalCents: 1, items: selection.items.map(item => ({...item, priceCents: 1}))});
  assert.equal(result.subtotalCents, 399);
  assert.equal(result.items[0].quantity, 3);
  assert.equal(result.items[0].regularPriceCents, 133);
  assert.equal(result.items[0].promotionalPriceCents, 99);
  assert.equal(result.pricingScope, 'item-subtotal-only');
  assert.equal(result.finalChargeCents, null);
  assert.equal(result.taxCents, null);
  assert.equal(result.feesCents, null);
  assert.equal(result.rewardsCents, null);
  assert.equal(result.orderPlaced, false);
  assert.equal(result.stockQuantitiesVerified, false);
  const url = new URL(calls.find(url => url.includes('/products/')));
  assert.equal(url.pathname, '/v1/products/0001');
  assert.equal(url.searchParams.get('filter.locationId'), '70500123');
});

test('basket rejects duplicate variants, invalid quantities and oversized requests before network use', async () => {
  const client = createRetailer({...credentials, fetcher: () => assert.fail('network called')});
  const row = selection.items[0];
  for (const items of [null, [], [row, row], [{...row, quantity: 0}], [{...row, quantity: 1.5}], [{...row, quantity: 21}], [{...row, productId: '../token'}], Array.from({length:13}, (_, i) => ({...row, productId:String(i)}))]) {
    await assert.rejects(client.basket({storeId: selection.storeId, items}), e => e.status === 400);
  }
});

test('basket cannot turn unknown, by-weight, out-of-stock or unpriced items into a package subtotal', async () => {
  for (const overrides of [{soldBy: 'WEIGHT'}, {soldBy: undefined}, {size: null}, {price: {}}, {price: {regular: 0.001}}, {inventory: {stockLevel: 'TEMPORARILY_OUT_OF_STOCK'}}]) {
    const {client} = basketFixture(packageProduct(overrides));
    await assert.rejects(client.basket(selection), e => e.status === 409);
  }
  const {client} = basketFixture();
  await assert.rejects(client.basket({...selection, items:[{...selection.items[0], itemId:'notfound'}]}), e => e.status === 409);
});

test('basket refresh reflects changed regular prices, while unknown stock remains explicitly unverified', async () => {
  const product = packageProduct({inventory: undefined});
  const {client} = basketFixture(product);
  assert.equal((await client.basket(selection)).subtotalCents,399);
  product.items[0].price.regular = 1.49;
  const refreshed = await client.basket(selection);
  assert.equal(refreshed.subtotalCents,447);
  assert.equal(refreshed.items[0].stockLevel,null);
  assert.equal(refreshed.stockQuantitiesVerified,false);
});

test('basket preserves different variants and fetches each product only once', async () => {
  const product = packageProduct();
  product.items.push({...product.items[0], itemId:'v2', size:'32 oz', price:{regular:2.49}});
  const {client,calls} = basketFixture(product);
  const result = await client.basket({...selection, items:[...selection.items,{productId:'0001',itemId:'v2',quantity:2}]});
  assert.equal(result.subtotalCents,897);
  assert.equal(calls.filter(url=>url.includes('/products/')).length,1);
  assert.deepEqual(result.items.map(item=>item.itemId),['v1','v2']);
});

test('basket does not accept the wrong provider product or an unsafe monetary total', async () => {
  const wrong = basketFixture({...packageProduct(),productId:'other'});
  await assert.rejects(wrong.client.basket(selection), e => e.status === 502);
  const overflow = basketFixture(packageProduct({price:{regular:Number.MAX_SAFE_INTEGER/100}}));
  await assert.rejects(overflow.client.basket(selection), e => e.status === 502);
});
