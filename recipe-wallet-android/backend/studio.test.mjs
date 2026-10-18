import test from 'node:test';
import assert from 'node:assert/strict';
import {scanPantry,weeklyPlan} from './studio.mjs';
const mock=value=>async()=>({ok:true,json:async()=>({candidates:[{content:{parts:[{text:JSON.stringify(value)}]}}]})});
test('scan whitelists IDs, deduplicates and requires confirmation',async()=>{
 const result=await scanPantry({mimeType:'image/jpeg',image:'a'.repeat(24)},{apiKey:'test',fetchImpl:mock({ingredientIds:['garlic','garlic','invented']})});
 assert.deepEqual(result.ingredientIds,['garlic']);assert.equal(result.requiresConfirmation,true);
});
test('scan rejects malformed and oversized images before provider',async()=>{
 for(const image of ['','bad image','a'.repeat(1500001)]) await assert.rejects(scanPantry({mimeType:'image/jpeg',image}),/Invalid/);
 await assert.rejects(scanPantry({mimeType:'text/plain',image:'a'.repeat(24)}),/Invalid/);
});
test('weekly fallback is grounded and respects vegetarian filter',async()=>{
 const result=await weeklyPlan({preferences:'quick dinners',pantryIds:['garlic'],vegetarian:true},{apiKey:''});
 assert.equal(result.recipeIds.length,5);assert.equal(result.source,'local');assert.ok(!result.recipeIds.includes('pasta'));
});
test('weekly Gemini results cannot introduce invented recipes',async()=>{
 await assert.rejects(weeklyPlan({preferences:'test',pantryIds:[]},{apiKey:'test',fetchImpl:mock({recipeIds:Array(5).fill('imaginary')})}),/unsupported/);
 const result=await weeklyPlan({preferences:'noodles',pantryIds:[]},{apiKey:'test',fetchImpl:mock({recipeIds:Array(5).fill('noodles')})});
 assert.equal(result.source,'gemini');assert.equal(result.recipeIds[0],'noodles');
});

test('weekly cuisine filtering never mixes in other catalog cuisines',async()=>{
 const result=await weeklyPlan({preferences:'Indian meals',pantryIds:[],cuisine:'Indian'},{apiKey:''});
 assert.deepEqual(result.recipeIds,Array(5).fill('chana'));
 await assert.rejects(weeklyPlan({preferences:'test',pantryIds:[],cuisine:'invented'},{apiKey:''}),/Unknown cuisine/);
});
