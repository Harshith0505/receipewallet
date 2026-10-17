import {recipeGroceries} from './grocery.mjs';
import {readFileSync} from 'node:fs';
export const catalog=JSON.parse(readFileSync(new URL('./demo.json',import.meta.url),'utf8'));
export const products=JSON.parse(readFileSync(new URL('./products.json',import.meta.url),'utf8'));
export function calculate(input) {
 if(input.customBasket!==undefined) return calculateCustom(input);
 const recipe=catalog.recipes.find(r=>r.id===input.recipeId);
 const store=catalog.stores.find(s=>s.id===input.storeId);
 const card=catalog.cards.find(c=>c.id===input.cardId);
 if(!recipe||!store||!card) throw new Error('Unknown recipe, store, or card');
 if(!Array.isArray(input.ingredientIds)||!input.ingredientIds.length||new Set(input.ingredientIds).size!==input.ingredientIds.length||input.ingredientIds.some(id=>!recipe.ingredients.some(i=>i.id===id))) throw new Error('Choose valid, unique ingredients');
 if(input.pricingVersion==='packages-v1') {
  const quote=recipeGroceries(input,catalog);
  if(!quote.complete) throw new Error('Selected store cannot fulfill this basket and delivery preference');
  const groceries=quote.grocerySubtotalCents;
  const rewards=Math.round(groceries*(card.rewardType==='points'?card.rate*card.pointsValue:card.rate/100));
  return {groceries,rewards,amount:quote.payableCents,net:quote.payableCents-rewards,store,card,groceryQuote:quote};
 }
 const raw=recipe.ingredients.filter(i=>input.ingredientIds.includes(i.id)).reduce((a,i)=>a+i.basePrice,0);
 const groceries=Math.floor((raw*store.multiplierBps+5000)/10000);
 const rewards=Math.round(groceries*(card.rewardType==='points'?card.rate*card.pointsValue:card.rate/100));
 return {groceries,rewards,amount:groceries+store.deliveryFee,net:groceries+store.deliveryFee-rewards,store,card};
}

function calculateCustom(input) {
 const store=catalog.stores.find(s=>s.id===input.storeId),card=catalog.cards.find(c=>c.id===input.cardId);
 if(!store||!card) throw new Error('Unknown store or card');
 if(input.pricingVersion!=='custom-packages-v1'||typeof input.recipeId!=='string'||!/^saved:[a-f0-9-]{36}$/.test(input.recipeId)) throw new Error('Invalid saved recipe pricing');
 const items=input.customBasket?.items;
 if(!Array.isArray(items)||!items.length||items.length>40||new Set(items.map(i=>i?.productId)).size!==items.length) throw new Error('Choose valid, unique products');
 const fulfillment=input.fulfillment ?? 'pickup';
 if(!['pickup','delivery'].includes(fulfillment)) throw new Error('Choose pickup or delivery');
 if(fulfillment==='delivery'&&!store.deliveryEnabled) throw new Error('Selected store cannot fulfill this delivery preference');
 const customItems=items.map(item=>{
  const product=products.find(p=>p.id===item?.productId);
  if(!product||!Number.isInteger(item.quantity)||item.quantity<1||item.quantity>20) throw new Error('Choose a known product and 1–20 packs');
  const unitPrice=Math.floor((product.price*store.multiplierBps+5000)/10000);
  return {productId:product.id,name:product.name,pack:product.pack,packages:item.quantity,unitPrice,cost:unitPrice*item.quantity};
 }).sort((a,b)=>a.productId.localeCompare(b.productId));
 const groceries=customItems.reduce((sum,item)=>sum+item.cost,0);
 const fees=fulfillment==='pickup'?store.pickupFee:store.packageDeliveryFee+store.serviceFee;
 const tax=Math.round(groceries*store.taxBps/10000);
 const rewards=Math.round(groceries*(card.rewardType==='points'?card.rate*card.pointsValue:card.rate/100));
 return {groceries,rewards,amount:groceries+fees+tax,net:groceries+fees+tax-rewards,store,card,customItems,fulfillment,fees,tax};
}
