import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
import {hostedRequest} from './api/index.mjs';

process.env.AUDIT_BEARER_TOKEN = 'synthetic-test-token-not-a-real-credential';
async function request(url, {method = 'GET', token, body, parsed = false} = {}) {
  const req = Readable.from(body === undefined ? [] : [JSON.stringify(body)]);
  Object.assign(req, {url, method, headers: token ? {authorization: 'Bearer ' + token} : {}});
  if (parsed) req.body = body;
  const result = {};
  await hostedRequest(req, {setHeader() {}, writeHead(status) {result.status = status;},
    end(value) {result.body = JSON.parse(value);}});
  return result;
}
test('hosted AI and payment routes require the audit token before invoking providers', async () => {
  for (const url of ['/plan', '/suggest', '/recommend', '/payment-intent', '/verify', '/api/index?path=plan']) {
    assert.equal((await request(url, {method: 'POST', body: {message: 'Dinner'}})).status, 401);
    assert.equal((await request(url, {method: 'POST', token: 'wrong', body: {}})).status, 401);
  }
});
test('hosted route rewriting and parsed JSON preserve the real grocery handler', async () => {
  const result = await request('/api/index?path=grocery-quotes', {method: 'POST',
    token: process.env.AUDIT_BEARER_TOKEN, parsed: true,
    body: {requirements: [{ingredientId: 'pasta', quantity: 600, pantryQuantity: 100, unit: 'g'}]}});
  assert.equal(result.status, 200);
  assert.equal(result.body.quotes[0].grocerySubtotalCents, 349);
});
test('public health and discovery do not disclose credentials', async () => {
  for (const url of ['/', '/health']) {
    const result = await request(url);
    assert.equal(result.status, 200);
    assert.ok(!JSON.stringify(result).includes(process.env.AUDIT_BEARER_TOKEN));
  }
});
test('hosted entrypoint fails closed without a strong configured token', async () => {
  const original = process.env.AUDIT_BEARER_TOKEN;
  try {
    delete process.env.AUDIT_BEARER_TOKEN;
    assert.equal((await request('/plan', {method: 'POST', body: {message: 'Dinner'}})).status, 503);
  } finally {process.env.AUDIT_BEARER_TOKEN = original;}
});
