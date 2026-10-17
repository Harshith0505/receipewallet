import http from 'node:http';
import {calculate} from './pricing.mjs';
import {recommend} from './recommend.mjs';
import {sessionPlan} from './agent.mjs';
const secret=process.env.STRIPE_SECRET_KEY||'';
const publishableKey=process.env.STRIPE_PUBLISHABLE_KEY||'';
const configured=secret.startsWith('sk_test_')&&publishableKey.startsWith('pk_test_');
if(!configured) console.log('Stripe is not configured. Set test keys in backend/.env. Payment requests will return a setup message.');
async function stripe(path,body,idempotencyKey) {
 const response=await fetch(`https://api.stripe.com/v1/${path}`,{method:body?'POST':'GET',headers:{'Stripe-Version':'2025-06-30.basil',Authorization:`Bearer ${secret}`,...(body?{'Content-Type':'application/x-www-form-urlencoded'}:{}),...(idempotencyKey?{'Idempotency-Key':idempotencyKey}:{})},body:body?.toString(),signal:AbortSignal.timeout(20000)});
 const result=await response.json();
 if(!response.ok) throw new Error('Stripe request failed. Check the test keys and backend logs.');
 if(result.livemode!==false) throw new Error('Only test mode is supported');
 return result;
}
const server=http.createServer(async(req,res)=>{
 res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store');
 const send=(status,data)=>{res.writeHead(status);res.end(JSON.stringify(data));};
 if(req.method==='GET'&&req.url==='/health') return send(200,{ok:true,stripeConfigured:configured,aiConfigured:!!process.env.GEMINI_API_KEY});
 if(req.method!=='POST'||!['/payment-intent','/verify','/recommend','/plan'].includes(req.url)) return send(404,{error:'Not found'});
 if(!['/recommend','/plan'].includes(req.url)&&!configured) return send(503,{error:'Stripe test keys are not configured on the backend. Follow README setup, or use the confirmation preview.'});
 try {
  let body='';for await(const chunk of req) {body+=chunk;if(body.length>8192) return send(413,{error:'Request too large'});}
  let input;try {input=JSON.parse(body);} catch{return send(400,{error:'Invalid JSON'});}
  if(req.url==='/plan') {
   try {return send(200,await sessionPlan(input));} catch(e) {return send(503,{error:e.message});}
  }
  if(req.url==='/recommend') {
   try { return send(200,await recommend(input.preference)); } catch(e) { return send(503,{error:e.message}); }
  }
  if(req.url==='/payment-intent') {
   let q;try {q=calculate(input);} catch(e){return send(400,{error:e.message});}
   if(typeof input.attempt!=='string'||!/^[a-f0-9-]{36}$/.test(input.attempt)) return send(400,{error:'Invalid payment attempt'});
   const form=new URLSearchParams({amount:String(q.amount),currency:'usd','payment_method_types[]':'card',description:'Recipe Wallet TEST — no grocery order','metadata[application]':'recipe-wallet-demo','metadata[recipe]':input.recipeId,'metadata[store]':input.storeId,'metadata[advisoryCard]':input.cardId,'metadata[estimatedRewards]':String(q.rewards)});
   const intent=await stripe('payment_intents',form,`recipe-wallet-${input.attempt}`);
   return send(200,{clientSecret:intent.client_secret,paymentIntentId:intent.id,publishableKey,amount:q.amount});
  }
  if(typeof input.paymentIntentId!=='string'||!/^pi_[A-Za-z0-9]+$/.test(input.paymentIntentId)) return send(400,{error:'Invalid PaymentIntent'});
  const intent=await stripe(`payment_intents/${input.paymentIntentId}`);
  if(intent.metadata?.application!=='recipe-wallet-demo') return send(400,{error:'Unrecognized payment'});
  return send(200,{paid:intent.status==='succeeded',amount:intent.amount,currency:intent.currency});
 } catch {send(502,{error:'Unable to complete the Stripe test request. Check backend setup and retry.'});}
});
server.requestTimeout=100000;
server.listen(Number(process.env.PORT||4242),process.env.HOST||'127.0.0.1',()=>console.log('Recipe Wallet demo backend: http://127.0.0.1:4242'));
