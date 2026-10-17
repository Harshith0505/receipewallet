import {catalog} from './pricing.mjs';
import {planShop} from './agent.mjs';
export function validateRecommendation(value) {
 if(!value || !catalog.recipes.some(r=>r.id===value.recipeId) || typeof value.reason!=='string' || !value.reason.trim() || value.reason.length>800) throw new Error('Invalid recipe recommendation');
 return {recipeId:value.recipeId,reason:value.reason.trim()};
}
// Compatibility endpoint for older demo clients. New clients use /plan for review and revisions.
export async function recommend(preference,options={}) {
 const result=await planShop(preference,{},options);
 if(!result.plan) throw new Error(result.reply);
 return validateRecommendation({recipeId:result.plan.recipeId,reason:result.reply});
}
