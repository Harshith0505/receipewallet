import {test} from 'node:test';
import assert from 'node:assert/strict';
import {recommend,validateRecommendation} from './recommend.mjs';
test('unknown recipe IDs and missing explanations are rejected',()=>{
 assert.throws(()=>validateRecommendation({recipeId:'made-up',reason:'Try it'}));
 assert.throws(()=>validateRecommendation({recipeId:'noodles',reason:''}));
});
test('missing key does not pretend to produce an AI response',async()=>{
 await assert.rejects(()=>recommend('fresh',{apiKey:''}),/Meal planning is unavailable/);
});
test('provider errors from the agent are returned to older clients',async()=>{
 await assert.rejects(()=>recommend('fresh',{apiKey:'test',fetchImpl:async()=>({ok:false,status:429,json:async()=>({})})}),/rate limit/);
});
