import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {createRetailer} from './retailer.mjs';
import {createKrogerCart} from './kroger-cart.mjs';

const json = (body, status = 200) => new Response(JSON.stringify(body), {status});
const credentials = {clientId: 'fixture-client', clientSecret: 'fixture-secret'};
const redirectUri = 'http://127.0.0.1:4242/kroger/callback';
const input = {storeId: '70500807', items: [{productId: '0001111085033', itemId: '0001111085033', quantity: 2}]};
const attemptId = '12345678-1234-1234-1234-123456789abc';
function fixture() {
  const calls = [];
  let time = 0, price = 1.33, cartMode = 204, grantScope = 'cart.basic:write', pickup = true;
  const fetcher = async (url, options) => {
    calls.push({url, options});
    if (url.endsWith('/token')) return json(new URLSearchParams(options.body).get('grant_type') === 'authorization_code' ?
      {access_token:'customer-private-token',token_type:'bearer',expires_in:1800,scope:grantScope} : {access_token:'catalog-private-token',expires_in:1800});
    if (url.includes('/products/')) return json({data:{productId:input.items[0].productId,upc:input.items[0].itemId,description:'Penne',items:[{itemId:input.items[0].itemId,size:'16 oz',soldBy:'UNIT',price:{regular:price},inventory:{stockLevel:'HIGH'},fulfillment:{curbside:pickup,delivery:true}}]}});
    if (url.endsWith('/cart/add')) {
      if (cartMode === 'timeout') throw new Error('private upstream error');
      return cartMode === 204 ? new Response(null,{status:204}) : json({},cartMode);
    }
    assert.fail(`Unexpected fixture URL ${url}`);
  };
  const retailer = createRetailer({...credentials,fetcher,now:()=>time});
  const cart = createKrogerCart({...credentials,retailer,redirectUri,fetcher,now:()=>time});
  async function connect() {
    const flow = cart.connect();
    const state = new URL(flow.authorizationUrl).searchParams.get('state');
    await cart.callback(new URLSearchParams({state,code:'fixture-code'}));
    return flow;
  }
  return {cart,retailer,calls,connect,setTime:value=>time=value,setPrice:value=>price=value,
    setCartMode:value=>cartMode=value,setScope:value=>grantScope=value,setPickup:value=>pickup=value};
}

test('authorization uses separate state and session proof, PKCE, exact redirect and least cart scope', async () => {
  const f = fixture(), flow = f.cart.connect();
  const params = new URL(flow.authorizationUrl).searchParams;
  assert.notEqual(flow.sessionToken,params.get('state'));
  assert.equal(params.get('scope'),'cart.basic:write');
  assert.equal(params.get('redirect_uri'),redirectUri);
  assert.equal(params.get('code_challenge_method'),'S256');
  assert.equal(f.cart.status(flow).status,'pending');
  await f.cart.callback(new URLSearchParams({state:params.get('state'),code:'code'}));
  const call = f.calls.find(call=>call.url.endsWith('/token'));
  const body = new URLSearchParams(call.options.body);
  assert.equal(createHash('sha256').update(body.get('code_verifier')).digest('base64url'),params.get('code_challenge'));
  assert.equal(body.get('grant_type'),'authorization_code');
  const status = f.cart.status(flow);
  assert.equal(status.status,'connected');
  assert.ok(!JSON.stringify(status).includes('private-token'));
  assert.ok(!flow.authorizationUrl.includes('fixture-secret'));
  assert.equal(f.calls.filter(call=>call.url.endsWith('/cart/add')).length,0);
});

test('rejects forged, duplicate, expired and replayed callbacks and supports cancellation without token exchange', async () => {
  const f = fixture(), flow = f.cart.connect(), state = new URL(flow.authorizationUrl).searchParams.get('state');
  await assert.rejects(f.cart.callback(new URLSearchParams({state:'forged',code:'code'})));
  await assert.rejects(f.cart.callback(new URLSearchParams(`state=${state}&state=${state}&code=code`)));
  await f.cart.callback(new URLSearchParams({state,error:'access_denied'}));
  assert.equal(f.cart.status(flow).status,'failed');
  assert.equal(f.calls.length,0);
  await assert.rejects(f.cart.callback(new URLSearchParams({state,code:'replayed'})));
  const expired = f.cart.connect();
  f.setTime(20*60*1000+1);
  await assert.rejects(f.cart.callback(new URLSearchParams({state:new URL(expired.authorizationUrl).searchParams.get('state'),code:'code'})));
  assert.throws(()=>f.cart.status(expired),e=>e.status===401);
});

test('token grant without cart permission never connects', async () => {
  const f = fixture();f.setScope('product.compact');
  await assert.rejects(f.connect(),e=>e.status===503);
});

test('a reviewed transfer rechecks prices then adds exact UPC counts once, without ordering or paying', async () => {
  const f = fixture(), flow = await f.connect(), quote = await f.retailer.basket(input);
  const transfer = {...flow,quoteToken:quote.quoteToken,attemptId,fulfillment:'PICKUP'};
  const result = await f.cart.add(transfer);
  assert.equal(result.status,'added');assert.equal(result.itemCount,2);
  assert.equal(result.orderPlaced,false);assert.equal(result.paymentMade,false);
  const call = f.calls.find(call=>call.url.endsWith('/cart/add'));
  assert.equal(call.options.method,'PUT');
  assert.equal(call.options.headers.Authorization,'Bearer customer-private-token');
  assert.deepEqual(JSON.parse(call.options.body),{items:[{upc:input.items[0].itemId,quantity:2,modality:'PICKUP'}]});
  assert.equal((await f.cart.add(transfer)).status,'added');
  assert.equal(f.calls.filter(call=>call.url.endsWith('/cart/add')).length,1);
  await assert.rejects(f.cart.add({...transfer,attemptId:'22345678-1234-1234-1234-123456789abc'}),e=>e.status===409);
  await assert.rejects(f.cart.add({...transfer,fulfillment:'DELIVERY'}),e=>e.status===409);
});

test('freshness, changed prices and fulfillment prevent writes before cart mutation', async () => {
  const f = fixture(), flow = await f.connect(), quote = await f.retailer.basket(input);
  const transfer = {...flow,quoteToken:quote.quoteToken,attemptId,fulfillment:'PICKUP'};
  f.setPrice(1.49);
  await assert.rejects(f.cart.add(transfer),e=>e.status===409 && /prices/.test(e.message));
  f.setPrice(1.33);f.setPickup(false);
  await assert.rejects(f.cart.add(transfer),e=>e.status===409 && /fulfillment/.test(e.message));
  f.setTime(10*60*1000+1);
  await assert.rejects(f.cart.add(transfer),e=>e.status===409 && /expired/.test(e.message));
  assert.equal(f.calls.filter(call=>call.url.endsWith('/cart/add')).length,0);
});

test('quote signatures prevent changing packages or prices, and restart invalidates old quotes', async () => {
  const f = fixture(), quote = await f.retailer.basket(input);
  const [payload,signature] = quote.quoteToken.split('.');
  const changed = JSON.parse(Buffer.from(payload,'base64url'));
  changed.items[0].quantity = 20;
  assert.throws(()=>f.retailer.readQuote(`${Buffer.from(JSON.stringify(changed)).toString('base64url')}.${signature}`),e=>e.status===400);
  assert.throws(()=>f.retailer.readQuote(`${payload}.${'é'.repeat(43)}`),e=>e.status===400);
  assert.throws(()=>fixture().retailer.readQuote(quote.quoteToken),e=>e.status===400);
});

test('uncertain writes never retry automatically, including a different attempt or basket', async () => {
  for (const mode of ['timeout',500,202]) {
    const f = fixture(), flow = await f.connect(), quote = await f.retailer.basket(input);
    f.setCartMode(mode);
    const transfer = {...flow,quoteToken:quote.quoteToken,attemptId,fulfillment:'PICKUP'};
    await assert.rejects(f.cart.add(transfer),e=>e.transferStatus==='unknown');
    await assert.rejects(f.cart.add(transfer),e=>e.transferStatus==='unknown');
    await assert.rejects(f.cart.add({...transfer,attemptId:'22345678-1234-1234-1234-123456789abc'}),e=>e.transferStatus==='unknown');
    assert.equal(f.cart.status(flow).transfer.status,'unknown');
    assert.equal(f.calls.filter(call=>call.url.endsWith('/cart/add')).length,1);
  }
});

test('rejected permissions, invalid sessions and disconnected accounts cannot write', async () => {
  const f = fixture(), flow = await f.connect(), quote = await f.retailer.basket(input);
  const transfer = {...flow,quoteToken:quote.quoteToken,attemptId,fulfillment:'DELIVERY'};
  await assert.rejects(f.cart.add({...transfer,sessionToken:'forged'}),e=>e.status===401);
  f.setCartMode(403);
  await assert.rejects(f.cart.add(transfer),e=>e.status===409);
  assert.equal(f.cart.status(flow).status,'failed');
  f.cart.disconnect(flow);
  assert.throws(()=>f.cart.status(flow),e=>e.status===401);
  await assert.rejects(f.cart.add(transfer),e=>e.status===401);
});

test('cart configuration accepts the exact loopback callback or HTTPS and rejects unsafe redirects', () => {
  for (const callback of ['', 'http://example.com/kroger/callback','https://example.com/kroger/callback?x=y','https://example.com/wrong']) {
    assert.throws(()=>createKrogerCart({...credentials,redirectUri:callback}).connect(),e=>e.status===503);
  }
  assert.equal(createKrogerCart({...credentials,redirectUri:'https://example.com/kroger/callback'}).configured,true);
});
