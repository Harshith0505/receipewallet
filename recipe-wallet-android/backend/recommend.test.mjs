import {test} from 'node:test';
import assert from 'node:assert/strict';
import {recommend,validateRecommendation} from './recommend.mjs';
test('unknown recipe IDs and missing explanations are rejected',()=>{
 assert.throws(()=>validateRecommendation({recipeId:'made-up',reason:'Try it'}));
 assert.throws(()=>validateRecommendation({recipeId:'noodles',reason:''}));
});
test('missing key does not pretend to produce an AI response',async()=>{
 await assert.rejects(()=>recommend('fresh',{apiKey:''}),/GEMINI_API_KEY/);
});
test('mocked provider response is constrained to the known catalog',async()=>{
 let sent;
 const result=await recommend('fresh in 15 minutes',{apiKey:'test-key',fetchImpl:async(url,options)=>{
  sent=JSON.parse(options.body);
  return {ok:true,json:async()=>({candidates:[{content:{parts:[{text:JSON.stringify({recipeId:'bowl',reason:'This bowl has fresh greens and takes 15 minutes.'})}]}}]})};
 }});
 assert.equal(result.recipeId,'bowl');
 assert.deepEqual(sent.generationConfig.responseJsonSchema.properties.recipeId.enum,['noodles','bowl','pasta']);
 assert.equal(JSON.parse(sent.contents[0].parts[0].text).preference,'fresh in 15 minutes');
});
