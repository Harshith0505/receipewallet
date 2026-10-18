import {test} from 'node:test';
import assert from 'node:assert/strict';
import {catalog,calculate} from './pricing.mjs';
import {paymentBasket,verifyPayment} from './payments.mjs';
import {createToolRunner} from './agent.mjs';
const base={recipeId:'pasta',ingredientIds:['pasta'],storeId:'market',cardId:'green',servings:2,fulfillment:'pickup',pricingVersion:'packages-v1',studentEligible:true,attempt:'11111111-1111-4111-8111-111111111111'};

test('QFC demo discount lowers charge before rewards and leaves fulfillment fees intact',()=>{
 const pickup=calculate(base);
 assert.deepEqual([pickup.groceries,pickup.studentDiscount,pickup.discountedGroceries,pickup.amount,pickup.rewards,pickup.net],[349,35,314,314,16,298]);
 const delivery=calculate({...base,fulfillment:'delivery'});
 assert.equal(delivery.amount,812);assert.equal(delivery.groceryQuote.feesCents,498);assert.equal(delivery.rewards,16);
 assert.equal(calculate({...base,studentEligible:false}).amount,349);
});
test('each store uses only its configured demo offer',()=>{
 const whole=calculate({...base,storeId:'pantry'}),trader=calculate({...base,storeId:'fresh'});
 assert.deepEqual([whole.groceries,whole.studentDiscount,whole.amount,whole.rewards],[599,30,569,28]);
 assert.deepEqual([trader.groceries,trader.studentDiscount,trader.amount],[209,0,209]);
 assert.equal(calculate({...base,studentDiscount:348,studentDiscountBps:10000,amount:1}).amount,314);
 for(const studentEligible of ['true',1,null]) assert.throws(()=>calculate({...base,studentEligible}),/student discount/);
});
test('saved recipe packs receive the same discount with rewards on the discounted groceries',()=>{
 const custom={...base,recipeId:'saved:6d49022b-3c0b-46ae-8db0-83be1edb6066',pricingVersion:'custom-packages-v1',ingredientIds:['line-0','line-1'],customBasket:{items:[{productId:'pasta',quantity:1},{productId:'cheddar',quantity:1}]}};
 const q=calculate(custom);
 assert.deepEqual([q.groceries,q.studentDiscount,q.amount,q.rewards],[616,62,554,28]);
 assert.equal(calculate({...custom,fulfillment:'delivery'}).amount,1052);
});
test('verified payment binds student mode even when a store offers no discount',()=>{
 for(const storeId of ['market','fresh']) {
  const input={...base,storeId};const {quote,fingerprint,attempt}=paymentBasket(input);
  const intent={id:'pi_student_demo',livemode:false,status:'succeeded',amount:quote.amount,amount_received:quote.amount,currency:'usd',metadata:{application:'recipe-wallet-demo',basket:fingerprint,attempt}};
  assert.equal(verifyPayment(intent,input).paid,true);
  assert.throws(()=>verifyPayment(intent,{...input,studentEligible:false}),/approved basket/);
 }
});
test('agent plans carry the same explicitly selected demo eligibility and discounted totals',()=>{
 const runner=createToolRunner({studentEligible:true});
 runner.run('find_recipes',{mealPreference:'pasta',diet:'any',maxPrepMinutes:0,budgetCents:10000,pantryIds:[]});
 const option=runner.run('compare_baskets',{recipeIds:['pasta']}).options[0];
 assert.equal(option.studentEligible,true);assert.ok(option.studentDiscount>0);
 const quote=calculate({...base,...option});
 assert.equal(option.amount,quote.amount);assert.equal(option.rewards,quote.rewards);
});
