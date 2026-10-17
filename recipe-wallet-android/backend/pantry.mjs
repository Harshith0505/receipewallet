import {catalog} from './pricing.mjs';

const ingredientIds=new Set(catalog.recipes.flatMap(r=>r.ingredients.map(i=>i.id)));
const name=id=>catalog.recipes.flatMap(r=>r.ingredients).find(i=>i.id===id)?.name||id;

export function validatePantry(pantryIds) {
 if(!Array.isArray(pantryIds)||pantryIds.length>40||new Set(pantryIds).size!==pantryIds.length||pantryIds.some(id=>!ingredientIds.has(id))) throw new Error('Invalid pantry ingredients');
 return pantryIds;
}

// Pure code, no AI: Gemini can never invent a recipe or an ingredient here.
// Ranks every catalog recipe by how much of it the user already owns.
export function rankRecipes(pantryIds,{diet='any',maxPrepMinutes=0}={}) {
 validatePantry(pantryIds);
 return catalog.recipes
  .filter(r=>(diet==='any'||r.id!=='pasta')&&(!maxPrepMinutes||r.prepTime<=maxPrepMinutes)) // pasta: Parmesan rennet is unspecified
  .map(r=>{
   const have=r.ingredients.filter(i=>pantryIds.includes(i.id));
   const missing=r.ingredients.filter(i=>!pantryIds.includes(i.id));
   return {
    recipeId:r.id,recipeName:r.name,emoji:r.emoji,prepTime:r.prepTime,
    haveIds:have.map(i=>i.id),missingIds:missing.map(i=>i.id),
    haveCount:have.length,totalCount:r.ingredients.length,
    canCookNow:missing.length===0,
    summary:missing.length===0
     ?`You have everything for ${r.name}.`
     :`You have ${have.length} of ${r.ingredients.length} ingredients for ${r.name}. Still needed: ${missing.map(i=>i.name).join(', ')}.`
   };
  })
  .sort((a,b)=>b.haveCount/b.totalCount-a.haveCount/a.totalCount||a.missingIds.length-b.missingIds.length||a.recipeId.localeCompare(b.recipeId));
}

const system='You are Recipe Wallet. Given ranked recipe matches computed by the server, write ONE or TWO friendly sentences recommending which to cook and why. Use only the recipes, ingredient names and counts provided. Do not invent ingredients, substitutions, prices or quantities.';

// Optional Gemini sentence. Falls back to a plain code-written sentence if Gemini is missing or fails.
export async function suggestFromPantry(input,{apiKey=process.env.GEMINI_API_KEY,model=process.env.GEMINI_MODEL||'gemini-3.5-flash-lite',fetchImpl=fetch}={}) {
 const pantryIds=validatePantry(input?.pantryIds);
 const suggestions=rankRecipes(pantryIds,{diet:input.diet||'any',maxPrepMinutes:input.maxPrepMinutes||0});
 const best=suggestions[0];
 let reply=best?(best.canCookNow?`You can cook ${best.recipeName} right now.`:`${best.recipeName} is your closest match. ${best.summary}`):'No recipes match those filters.';
 if(apiKey&&best) {
  try {
   const response=await fetchImpl('https://generativelanguage.googleapis.com/v1beta/interactions',{method:'POST',headers:{'Content-Type':'application/json','x-goog-api-key':apiKey},body:JSON.stringify({model,store:false,system_instruction:system,input:[{type:'user_input',content:[{type:'text',text:JSON.stringify({pantry:pantryIds.map(name),matches:suggestions.map(s=>({recipe:s.recipeName,have:s.haveIds.map(name),missing:s.missingIds.map(name)}))})}]}]}),signal:AbortSignal.timeout(20000)});
   const data=await response.json();
   const text=response.ok&&Array.isArray(data.steps)?data.steps.filter(s=>s.type==='model_output').flatMap(s=>s.content||[]).filter(c=>c.type==='text').map(c=>c.text).join('').trim():'';
   if(text&&text.length<=600) reply=text;
  } catch {/* keep the code-written reply */}
 }
 return {suggestions,reply};
}
