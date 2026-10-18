import {test} from 'node:test';
import assert from 'node:assert/strict';
import {rankRecipes,suggestFromPantry,validatePantry} from './pantry.mjs';
import {catalog} from './pricing.mjs';

test('ranks by how much of each recipe is already owned',()=>{
 const r=rankRecipes(['noodles','garlic','soy','sesame','chili','onion']);
 assert.equal(r[0].recipeId,'noodles');assert.equal(r[0].canCookNow,true);
 assert.deepEqual(r[0].missingIds,[]);
 assert.ok(r.every(x=>x.haveCount<=x.totalCount));
});
test('partial pantry lists exactly what is missing',()=>{
 const r=rankRecipes(['rice','avo']).find(x=>x.recipeId==='bowl');
 assert.equal(r.haveCount,2);assert.deepEqual(r.missingIds,['chickpeas','greens','lemon']);
 assert.match(r.summary,/2 of 5/);
});
test('empty pantry still returns every recipe with everything missing',()=>{
 const r=rankRecipes([]);assert.equal(r.length,catalog.recipes.length);assert.ok(r.every(x=>x.haveCount===0&&!x.canCookNow));
});
test('vegetarian excludes pasta and prep time filters apply',()=>{
 assert.ok(!rankRecipes([],{diet:'vegetarian'}).some(x=>x.recipeId==='pasta'));
 assert.ok(rankRecipes([],{maxPrepMinutes:15}).every(x=>x.prepTime<=15));
});
test('invented or duplicate pantry items are rejected',()=>{
 assert.throws(()=>validatePantry(['unicorn']),/Invalid pantry/);
 assert.throws(()=>validatePantry(['rice','rice']),/Invalid pantry/);
 assert.throws(()=>validatePantry('rice'),/Invalid pantry/);
});
test('works without Gemini and falls back when Gemini fails',async()=>{
 const a=await suggestFromPantry({pantryIds:['rice']},{apiKey:''});
 assert.ok(a.reply.length>0&&a.suggestions.length===catalog.recipes.length);
 const b=await suggestFromPantry({pantryIds:['rice']},{apiKey:'k',fetchImpl:async()=>{throw new Error('down');}});
 assert.equal(b.reply,a.reply);
});
test('uses Gemini sentence but suggestions always come from code',async()=>{
 const out=await suggestFromPantry({pantryIds:['rice']},{apiKey:'k',fetchImpl:async()=>({ok:true,json:async()=>({steps:[{type:'model_output',content:[{type:'text',text:'Try the bowl!'}]}]})})});
 assert.equal(out.reply,'Try the bowl!');
 assert.ok(out.suggestions.every(s=>catalog.recipes.some(r=>r.id===s.recipeId)));
});
