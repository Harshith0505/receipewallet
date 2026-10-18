import http from 'node:http';
import {pathToFileURL} from 'node:url';
import {catalog} from './pricing.mjs';
import {paymentBasket,verifyPayment} from './payments.mjs';
import {planGroceries} from './grocery.mjs';
import {recommend} from './recommend.mjs';
import {sessionPlan} from './agent.mjs';
import {suggestFromPantry} from './pantry.mjs';
import {scanPantry,weeklyPlan} from './studio.mjs';
const secret=process.env.STRIPE_SECRET_KEY||'';
const publishableKey=process.env.STRIPE_PUBLISHABLE_KEY||'';
const configured=secret.startsWith('sk_test_')&&publishableKey.startsWith('pk_test_');
async function stripe(path,body,idempotencyKey) {
 const response=await fetch(`https://api.stripe.com/v1/${path}`,{method:body?'POST':'GET',headers:{'Stripe-Version':'2025-06-30.basil',Authorization:`Bearer ${secret}`,...(body?{'Content-Type':'application/x-www-form-urlencoded'}:{}),...(idempotencyKey?{'Idempotency-Key':idempotencyKey}:{})},body:body?.toString(),signal:AbortSignal.timeout(20000)});
 const result=await response.json();
 if(!response.ok) throw new Error('Stripe request failed. Check the test keys and backend logs.');
 if(result.livemode!==false) throw new Error('Only test mode is supported');
 return result;
}
export async function handleRequest(req,res) {
 res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store');
 const send=(status,data)=>{res.writeHead(status);res.end(JSON.stringify(data));};
 if(req.method==='GET'&&req.url==='/health') return send(200,{ok:true,stripeConfigured:configured,aiConfigured:!!process.env.GEMINI_API_KEY});
 if(req.method!=='POST'||!['/payment-intent','/verify','/recommend','/plan','/grocery-quotes','/suggest','/pantry-scan','/weekly-plan'].includes(req.url)) return send(404,{error:'Not found'});
 if(['/payment-intent','/verify'].includes(req.url)&&!configured) return send(503,{error:'Stripe test keys are not configured on the backend. Follow README setup, or use the confirmation preview.'});
 try {
  let body='';for await(const chunk of req) {body+=chunk;if(body.length>(req.url==='/pantry-scan'?1550000:8192)) return send(413,{error:'Request too large'});}
  let input;try {input=JSON.parse(body);} catch{return send(400,{error:'Invalid JSON'});}
  if(req.url==='/pantry-scan'||req.url==='/weekly-plan') {
   try {return send(200,await (req.url==='/pantry-scan'?scanPantry(input):weeklyPlan(input)));} catch(e) {return send(e.message.startsWith('Invalid')?400:503,{error:e.message});}
  }
  if(req.url==='/grocery-quotes') {
   try {return send(200,planGroceries(input,catalog));} catch(e) {return send(400,{error:e.message});}
  }
  if(req.url==='/plan') {
   try {return send(200,await sessionPlan(input));} catch(e) {return send(503,{error:e.message});}
  }
  if(req.url==='/suggest') {
   try {return send(200,await suggestFromPantry(input));} catch(e) {return send(e.message.startsWith('Invalid')?400:503,{error:e.message});}
  }
  if(req.url==='/recommend') {
   try { return send(200,await recommend(input.preference)); } catch(e) { return send(503,{error:e.message}); }
  }
  if(req.url==='/payment-intent') {
   if(!['packages-v1','custom-packages-v1'].includes(input.pricingVersion)) return send(400,{error:'Update the app to use whole-package checkout.'});
   let basket;try {basket=paymentBasket(input);} catch(e){return send(400,{error:e.message});}
   const q=basket.quote;
   if(typeof input.attempt!=='string'||!/^[a-f0-9-]{36}$/.test(input.attempt)) return send(400,{error:'Invalid payment attempt'});
   const form=new URLSearchParams({amount:String(q.amount),currency:'usd','payment_method_types[]':'card',description:'Recipe Wallet TEST — no grocery order','metadata[application]':'recipe-wallet-demo','metadata[recipe]':input.recipeId,'metadata[store]':input.storeId,'metadata[advisoryCard]':input.cardId,'metadata[estimatedRewards]':String(q.rewards),'metadata[pricingVersion]':input.pricingVersion,'metadata[fulfillment]':input.fulfillment||'pickup','metadata[basket]':basket.fingerprint,'metadata[attempt]':basket.attempt});
   const intent=await stripe('payment_intents',form,`recipe-wallet-${input.attempt}`);
   return send(200,{clientSecret:intent.client_secret,paymentIntentId:intent.id,publishableKey,amount:q.amount});
  }
  if(typeof input.paymentIntentId!=='string'||!/^pi_[A-Za-z0-9]+$/.test(input.paymentIntentId)) return send(400,{error:'Invalid PaymentIntent'});
  const intent=await stripe(`payment_intents/${input.paymentIntentId}`);
  try {return send(200,verifyPayment(intent,input));} catch(e){return send(400,{error:e.message});}
 } catch {send(502,{error:'Unable to complete the Stripe test request. Check backend setup and retry.'});}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) {
 if(!configured) console.log('Stripe is not configured. Set test keys in backend/.env. Payment requests will return a setup message.');
 const server=http.createServer(handleRequest);
 server.requestTimeout=100000;
 server.listen(Number(process.env.PORT||4242),process.env.HOST||'127.0.0.1',()=>console.log(`Recipe Wallet demo backend: http://${process.env.HOST||'127.0.0.1'}:${server.address().port}`));
}
