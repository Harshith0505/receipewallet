import {readFileSync} from 'node:fs';
export const catalog=JSON.parse(readFileSync(new URL('./demo.json',import.meta.url),'utf8'));
export const products=JSON.parse(readFileSync(new URL('./products.json',import.meta.url),'utf8'));
export function calculate(input) {
 const store=catalog.stores.find(s=>s.id===input.storeId);
 const card=catalog.cards.find(c=>c.id===input.cardId);
 if(!store||!card) throw new Error('Unknown store or card');
 let raw;
 if(input.customBasket!==undefined) {
  if(typeof input.recipeId!=='string'||!/^saved:[a-f0-9-]{36}$/.test(input.recipeId)) throw new Error('Invalid saved recipe');
  const items=input.customBasket?.items;
  if(!Array.isArray(items)||!items.length||items.length>40||new Set(items.map(i=>i?.productId)).size!==items.length) throw new Error('Choose valid, unique products');
  raw=items.reduce((sum,item)=>{
   const product=products.find(p=>p.id===item?.productId);
   if(!product||!Number.isInteger(item.quantity)||item.quantity<1||item.quantity>20) throw new Error('Choose a known product and 1–20 packs');
   return sum+product.price*item.quantity;
  },0);
 } else {
  const recipe=catalog.recipes.find(r=>r.id===input.recipeId);
  if(!recipe) throw new Error('Unknown recipe, store, or card');
  if(!Array.isArray(input.ingredientIds)||!input.ingredientIds.length||new Set(input.ingredientIds).size!==input.ingredientIds.length||input.ingredientIds.some(id=>!recipe.ingredients.some(i=>i.id===id))) throw new Error('Choose valid, unique ingredients');
  raw=recipe.ingredients.filter(i=>input.ingredientIds.includes(i.id)).reduce((a,i)=>a+i.basePrice,0);
 }
 const groceries=Math.floor((raw*store.multiplierBps+5000)/10000);
 const rewards=Math.round(groceries*(card.rewardType==='points'?card.rate*card.pointsValue:card.rate/100));
 return {groceries,rewards,amount:groceries+store.deliveryFee,net:groceries+store.deliveryFee-rewards,store,card};
}
