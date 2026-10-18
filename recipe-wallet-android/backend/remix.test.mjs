import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createToolRunner,reviseCatalogPlan,sessionPlan} from './agent.mjs';
const initial=()=>{
 const r=createToolRunner();r.run('find_recipes',{mealPreference:'Spicy garlic noodles',diet:'vegetarian',maxPrepMinutes:35,budgetCents:3000,pantryIds:['garlic'],servings:4});
 const {options}=r.run('compare_baskets',{recipeIds:['noodles']});
 return r.run('prepare_basket',{optionId:options[0].optionId,explanation:'Noodles for four.'});
};
test('omit and include update shopping IDs immediately without changing meal or ownership',()=>{
 const first=initial(),mild=reviseCatalogPlan(first,{type:'omit',ingredientId:'chili'}).plan;
 assert.equal(mild.recipeId,first.recipeId);assert.equal(mild.servings,4);assert.equal(mild.constraints.budgetCents,3000);
 assert.ok(!mild.ingredientIds.includes('chili'));assert.deepEqual(mild.omittedIngredientIds,['chili']);assert.ok(!mild.ownedIngredientIds.includes('chili'));
 const spicy=reviseCatalogPlan(mild,{type:'include',ingredientId:'chili'}).plan;
 assert.ok(spicy.ingredientIds.includes('chili'));assert.deepEqual(spicy.omittedIngredientIds,[]);assert.equal(spicy.amount,first.amount);
});
test('pantry refresh preserves earlier ownership and omitted ingredients, and supports an empty shopping list',()=>{
 const mild=reviseCatalogPlan(initial(),{type:'omit',ingredientId:'chili'}).plan;
 const pantry=reviseCatalogPlan(mild,{type:'pantry',pantryIds:['soy']}).plan;
 assert.ok(!pantry.ingredientIds.includes('soy'));assert.ok(pantry.ownedIngredientIds.includes('garlic'));assert.ok(pantry.ownedIngredientIds.includes('soy'));assert.deepEqual(pantry.omittedIngredientIds,['chili']);
 const ready=reviseCatalogPlan(pantry,{type:'pantry',pantryIds:['noodles','sesame','onion','chili']}).plan;
 assert.deepEqual(ready.ingredientIds,[]);assert.equal(ready.shoppingRequired,false);assert.equal(ready.amount,0);assert.ok(!ready.ownedIngredientIds.includes('chili'));
 const included=reviseCatalogPlan(ready,{type:'include',ingredientId:'chili'}).plan;
 assert.ok(included.ownedIngredientIds.includes('chili'));assert.ok(!included.ingredientIds.includes('chili'));
});
test('structured remixes pin the displayed meal, bypass old discovery goals and do not require a model call',async()=>{
 const base=initial();let calls=0;
 const options={apiKey:'',fetchImpl:async()=>{calls++;throw Error('Unexpected model request');}};
 const mild=await sessionPlan({goal:'surprise',message:'Remove chili',currentPlan:base,revision:{type:'omit',ingredientId:'chili'}},options);
 assert.equal(mild.plan.recipeId,base.recipeId);assert.ok(!mild.plan.ingredientIds.includes('chili'));assert.ok(mild.changes.includes('Left out of recipe: Chili flakes'));
 await assert.rejects(()=>sessionPlan({sessionId:mild.sessionId,revision:{type:'include',ingredientId:'invented'}},options),/not part/);
 const spicy=await sessionPlan({sessionId:mild.sessionId,revision:{type:'include',ingredientId:'chili'}},options);
 assert.ok(spicy.plan.ingredientIds.includes('chili'));assert.equal(calls,0);
});
test('invalid edits cannot invent ingredients, ownership or remove the entire recipe',()=>{
 assert.throws(()=>reviseCatalogPlan(initial(),{type:'omit',ingredientId:'invented'}),/not part/);
 assert.throws(()=>reviseCatalogPlan(initial(),{type:'pantry',pantryIds:['invented']}),/pantry/);
 let plan=initial();
 for(const ingredientId of ['noodles','garlic','soy','sesame','chili'])plan=reviseCatalogPlan(plan,{type:'omit',ingredientId}).plan;
 assert.throws(()=>reviseCatalogPlan(plan,{type:'omit',ingredientId:'onion'}),/at least one/);
});
