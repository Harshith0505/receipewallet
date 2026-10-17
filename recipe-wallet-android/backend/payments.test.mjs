import {test} from 'node:test';
import assert from 'node:assert/strict';
import {paymentBasket,verifyPayment} from './payments.mjs';

const input={recipeId:'noodles',ingredientIds:['noodles','soy','sesame','chili','onion'],storeId:'market',cardId:'green',attempt:'11111111-1111-4111-8111-111111111111'};
function intentFor(basket=input) {
 const {quote,fingerprint,attempt}=paymentBasket(basket);
 return {id:'pi_demo',livemode:false,status:'succeeded',amount:quote.amount,amount_received:quote.amount,currency:'usd',metadata:{application:'recipe-wallet-demo',basket:fingerprint,attempt}};
}
test('verified receipt requires a succeeded, fully received test payment for this basket',()=>{
 assert.deepEqual(verifyPayment(intentFor(),input),{paid:true,status:'succeeded',amount:779,currency:'usd',paymentIntentId:'pi_demo'});
});
test('rejects a different basket even if the paid total is identical',()=>{
 const other={...input,cardId:'black'};
 assert.equal(paymentBasket(other).quote.amount,779);
 assert.throws(()=>verifyPayment(intentFor(),other),/approved basket/);
 assert.throws(()=>verifyPayment(intentFor(),{...input,ingredientIds:['noodles']}),/approved basket/);
 assert.throws(()=>verifyPayment(intentFor(),{...input,attempt:'22222222-2222-4222-8222-222222222222'}),/approved basket/);
});
test('rejects live payments, wrong amounts, currencies, metadata and incomplete capture',()=>{
 for(const changes of [{livemode:true},{amount:1},{currency:'eur'},{amount_received:1},{metadata:{application:'other'}}]) {
  assert.throws(()=>verifyPayment({...intentFor(),...changes},input));
 }
});
test('canceled, declined and processing intents never produce paid receipts',()=>{
 for(const status of ['canceled','requires_payment_method','processing','requires_action']) {
  assert.equal(verifyPayment({...intentFor(),status,amount_received:0},input).paid,false);
 }
});
test('basket identity ignores ingredient ordering and untrusted client totals',()=>{
 assert.equal(paymentBasket(input).fingerprint,paymentBasket({...input,ingredientIds:[...input.ingredientIds].reverse(),amount:1}).fingerprint);
 assert.throws(()=>paymentBasket({...input,attempt:'invalid'}));
});
test('package receipt binds servings and fulfillment even when the same packs cover both quantities',()=>{
 const packageInput={...input,pricingVersion:'packages-v1',ingredientIds:['noodles'],servings:2,fulfillment:'pickup',organicOnly:false};
 const fewerServings={...packageInput,servings:1};
 assert.equal(paymentBasket(packageInput).quote.amount,paymentBasket(fewerServings).quote.amount);
 assert.throws(()=>verifyPayment(intentFor(packageInput),fewerServings),/approved basket/);
 assert.throws(()=>verifyPayment(intentFor(packageInput),{...packageInput,fulfillment:'delivery'}),/approved basket/);
 assert.equal(verifyPayment(intentFor(packageInput),packageInput).paid,true);
});
