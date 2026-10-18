import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
import {paymentBasket} from './payments.mjs';
process.env.STRIPE_SECRET_KEY='sk_test_fixture';
process.env.STRIPE_PUBLISHABLE_KEY='pk_test_fixture';
process.env.MEMORY_DB=':memory:';
process.env.SUPABASE_SERVICE_KEY='';
const {handleRequest}=await import('./server.mjs');
async function request(url,body) {
 const req=Readable.from([JSON.stringify(body)]);req.method='POST';req.url=url;
 const out={};await handleRequest(req,{setHeader(){},writeHead(status){out.status=status;},end(value){out.body=JSON.parse(value);}});return out;
}
test('only a fully verified matching test payment creates pantry memory; previews and tampered receipts do not',async()=>{
 const user='11111111-1111-4111-8111-111111111111';
 const input={recipeId:'noodles',ingredientIds:['soy'],storeId:'market',cardId:'green',attempt:user,pricingVersion:'packages-v1',fulfillment:'pickup',studentEligible:true};
 const {quote,fingerprint}=paymentBasket(input);
 let intent={id:'pi_fixture',livemode:false,status:'succeeded',amount:quote.amount,amount_received:quote.amount,currency:'usd',metadata:{application:'recipe-wallet-demo',basket:fingerprint,attempt:user,user,recipe:'noodles',ingredients:'soy'}};
 const original=globalThis.fetch;
 globalThis.fetch=async url=>{assert.match(url,/^https:\/\/api.stripe.com\/v1\/payment_intents\/pi_fixture$/);return {ok:true,json:async()=>intent};};
 try {
  assert.deepEqual((await request('/memory',{userId:user})).body.likelyOwned,[]);
  assert.equal((await request('/verify',{...input,cardId:'black',paymentIntentId:'pi_fixture'})).status,400);
  assert.deepEqual((await request('/memory',{userId:user})).body.likelyOwned,[]);
  intent={...intent,status:'processing',amount_received:0};
  assert.equal((await request('/verify',{...input,paymentIntentId:'pi_fixture'})).body.paid,false);
  assert.deepEqual((await request('/memory',{userId:user})).body.likelyOwned,[]);
  intent={...intent,status:'succeeded',amount_received:quote.amount};
  assert.equal((await request('/verify',{...input,paymentIntentId:'pi_fixture'})).body.paid,true);
  assert.deepEqual((await request('/memory',{userId:user})).body.likelyOwned.map(i=>i.id),['soy']);
  assert.equal((await request('/used-up',{userId:user,ingredientId:'soy'})).status,200);
  await request('/verify',{...input,paymentIntentId:'pi_fixture'});
  assert.deepEqual((await request('/memory',{userId:user})).body.likelyOwned,[]);
  assert.equal((await request('/used-up',{userId:'invalid',ingredientId:'soy'})).status,400);
 } finally {globalThis.fetch=original;}
});
