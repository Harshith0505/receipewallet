import test from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
process.env.PERCEPTRON_API_KEY='fixture';process.env.GEMINI_API_KEY='fixture';process.env.GEMINI_MODEL='gemini-3.5-flash-lite';process.env.STRIPE_SECRET_KEY='';process.env.STRIPE_PUBLISHABLE_KEY='';
const {handleRequest}=await import('./server.mjs');
async function call(url,input){const req=Readable.from([JSON.stringify(input)]);req.url=url;req.method='POST';const out={};await handleRequest(req,{setHeader(){},writeHead(status){out.status=status;},end(body){out.body=JSON.parse(body);}});return out;}
test('new studio image and existing scanner contracts both use Perceptron and require ingredient review',async()=>{
 const original=globalThis.fetch;let calls=0;
 globalThis.fetch=async url=>{calls++;assert.equal(url,'https://api.perceptron.inc/v1/chat/completions');return {ok:true,json:async()=>({choices:[{finish_reason:'stop',message:{content:JSON.stringify({ingredients:[{name:'Garlic',amount:'2 bulbs'}]})}}]})};};
 try{
  const image=Buffer.from([255,216,255,224,0,0,1,2]).toString('base64');
  for(const field of ['image','imageBase64']){const r=await call('/pantry-scan',{mimeType:'image/jpeg',[field]:image});assert.equal(r.status,200);assert.equal(r.body.provider,'perceptron');assert.deepEqual(r.body.ingredientIds,['garlic']);assert.deepEqual(r.body.ingredients,[{name:'Garlic',amount:'2 bulbs'}]);assert.equal(r.body.requiresConfirmation,true);}
  assert.equal((await call('/pantry-scan',{mimeType:'image/jpeg',image:'bad'})).status,400);assert.equal(calls,2);
 }finally{globalThis.fetch=original;}
});
test('weekly frontend route uses the configured working Gemini model and returns allowlisted recipes',async()=>{
 const original=globalThis.fetch;
 globalThis.fetch=async url=>{assert.match(url,/gemini-3\.5-flash-lite:generateContent$/);return {ok:true,json:async()=>({candidates:[{content:{parts:[{text:JSON.stringify({recipeIds:Array(5).fill('bowl')})}]}}]})};};
 try{const r=await call('/weekly-plan',{preferences:'Fresh dinners',pantryIds:[],vegetarian:true,maxMinutes:45,cuisine:'Any'});assert.equal(r.status,200);assert.equal(r.body.source,'gemini');assert.deepEqual(r.body.recipeIds,Array(5).fill('bowl'));}finally{globalThis.fetch=original;}
});
