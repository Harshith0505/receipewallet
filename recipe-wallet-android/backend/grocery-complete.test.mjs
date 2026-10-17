import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {catalog,calculate} from './pricing.mjs';
import {recipeGroceries} from './grocery.mjs';
const input={pricingVersion:'packages-v1',recipeId:'pasta',ingredientIds:catalog.recipes.find(r=>r.id==='pasta').ingredients.map(i=>i.id),storeId:'market',cardId:'green',servings:2,fulfillment:'pickup',organicOnly:false};
test('all recipes have complete package offers for every pickup store',()=>{
 for(const recipe of catalog.recipes) for(const store of catalog.stores) {
  const q=calculate({...input,recipeId:recipe.id,ingredientIds:recipe.ingredients.map(i=>i.id),storeId:store.id});
  assert.ok(q.groceryQuote.complete);
  assert.equal(q.amount,q.groceries+q.groceryQuote.feesCents+q.groceryQuote.taxCents);
 }
});
test('delivery changes the charge while future rewards stay separate',()=>{
 const pickup=calculate(input),delivery=calculate({...input,fulfillment:'delivery'});
 assert.equal(delivery.amount-pickup.amount,498);
 assert.equal(delivery.rewards,pickup.rewards);
 assert.equal(delivery.net,delivery.amount-delivery.rewards);
 assert.throws(()=>calculate({...input,storeId:'fresh',fulfillment:'delivery'}),/cannot fulfill/);
});
test('organic requirements never silently substitute conventional products',()=>{
 const missing=recipeGroceries({...input,organicOnly:true},catalog);
 assert.equal(missing.complete,false);
 assert.ok(missing.missingIngredients.some(i=>i.ingredientId==='basil'));
 assert.ok(calculate({...input,storeId:'pantry',organicOnly:true}).groceryQuote.items.every(i=>i.tags.includes('organic')));
});
test('package payments reject tampering and invalid selections',()=>{
 assert.equal(calculate({...input,amount:1,priceCents:1}).amount,calculate(input).amount);
 for(const servings of [0,13,1.5]) assert.throws(()=>calculate({...input,servings}));
 assert.throws(()=>calculate({...input,ingredientIds:['fake']}));
});
test('288 shared Android parity scenarios match server calculations',()=>{
 const rows=readFileSync(new URL('../app/src/test/resources/grocery-parity.tsv',import.meta.url),'utf8').trimEnd().split('\n').slice(1);
 assert.equal(rows.length,288);
 for(const row of rows) {
  const [recipeId,storeId,servings,fulfillment,organic,pantry,complete,groceries,fees,amount]=row.split('\t');
  const recipe=catalog.recipes.find(r=>r.id===recipeId);
  let q;try{q=calculate({...input,recipeId,storeId,servings:Number(servings),fulfillment,organicOnly:organic==='true',ingredientIds:recipe.ingredients.filter(i=>i.id!==pantry).map(i=>i.id)})}catch{}
  assert.equal(!!q,complete==='true',row);
  if(q)assert.deepEqual([q.groceries,q.groceryQuote.feesCents,q.amount],[Number(groceries),Number(fees),Number(amount)],row);
 }
});
