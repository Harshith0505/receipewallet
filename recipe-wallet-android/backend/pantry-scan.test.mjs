import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
import {scanPantry,parseIngredients,validateScanInput} from './pantry-scan.mjs';
const photo={mimeType:'image/jpeg',imageBase64:Buffer.from([255,216,255,217]).toString('base64')};
const completion=(ingredients,finish_reason='stop')=>({choices:[{finish_reason,message:{content:JSON.stringify({ingredients})}}]});
const fake=(body,status=200)=>async()=>({ok:status===200,status,json:async()=>body});

test('sends a bounded JPEG to Perceptron using the documented structured-output API',async()=>{
 let request;
 const result=await scanPantry(photo,{apiKey:'test-only-key',fetchImpl:async(url,options)=>{
  request={url,options};return {ok:true,status:200,json:async()=>completion([{name:'Eggs',amount:'3 eggs'},{name:' eggs ',amount:''},{name:'Milk',amount:''}])};
 }});
 assert.equal(request.url,'https://api.perceptron.inc/v1/chat/completions');
 assert.equal(request.options.headers.Authorization,'Bearer test-only-key');
 const sent=JSON.parse(request.options.body);
 assert.equal(sent.model,'perceptron-mk1.5');
 assert.equal(sent.messages[0].content[0].image_url.url,`data:image/jpeg;base64,${photo.imageBase64}`);
 assert.equal(sent.response_format.type,'json_schema');
 assert.equal(sent.response_format.json_schema.strict,true);
 assert.deepEqual(result.ingredients,[{name:'Eggs',amount:'3 eggs'},{name:'Milk',amount:''}]);
 assert.equal(result.provider,'perceptron');
});
test('rejects missing, invalid, disguised and oversized photos before calling the model',async()=>{
 for(const value of [null,{}, {...photo,mimeType:'image/png'}, {...photo,imageBase64:'a=== '},{...photo,imageBase64:Buffer.from('not a photo').toString('base64')}]) assert.throws(()=>validateScanInput(value),e=>e.status===400);
 assert.throws(()=>validateScanInput({...photo,imageBase64:'a'.repeat(4_000_004)}),e=>e.status===413);
 await assert.rejects(scanPantry(photo,{apiKey:''}),e=>e.status===503);
});
test('keeps empty scans honest and rejects partial or invalid model answers',()=>{
 assert.deepEqual(parseIngredients(completion([])),[]);
 for(const value of [completion([{name:'Milk',amount:''}],'length'),completion([{name:'',amount:''}]),completion([{name:'Eggs',amount:3}]),completion(Array.from({length:41},()=>({name:'Milk',amount:''}))),{choices:[{finish_reason:'stop',message:{content:'not-json'}}]}]) assert.throws(()=>parseIngredients(value),e=>e.status===502);
});
test('provider errors are actionable and never echo upstream secrets or image content',async()=>{
 for(const [status,expected] of [[401,503],[403,503],[429,429],[500,502]]) {
  await assert.rejects(scanPantry(photo,{apiKey:'test-key',fetchImpl:fake({error:'secret photo'},status)}),e=>e.status===expected&&!e.message.includes('secret photo'));
 }
 await assert.rejects(scanPantry(photo,{apiKey:'test-key',fetchImpl:async()=>{throw new Error('secret');}}),e=>e.status===504&&!e.message.includes('secret'));
});

test('HTTP route accepts photos larger than existing route limits without changing other endpoints',async()=>{
 process.env.PERCEPTRON_API_KEY='';
 const {handleRequest}=await import('./server.mjs');
 async function request(input,url='/pantry-scan',raw=false) {
  const req=Readable.from([raw?input:JSON.stringify(input)]);req.method='POST';req.url=url;
  const result={};await handleRequest(req,{setHeader(){},writeHead(status){result.status=status;},end(body){result.body=JSON.parse(body);}});return result;
 }
 assert.equal((await request({...photo,imageBase64:Buffer.concat([Buffer.from([255,216,255]),Buffer.alloc(12000)]).toString('base64')})).status,503);
 assert.equal((await request({...photo,mimeType:'image/png'})).status,400);
 assert.equal((await request('x'.repeat(4_001_025),'/pantry-scan',true)).status,413);
 assert.equal((await request('x'.repeat(8193),'/grocery-quotes',true)).status,413);
 assert.equal((await request('{invalid','/pantry-scan',true)).status,400);
});
