import {recipeGroceries} from './grocery.mjs';
import {readFileSync} from 'node:fs';
export const catalog=JSON.parse(readFileSync(new URL('./demo.json',import.meta.url),'utf8'));
export function calculate(input) {
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
