import {test} from 'node:test';
import assert from 'node:assert/strict';
import {calculate,products} from './pricing.mjs';
import {readFileSync} from 'node:fs';

const basket={recipeId:'saved:6d49022b-3c0b-46ae-8db0-83be1edb6066',storeId:'market',cardId:'green',
 customBasket:{items:[{productId:'pasta',quantity:1},{productId:'cheddar',quantity:1}]}};
test('saved recipe checkout is calculated from trusted package prices',()=>{
 const q=calculate(basket);
 assert.equal(q.groceries,616);assert.equal(q.amount,815);assert.equal(q.rewards,31);assert.equal(q.net,784);
 assert.equal(calculate({...basket,amount:1,customBasket:{items:[{productId:'pasta',quantity:1,price:1},{productId:'cheddar',quantity:1,price:1}]}}).amount,815);
});
test('saved recipe pricing respects package counts',()=>{
 const q=calculate({...basket,customBasket:{items:[{productId:'pasta',quantity:2},{productId:'cheddar',quantity:1}]}});
 assert.equal(q.groceries,805);assert.equal(q.amount,1004);assert.equal(q.rewards,40);
});
test('rejects unknown products, duplicates and invalid counts',()=>{
 for(const items of [[],[{productId:'invented',quantity:1}],[{productId:'pasta',quantity:0}],[{productId:'pasta',quantity:1.5}],
  [{productId:'pasta',quantity:21}],[{productId:'pasta',quantity:1},{productId:'pasta',quantity:1}], [null]])
  assert.throws(()=>calculate({...basket,customBasket:{items}}));
 assert.throws(()=>calculate({...basket,recipeId:'noodles'}));
});
test('Android and backend use identical personal-recipe product prices',()=>{
 assert.deepEqual(JSON.parse(readFileSync(new URL('../app/src/main/assets/products.json',import.meta.url),'utf8')),products);
});
