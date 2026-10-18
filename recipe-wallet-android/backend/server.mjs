import http from 'node:http';
import {pathToFileURL} from 'node:url';
import {catalog} from './pricing.mjs';
import {paymentBasket,verifyPayment} from './payments.mjs';
import {planGroceries} from './grocery.mjs';
import {recommend} from './recommend.mjs';
import {sessionPlan} from './agent.mjs';
import {suggestFromPantry} from './pantry.mjs';
import {createRetailer, RetailerFailure} from './retailer.mjs';
import {createKrogerCart, krogerReturnHtml} from './kroger-cart.mjs';
const retailer = createRetailer();
const krogerCart = createKrogerCart({retailer});
import {scanPantry,ScanError} from './pantry-scan.mjs';
let activeScans=0;
import {openMemory,recordPurchase,likelyOwned,ownedHints,markUsedUp,validUser} from './memory.mjs';
let memoryPromise;
const purchaseMemory=()=>memoryPromise??=openMemory();
import {weeklyPlan} from './studio.mjs';
import {buildWeeklyWrap} from './wrapup.mjs';
import {studioScan} from './pantry-adapter.mjs';
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
 let url;try {url=new URL(req.url,'http://127.0.0.1');} catch {return send(400,{error:'Invalid request URL'});}
 if(req.method==='GET'&&url.pathname==='/kroger/callback') {
  try { await krogerCart.callback(url.searchParams); } catch { /* The app checks its own connection. Never return authorization codes or provider errors to the browser. */ }
  res.setHeader('Referrer-Policy','no-referrer');
  res.writeHead(303,{Location:'/kroger/return'});res.end();return;
 }
 if(req.method==='GET'&&url.pathname==='/kroger/return') {
  res.setHeader('Content-Type','text/html; charset=utf-8');res.setHeader('Referrer-Policy','no-referrer');
  res.setHeader('Content-Security-Policy',"default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
  res.writeHead(200);res.end(krogerReturnHtml());return;
 }
 if(req.method==='GET'&&req.url==='/health') return send(200,{ok:true,stripeConfigured:configured,aiConfigured:!!process.env.GEMINI_API_KEY,krogerConfigured:retailer.configured,krogerCartConfigured:krogerCart.configured,pantryScanConfigured:!!process.env.PERCEPTRON_API_KEY});
 if(req.method!=='POST'||!['/payment-intent','/verify','/recommend','/plan','/grocery-quotes','/suggest','/memory','/used-up','/pantry-scan','/weekly-plan','/weekly-wrap','/retailers/stores','/retailers/products','/retailers/basket','/retailers/cart/connect','/retailers/cart/status','/retailers/cart/add','/retailers/cart/disconnect'].includes(req.url)) return send(404,{error:'Not found'});
 if(['/payment-intent','/verify'].includes(req.url)&&!configured) return send(503,{error:'Stripe test keys are not configured on the backend. Follow README setup, or use the confirmation preview.'});
 try {
  const limit=req.url==='/pantry-scan'?4_001_024:req.url==='/weekly-wrap'?64000:8192;
  let body='',bodyBytes=0;for await(const chunk of req) {bodyBytes+=Buffer.byteLength(chunk);if(bodyBytes>limit) return send(413,{error:'Request too large'});body+=chunk;}
  let input;try {input=JSON.parse(body);} catch{return send(400,{error:'Invalid JSON'});}
  if(req.url.startsWith('/retailers/cart/')) {
   try { return send(200,await krogerCart[req.url.split('/').pop()](input)); }
   catch(e) {return send(e instanceof RetailerFailure?e.status:502,{error:e instanceof RetailerFailure?e.message:'Kroger cart service is unavailable.',...(e.transferStatus?{transferStatus:e.transferStatus}:{})});}
  }
  if(req.url.startsWith('/retailers/')) {
   try { return send(200,await retailer[req.url.split('/').pop()](input)); }
   catch(e) {return send(e instanceof RetailerFailure?e.status:502,{error:e instanceof RetailerFailure?e.message:'Retailer search is unavailable. Please try again.'});}
  }
  if(req.url==='/pantry-scan') {
   if(activeScans>=2) return send(429,{error:'Photo scanning is busy. Please try again shortly.'});
   activeScans++;
   try {return send(200,studioScan(await scanPantry({...input,imageBase64:input?.imageBase64??input?.image})));}
   catch(e) {return send(e instanceof ScanError?e.status:502,{error:e instanceof ScanError?e.message:'Couldn’t scan this photo. Please retry.'});}
   finally {activeScans--;}
  }
  if(req.url==='/weekly-plan'||req.url==='/weekly-wrap') {
   try {return send(200,await (req.url==='/weekly-plan'?weeklyPlan(input):buildWeeklyWrap(input)));} catch(e){return send(e.message.startsWith('Invalid')?400:503,{error:e.message});}
  }
  if(req.url==='/grocery-quotes') {
   try {return send(200,planGroceries(input,catalog));} catch(e) {return send(400,{error:e.message});}
  }
  if(req.url==='/plan') {
   if(!input||typeof input!=='object'||Array.isArray(input)) return send(400,{error:'Invalid planning request'});
   const message=input.message??input.prompt;
   if((input.message!==undefined&&input.prompt!==undefined&&input.message!==input.prompt)||
      ((message!==undefined||!input.revision)&&(typeof message!=='string'||!message.trim()||message.length>2400))) {
    return send(400,{error:'Describe your shop in 1–2400 characters using message or prompt.'});
   }
   try {const out=await sessionPlan({...input,message});return send(200,{...out,ownedHints:validUser(input.userId)?await ownedHints(await purchaseMemory(),input.userId,out.plan).catch(()=>[]):[]});} catch(e) {return send(e.status===429?429:503,{error:e.message});}
  }
  if(req.url==='/memory') return send(200,{likelyOwned:await likelyOwned(await purchaseMemory(),input.userId).catch(()=>[])});
  if(req.url==='/used-up') {try {await markUsedUp(await purchaseMemory(),input.userId,input.ingredientId);return send(200,{ok:true});} catch(e) {return send(400,{error:e.message});}}
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
   const form=new URLSearchParams({amount:String(q.amount),currency:'usd','payment_method_types[]':'card',description:'Recipe Wallet TEST — no grocery order','metadata[application]':'recipe-wallet-demo','metadata[recipe]':input.recipeId,'metadata[store]':input.storeId,'metadata[advisoryCard]':input.cardId,'metadata[estimatedRewards]':String(q.rewards),'metadata[pricingVersion]':input.pricingVersion,'metadata[fulfillment]':input.fulfillment||'pickup','metadata[basket]':basket.fingerprint,'metadata[attempt]':basket.attempt,'metadata[ingredients]':input.ingredientIds.join(','),...(validUser(input.userId)?{'metadata[user]':input.userId}:{})});
   const intent=await stripe('payment_intents',form,`recipe-wallet-${input.attempt}`);
   return send(200,{clientSecret:intent.client_secret,paymentIntentId:intent.id,publishableKey,amount:q.amount});
  }
  if(typeof input.paymentIntentId!=='string'||!/^pi_[A-Za-z0-9]+$/.test(input.paymentIntentId)) return send(400,{error:'Invalid PaymentIntent'});
  const intent=await stripe(`payment_intents/${input.paymentIntentId}`);
  try {
   const result=verifyPayment(intent,input);
   if(result.paid&&validUser(intent.metadata?.user)&&catalog.recipes.some(r=>r.id===intent.metadata.recipe)) {
    try {await recordPurchase(await purchaseMemory(),{userId:intent.metadata.user,paymentIntent:intent.id,recipeId:intent.metadata.recipe,ingredientIds:(intent.metadata.ingredients||'').split(',').filter(Boolean)});} catch { /* Test purchase memory never blocks a verified receipt. */ }
   }
   return send(200,result);
  } catch(e){return send(400,{error:e.message});}
 } catch {send(502,{error:'Unable to complete the Stripe test request. Check backend setup and retry.'});}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) {
 if(!configured) console.log('Stripe is not configured. Set test keys in backend/.env. Payment requests will return a setup message.');
 const server=http.createServer(handleRequest);
 server.requestTimeout=100000;
 server.listen(Number(process.env.PORT||4242),process.env.HOST||'127.0.0.1',()=>console.log(`Recipe Wallet demo backend: http://${process.env.HOST||'127.0.0.1'}:${server.address().port}`));
}
