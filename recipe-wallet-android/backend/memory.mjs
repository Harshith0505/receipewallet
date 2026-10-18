import {catalog} from './pricing.mjs';

// ---------- Storage backends (same async interface) ----------
// SQLite (default, Node 22+) for local demos; Supabase when SUPABASE_URL and SUPABASE_SERVICE_KEY are set.
export async function sqliteStore(file=process.env.MEMORY_DB||'memory.db') {
 const {DatabaseSync}=await import('node:sqlite');
 const db=new DatabaseSync(file);
 db.exec(`CREATE TABLE IF NOT EXISTS purchases(
  payment_intent TEXT NOT NULL, user_id TEXT NOT NULL, ingredient_id TEXT NOT NULL, recipe_id TEXT NOT NULL, bought_at INTEGER NOT NULL,
  PRIMARY KEY(payment_intent,ingredient_id));
  CREATE INDEX IF NOT EXISTS idx_user ON purchases(user_id,bought_at);
  CREATE TABLE IF NOT EXISTS used_up(user_id TEXT NOT NULL, ingredient_id TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(user_id,ingredient_id));`);
 return {
  async insertPurchases(rows) {
   const q=db.prepare('INSERT OR IGNORE INTO purchases(payment_intent,user_id,ingredient_id,recipe_id,bought_at) VALUES(?,?,?,?,?)');
   let n=0;for(const r of rows) n+=Number(q.run(r.payment_intent,r.user_id,r.ingredient_id,r.recipe_id,r.bought_at).changes);
   return n;
  },
  async clearUsedUp(userId,ids) {const q=db.prepare('DELETE FROM used_up WHERE user_id=? AND ingredient_id=?');for(const id of ids) q.run(userId,id);},
  async setUsedUp(userId,id,at) {db.prepare('INSERT OR REPLACE INTO used_up(user_id,ingredient_id,at) VALUES(?,?,?)').run(userId,id,at);},
  async purchases(userId) {return db.prepare('SELECT ingredient_id,MAX(bought_at) AS bought_at FROM purchases WHERE user_id=? GROUP BY ingredient_id').all(userId);},
  async usedUp(userId) {return db.prepare('SELECT ingredient_id FROM used_up WHERE user_id=?').all(userId).map(r=>r.ingredient_id);}
 };
}

// Uses Supabase's REST API directly (no extra npm package). Run supabase-schema.sql once in the Supabase SQL editor.
// The SERVICE key is a backend secret: keep it in backend/.env, never in the Android app.
export function supabaseStore({url=process.env.SUPABASE_URL,key=process.env.SUPABASE_SERVICE_KEY,fetchImpl=fetch}={}) {
 if(!url||!key) throw new Error('Set SUPABASE_URL and SUPABASE_SERVICE_KEY');
 const base=url.replace(/\/$/,'')+'/rest/v1';
 const headers={apikey:key,Authorization:`Bearer ${key}`,'Content-Type':'application/json'};
 async function call(path,{method='GET',body,prefer}={}) {
  const res=await fetchImpl(base+path,{method,headers:{...headers,...(prefer?{Prefer:prefer}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(15000)});
  if(!res.ok) throw new Error(`Supabase request failed (${res.status})`);
  const text=await res.text();return text?JSON.parse(text):null;
 }
 const enc=encodeURIComponent;
 return {
  async insertPurchases(rows) {
   if(!rows.length) return 0;
   // ignore-duplicates makes recording idempotent per (payment_intent, ingredient_id); returns only newly inserted rows.
   const inserted=await call('/purchases?on_conflict=payment_intent,ingredient_id',{method:'POST',body:rows,prefer:'resolution=ignore-duplicates,return=representation'});
   return Array.isArray(inserted)?inserted.length:0;
  },
  async clearUsedUp(userId,ids) {if(ids.length) await call(`/used_up?user_id=eq.${enc(userId)}&ingredient_id=in.(${ids.map(enc).join(',')})`,{method:'DELETE'});},
  async setUsedUp(userId,id,at) {await call('/used_up?on_conflict=user_id,ingredient_id',{method:'POST',body:[{user_id:userId,ingredient_id:id,at}],prefer:'resolution=merge-duplicates'});},
  async purchases(userId) {
   const rows=await call(`/purchases?user_id=eq.${enc(userId)}&select=ingredient_id,bought_at`);
   const latest=new Map();for(const r of rows||[]) latest.set(r.ingredient_id,Math.max(latest.get(r.ingredient_id)||0,Number(r.bought_at)));
   return [...latest].map(([ingredient_id,bought_at])=>({ingredient_id,bought_at}));
  },
  async usedUp(userId) {return ((await call(`/used_up?user_id=eq.${enc(userId)}&select=ingredient_id`))||[]).map(r=>r.ingredient_id);}
 };
}
export async function openMemory(file) {
 return process.env.SUPABASE_URL&&process.env.SUPABASE_SERVICE_KEY&&!file?supabaseStore():sqliteStore(file);
}

// ---------- Logic (storage-independent) ----------
export const validUser=id=>typeof id==='string'&&/^[a-f0-9-]{36}$/.test(id);
const ingredient=id=>catalog.recipes.flatMap(r=>r.ingredients).find(i=>i.id===id);
const DAY=86400000;
// Rough, illustrative shelf life (days) after buying. Anything not listed defaults to 14.
export const shelfLifeDays={avo:4,greens:5,basil:5,tomato:7,onion:10,lemon:14,garlic:21,cheese:21,noodles:90,pasta:365,rice:180,soy:180,sesame:180,chili:180,oil:180,chickpeas:365};

// Call only after the server has verified a succeeded Stripe PaymentIntent. Idempotent per payment.
export async function recordPurchase(store,{userId,paymentIntent,recipeId,ingredientIds,now=Date.now()}) {
 if(!validUser(userId)) throw new Error('Invalid user');
 if(!catalog.recipes.some(r=>r.id===recipeId)) throw new Error('Invalid recipe');
 const ids=ingredientIds.filter(id=>ingredient(id));
 const n=await store.insertPurchases(ids.map(id=>({payment_intent:paymentIntent,user_id:userId,ingredient_id:id,recipe_id:recipeId,bought_at:now})));
 await store.clearUsedUp(userId,ids); // buying again means it is no longer "used up"
 return n;
}
export async function markUsedUp(store,userId,ingredientId,now=Date.now()) {
 if(!validUser(userId)||!ingredient(ingredientId)) throw new Error('Invalid request');
 await store.setUsedUp(userId,ingredientId,now);
}
// Ingredients bought recently enough that the user probably still has some.
export async function likelyOwned(store,userId,now=Date.now()) {
 if(!validUser(userId)) return [];
 const [rows,usedUp]=await Promise.all([store.purchases(userId),store.usedUp(userId)]);
 const gone=new Set(usedUp);
 return rows.filter(r=>ingredient(r.ingredient_id)).map(r=>{
  const ageDays=Math.floor((now-Number(r.bought_at))/DAY);
  return {id:r.ingredient_id,name:ingredient(r.ingredient_id).name,boughtDaysAgo:ageDays,daysLeft:(shelfLifeDays[r.ingredient_id]??14)-ageDays};
 }).filter(r=>r.daysLeft>0&&!gone.has(r.id)).sort((a,b)=>a.daysLeft-b.daysLeft||a.id.localeCompare(b.id));
}
// Hints for a prepared plan: basket items the user probably already has. Never changes the basket by itself.
export async function ownedHints(store,userId,plan,now=Date.now()) {
 if(!plan) return [];
 const owned=new Map((await likelyOwned(store,userId,now)).map(o=>[o.id,o]));
 return plan.ingredientIds.filter(id=>owned.has(id)).map(id=>{
  const o=owned.get(id);
  const when=o.boughtDaysAgo<=0?'today':o.boughtDaysAgo===1?'yesterday':`${o.boughtDaysAgo} days ago`;
  return {ingredientId:id,name:o.name,boughtDaysAgo:o.boughtDaysAgo,text:`You bought ${o.name.toLowerCase()} ${when}. You may already have it.`};
 });
}
