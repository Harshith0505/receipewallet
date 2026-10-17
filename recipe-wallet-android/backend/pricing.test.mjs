import {test} from 'node:test';
import assert from 'node:assert/strict';
import {catalog,calculate} from './pricing.mjs';
const base={recipeId:'noodles',ingredientIds:catalog.recipes[0].ingredients.map(i=>i.id),storeId:'market',cardId:'green'};
test('uses integer cents and never subtracts rewards from payment amount',()=>{
 assert.deepEqual((({groceries,rewards,amount,net})=>({groceries,rewards,amount,net}))(calculate(base)),{groceries:651,rewards:33,amount:850,net:817});
});
test('points are points per dollar multiplied by redemption value',()=>assert.equal(calculate({...base,cardId:'blue'}).rewards,20));
test('removing an ingredient changes price and rewards',()=>assert.equal(calculate({...base,ingredientIds:base.ingredientIds.filter(i=>i!=='chili')}).groceries,613));
test('rejects empty, duplicated, fabricated items and client prices',()=>{
 for(const ids of [[],['noodles','noodles'],['fabricated']]) assert.throws(()=>calculate({...base,ingredientIds:ids}));
 assert.equal(calculate({...base,amount:1}).amount,850);
});
test('store ranking uses each stores best card and net cost only',()=>{
 const ranked=catalog.stores.map(store=>catalog.cards.map(card=>calculate({...base,storeId:store.id,cardId:card.id})).sort((a,b)=>a.net-b.net)[0]).sort((a,b)=>a.net-b.net);
 assert.equal(ranked[0].store.id,'market'); assert.equal(ranked[0].card.id,'green');
});
test('backend and Android share identical fixture data',async()=>{
 const {readFile}=await import('node:fs/promises');
 assert.deepEqual(JSON.parse(await readFile(new URL('../app/src/main/assets/demo.json',import.meta.url),'utf8')),catalog);
});
