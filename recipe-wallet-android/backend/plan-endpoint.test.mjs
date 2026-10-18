import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';

process.env.GEMINI_API_KEY='synthetic-key';
process.env.GEMINI_MIN_INTERVAL_MS='0';
let provider;
globalThis.fetch=async()=>provider;
const {handleRequest}=await import('./server.mjs');
async function plan(message) {
 const req=Readable.from([JSON.stringify({prompt:message})]);
 Object.assign(req,{url:'/plan',method:'POST'});
 const result={};
 await handleRequest(req,{setHeader(){},writeHead(status){result.status=status;},
  end(body){result.body=JSON.parse(body);}});
 return result;
}
test('HTTP plan returns refusals and answers without fabricating a basket',async()=>{
 for(const text of ['I cannot assist with harming someone.','A brief factual answer.']) {
  provider={ok:true,json:async()=>({steps:[{type:'model_output',content:[{type:'text',text}]}]})};
  const result=await plan('A user request');
  assert.equal(result.status,200);assert.equal(result.body.reply,text);
  assert.equal(result.body.plan,null);assert.deepEqual(result.body.trace,[]);
  assert.match(result.body.sessionId,/^[a-f0-9-]{36}$/);
 }
});
test('HTTP plan preserves a provider quota outage as 429, not a passing AI answer',async()=>{
 provider={ok:false,status:429,json:async()=>({error:{code:'quota_exceeded'}})};
 const result=await plan('A meal request');
 assert.equal(result.status,429);assert.match(result.body.error,/daily quota/);
 assert.equal(result.body.reply,undefined);
});
test('planning aliases preserve the live branch input limit and reject ambiguity',async()=>{
 provider={ok:true,json:async()=>({steps:[{type:'model_output',content:[{type:'text',text:'A plain answer.'}]}]})};
 assert.equal((await plan('x'.repeat(1200))).status,200);
 assert.equal((await plan('x'.repeat(2401))).status,400);
 for(const input of [null,[],{}, {prompt:7},{message:'Noodles',prompt:'Pizza'}]) {
  const req=Readable.from([JSON.stringify(input)]);Object.assign(req,{url:'/plan',method:'POST'});
  let status;
  await handleRequest(req,{setHeader(){},writeHead(value){status=value;},end(){}});
  assert.equal(status,400);
 }
});
