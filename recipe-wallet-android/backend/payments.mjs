import {createHash} from 'node:crypto';
import {calculate} from './pricing.mjs';

// Bind a test payment to the exact reviewed basket, including the advisory card.
export function paymentBasket(input) {
 const quote=calculate(input);
 if(typeof input.attempt!=='string'||!/^[a-f0-9-]{36}$/.test(input.attempt)) throw new Error('Invalid payment attempt');
 const fingerprint=createHash('sha256').update(JSON.stringify({
  recipeId:input.recipeId,ingredientIds:[...input.ingredientIds].sort(),
  storeId:input.storeId,cardId:input.cardId,
  pricingVersion:input.pricingVersion ?? 'legacy-portions',
  ...(quote.groceryQuote ? {
   servings:input.servings ?? 2,fulfillment:quote.groceryQuote.fulfillment,
   organicOnly:input.organicOnly ?? false,
   fees:quote.groceryQuote.feesCents,tax:quote.groceryQuote.taxCents,
   items:quote.groceryQuote.items
  } : {}),
  ...(quote.customItems ? {customItems:quote.customItems,fulfillment:quote.fulfillment,fees:quote.fees,tax:quote.tax} : {}),
  groceries:quote.groceries,amount:quote.amount,rewards:quote.rewards,currency:'usd'
 })).digest('hex');
 return {quote,fingerprint,attempt:input.attempt};
}

export function verifyPayment(intent,input) {
 const {quote,fingerprint,attempt}=paymentBasket(input);
 if(intent.livemode!==false||intent.metadata?.application!=='recipe-wallet-demo') throw new Error('Unrecognized test payment');
 if(intent.metadata.basket!==fingerprint||intent.metadata.attempt!==attempt) throw new Error('Payment does not match the approved basket');
 if(intent.amount!==quote.amount||intent.currency!=='usd') throw new Error('Payment amount or currency does not match the basket');
 if(intent.status==='succeeded'&&intent.amount_received!==quote.amount) throw new Error('Received amount does not match the basket');
 return {paid:intent.status==='succeeded',status:intent.status,amount:intent.amount,currency:intent.currency,paymentIntentId:intent.id};
}
