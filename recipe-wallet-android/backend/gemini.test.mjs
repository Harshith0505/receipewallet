import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createGeminiClient,blockedReply} from './gemini.mjs';

const reply={steps:[{type:'model_output',content:[{type:'text',text:'A real provider reply'}]}]};
const response=(status,data,headers={})=>({ok:status===200,status,json:async()=>data,
 headers:new Headers(headers)});
function fixture(responses,options={}) {
 let time=0;const starts=[],waits=[];
 const request=createGeminiClient({now:()=>time,sleep:async ms=>{waits.push(ms);time+=ms;},
  minIntervalMs:100,fetchImpl:async()=>{starts.push(time);return responses.shift();},...options});
 return {request,starts,waits};
}
test('concurrent callers share spacing instead of bursting at Gemini',async()=>{
 const f=fixture([response(200,reply),response(200,reply),response(200,reply)]);
 const results=await Promise.all([f.request({}),f.request({}),f.request({})]);
 assert.deepEqual(f.starts,[0,100,200]);
 assert.ok(results.every(r=>r===reply));
});
test('transient limits honor Retry-After and retry the same request',async()=>{
 const f=fixture([response(429,{error:{code:'rate_limit_exceeded'}},{'Retry-After':'3'}),response(200,reply)]);
 assert.equal(await f.request({}),reply);assert.deepEqual(f.starts,[0,3000]);
});
test('provider retry details and exponential backoff are respected',async()=>{
 const f=fixture([response(429,{error:{details:[{'@type':'type.googleapis.com/google.rpc.RetryInfo',retryDelay:'2.5s'}]}}),
  response(503,{}),response(200,reply)]);
 assert.equal(await f.request({}),reply);assert.deepEqual(f.starts,[0,2500,4500]);
});
test('daily quota and invalid credentials are not repeatedly retried or disguised as success',async()=>{
 for(const [status,code] of [[429,'quota_exceeded'],[401,'authentication']]) {
  const f=fixture([response(status,{error:{code}})]);
  await assert.rejects(()=>f.request({}),code==='quota_exceeded'?/daily quota/:/could not complete/);
  assert.equal(f.starts.length,1);
 }
});
test('exhausted retries release the queue and remain a real error',async()=>{
 const f=fixture([response(429,{}),response(429,{}),response(429,{}),response(200,reply)]);
 await assert.rejects(()=>f.request({}),e=>e.status===429&&/rate limit/.test(e.message));
 assert.equal(f.starts.length,3);assert.equal(await f.request({}),reply);
});
test('cancelled requests never call the provider',async()=>{
 const f=fixture([]);
 await assert.rejects(()=>f.request({},{signal:AbortSignal.abort()}),{name:'AbortError'});
 assert.deepEqual(f.starts,[]);
});
test('documented provider safety blocks become explicit refusals, not outages',async()=>{
 const f=fixture([response(400,{error:{code:'safety',message:'not exposed'}})]);
 const data=await f.request({});
 assert.equal(data.steps[0].content[0].text,blockedReply);
 assert.ok(!JSON.stringify(data).includes('not exposed'));
});
test('cancelling a queued caller does not let later callers bypass the running request',async()=>{
 let finish;let calls=0;
 const request=createGeminiClient({minIntervalMs:0,fetchImpl:async()=>{
  calls++;
  if(calls===1) await new Promise(resolve=>{finish=resolve;});
  return response(200,reply);
 }});
 const first=request({});
 await new Promise(resolve=>setImmediate(resolve));
 const controller=new AbortController();
 const cancelled=request({},{signal:controller.signal});
 const last=request({});
 controller.abort();await assert.rejects(()=>cancelled,{name:'AbortError'});
 assert.equal(calls,1);finish();await Promise.all([first,last]);assert.equal(calls,2);
});
test('a long provider cooldown stays an error instead of retrying prematurely',async()=>{
 const f=fixture([response(429,{},{'Retry-After':'120'})]);
 await assert.rejects(()=>f.request({}),/rate limit/);assert.deepEqual(f.starts,[0]);
});
