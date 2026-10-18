import {test} from 'node:test';
import assert from 'node:assert/strict';
import {openMemory,recordPurchase,likelyOwned,ownedHints,markUsedUp,supabaseStore} from './memory.mjs';

const user='11111111-1111-4111-8111-111111111111',other='22222222-2222-4222-8222-222222222222';
const DAY=86400000,t0=1_700_000_000_000;
const buy=(db,pi,ids,now,u=user)=>recordPurchase(db,{userId:u,paymentIntent:pi,recipeId:'noodles',ingredientIds:ids,now});

test('remembers purchases and reports what is probably still in the pantry',async()=>{
 const db=await openMemory(':memory:');await buy(db,'pi_1',['soy','avo','garlic'],t0);
 const owned=await likelyOwned(db,user,t0+5*DAY);
 assert.deepEqual(owned.map(o=>o.id).sort(),['garlic','soy']); // avocado (4-day life) is gone
 assert.equal(owned.find(o=>o.id==='soy').boughtDaysAgo,5);
});
test('purchase recording is idempotent per payment and private per user',async()=>{
 const db=await openMemory(':memory:');
 assert.equal(await buy(db,'pi_1',['soy'],t0),1);assert.equal(await buy(db,'pi_1',['soy'],t0),0);
 assert.equal((await likelyOwned(db,other,t0)).length,0);
});
test('rejects bad users and recipes, ignores unknown ingredients',async()=>{
 const db=await openMemory(':memory:');
 await assert.rejects(()=>recordPurchase(db,{userId:'x',paymentIntent:'pi_1',recipeId:'noodles',ingredientIds:['soy']}),/Invalid user/);
 await assert.rejects(()=>recordPurchase(db,{userId:user,paymentIntent:'pi_1',recipeId:'nope',ingredientIds:['soy']}),/Invalid recipe/);
 assert.equal(await buy(db,'pi_2',['unicorn'],t0),0);
});
test('hints only cover items in the current basket and never change it',async()=>{
 const db=await openMemory(':memory:');await buy(db,'pi_1',['soy','garlic'],t0);
 const plan={ingredientIds:['soy','noodles']};
 const hints=await ownedHints(db,user,plan,t0+DAY);
 assert.deepEqual(hints.map(h=>h.ingredientId),['soy']);
 assert.deepEqual(plan.ingredientIds,['soy','noodles']);
 assert.deepEqual(await ownedHints(db,user,null),[]);
});
test('marking used up removes it until it is bought again',async()=>{
 const db=await openMemory(':memory:');await buy(db,'pi_1',['soy'],t0);
 await markUsedUp(db,user,'soy',t0+DAY);
 assert.equal((await likelyOwned(db,user,t0+2*DAY)).length,0);
 await buy(db,'pi_2',['soy'],t0+3*DAY);
 assert.equal((await likelyOwned(db,user,t0+3*DAY)).length,1);
});
test('hint text reads naturally',async()=>{
 const db=await openMemory(':memory:');await buy(db,'pi_1',['soy'],t0);
 const [h]=await ownedHints(db,user,{ingredientIds:['soy']},t0+3*DAY);
 assert.equal(h.text,'You bought soy sauce 3 days ago. You may already have it.');
});
test('supabase store sends correct REST calls and stays idempotent',async()=>{
 const calls=[];
 const fake=async(url,opts)=>{calls.push({url,method:opts.method,prefer:opts.headers.Prefer,key:opts.headers.apikey,body:opts.body&&JSON.parse(opts.body)});
  if(opts.method==='POST'&&url.includes('/purchases')) return {ok:true,text:async()=>opts.body};
  if(url.includes('/purchases?user_id')) return {ok:true,text:async()=>JSON.stringify([{ingredient_id:'soy',bought_at:t0},{ingredient_id:'soy',bought_at:t0+1000}])};
  return {ok:true,text:async()=>''};};
 const store=supabaseStore({url:'https://x.supabase.co/',key:'service-key',fetchImpl:fake});
 assert.equal(await recordPurchase(store,{userId:user,paymentIntent:'pi_1',recipeId:'noodles',ingredientIds:['soy','unicorn'],now:t0}),1);
 assert.equal(calls[0].url,'https://x.supabase.co/rest/v1/purchases?on_conflict=payment_intent,ingredient_id');
 assert.match(calls[0].prefer,/ignore-duplicates/);assert.equal(calls[0].key,'service-key');
 assert.equal(calls[0].body.length,1);
 assert.deepEqual((await likelyOwned(store,user,t0+2*DAY)).map(o=>o.id),['soy']);
 assert.throws(()=>supabaseStore({url:'',key:''}),/SUPABASE_URL/);
});
