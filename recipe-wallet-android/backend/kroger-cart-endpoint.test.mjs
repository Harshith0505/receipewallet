import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
process.env.KROGER_CLIENT_ID='';process.env.KROGER_CLIENT_SECRET='';process.env.KROGER_REDIRECT_URI='';
const {handleRequest}=await import('./server.mjs');
async function request(url,method='GET',body={}) {
  const req=Readable.from([JSON.stringify(body)]);req.url=url;req.method=method;
  const result={headers:{}};
  await handleRequest(req,{setHeader:(key,value)=>result.headers[key]=value,
    writeHead:(status,headers={})=>{result.status=status;Object.assign(result.headers,headers);},end:text=>result.text=text});
  return result;
}
test('cart routes report setup and connection failures without exposing credentials',async()=>{
  const setup=await request('/retailers/cart/connect','POST');
  assert.equal(setup.status,503);assert.match(setup.text,/KROGER_REDIRECT_URI/);
  assert.equal((await request('/retailers/cart/status','POST')).status,401);
  assert.equal((await request('/retailers/cart/add','POST')).status,401);
  assert.equal((await request('/retailers/cart/disconnect','POST')).status,401);
});
test('browser callback clears sensitive query values and the return page cannot execute scripts',async()=>{
  const callback=await request('/kroger/callback?state=forged&code=sensitive-code');
  assert.equal(callback.status,303);assert.equal(callback.headers.Location,'/kroger/return');
  assert.equal(callback.headers['Referrer-Policy'],'no-referrer');assert.equal(callback.headers['Cache-Control'],'no-store');
  const page=await request('/kroger/return?code=sensitive-code');
  assert.equal(page.status,200);assert.match(page.headers['Content-Security-Policy'],/default-src 'none'/);
  assert.ok(!page.text.includes('sensitive-code'));assert.match(page.text,/com.recipewallet.app:\/\/kroger\/return/);
});
