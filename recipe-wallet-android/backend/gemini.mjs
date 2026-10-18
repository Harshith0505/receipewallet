import {setTimeout as delay} from 'node:timers/promises';

const blockedCodes=new Set(['safety','prohibited_content','spii','blocklist','content_blocked']);
export const blockedReply="I can’t help with that request. I can help with safe information or planning a meal.";

function waitForTurn(previous,signal) {
 if(!signal) return previous;
 signal.throwIfAborted();
 return new Promise((resolve,reject)=>{
  const abort=()=>{cleanup();reject(signal.reason);};
  const cleanup=()=>signal.removeEventListener('abort',abort);
  signal.addEventListener('abort',abort,{once:true});
  previous.then(()=>{cleanup();resolve();});
 });
}

// One queue for this demo process: agent tool rounds and pantry suggestions share
// the same provider allowance. No cached or synthetic successful AI responses.
export function createGeminiClient({fetchImpl=fetch,now=Date.now,
 sleep=(ms,signal)=>delay(ms,undefined,{signal}),
 minIntervalMs=6500,maxRetries=2}={}) {
 let tail=Promise.resolve(),nextStart=0;
 return async function interact(body,{apiKey,signal}={}) {
  const previous=tail;
  let release;
  tail=new Promise(resolve=>{release=resolve;});
  try {
   await waitForTurn(previous,signal);
   for(let attempt=0;;attempt++) {
    signal?.throwIfAborted();
    const wait=nextStart-now();
    if(wait>0) await sleep(wait,signal);
    signal?.throwIfAborted();
    nextStart=now()+minIntervalMs;
    const response=await fetchImpl('https://generativelanguage.googleapis.com/v1beta/interactions',{
     method:'POST',headers:{'Content-Type':'application/json','x-goog-api-key':apiKey},
     body:JSON.stringify(body),signal});
    const data=await response.json();
    if(response.ok) return data;
    const code=data.error?.code;
    if(blockedCodes.has(code)) return {steps:[{type:'model_output',content:[{type:'text',text:blockedReply}]}]};
    const quota=code==='quota_exceeded';
    const retryable=!quota&&[429,500,502,503,504].includes(response.status);
    if(retryable&&attempt<maxRetries) {
     const header=response.headers?.get('retry-after');
     const seconds=header&&/^\d+(\.\d+)?$/.test(header)?Number(header)*1000:NaN;
     const date=header?Date.parse(header):NaN;
     const detail=data.error?.details?.find(d=>d['@type']==='type.googleapis.com/google.rpc.RetryInfo')?.retryDelay;
     const detailMs=typeof detail==='string'&&/^\d+(\.\d+)?s$/.test(detail)?Number(detail.slice(0,-1))*1000:0;
     const backoff=Math.max(1000*2**attempt,Number.isFinite(seconds)?seconds:0,
      Number.isFinite(date)&&!Number.isFinite(seconds)?date-now():0,detailMs);
     // Never retry earlier than requested, or hold a request indefinitely.
     if(backoff<=60000) {nextStart=Math.max(nextStart,now()+backoff);continue;}
    }
    const error=new Error(quota?'Gemini daily quota is exhausted. Wait for the quota reset or review your Google AI Studio limits.'
     :response.status===429?'Gemini rate limit reached after waiting and retrying. Please try again shortly.'
     :'Gemini could not complete this request. Check the configured model and API key, then retry.');
    error.status=quota||response.status===429?429:503;
    throw error;
   }
  } finally {
   // Cancelling a queued request must not allow the next caller to jump ahead
   // of a provider request that is still running.
   previous.then(release);
  }
 };
}

const configured=Number(process.env.GEMINI_MIN_INTERVAL_MS??6500);
export const interact=createGeminiClient({minIntervalMs:Number.isFinite(configured)&&configured>=0?configured:6500});
