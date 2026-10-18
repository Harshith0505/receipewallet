import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createToolRunner,planShop,diffPlans} from './agent.mjs';
import {catalog} from './pricing.mjs';

const constraints={mealPreference:'noodle dinner',diet:'vegetarian',maxPrepMinutes:0,budgetCents:3000,pantryIds:['garlic']};
test('pantry exclusion, diet filtering and pricing are grounded in catalog tools',()=>{
 const r=createToolRunner();
 const found=r.run('find_recipes',constraints);
 assert.deepEqual(found.recipes.map(x=>x.id),catalog.recipes.filter(r=>r.id!=='pasta').map(r=>r.id));
 const {options}=r.run('compare_baskets',{recipeIds:['noodles']});
 assert.equal(options[0].amount,1580);
 assert.equal(options[0].rewards,79);
 assert.ok(!options[0].ingredientIds.includes('garlic'));
 const p=r.run('prepare_basket',{optionId:options[0].optionId,explanation:'Noodles match your request; garlic is already owned.'});
 assert.equal(p.approvalRequired,true);assert.equal(p.withinBudget,true);
 assert.equal(r.trace.length,3);
});
test('budget applies to amount charged, even if rewards would make net fit',()=>{
 const r=createToolRunner();r.run('find_recipes',{...constraints,budgetCents:1550});
 const {options}=r.run('compare_baskets',{recipeIds:['noodles']});
 assert.ok(options[0].net<=1550);assert.ok(options[0].amount>1550);
 assert.equal(r.run('prepare_basket',{optionId:options[0].optionId,explanation:'This is the closest available option.'}).withinBudget,false);
});
test('unknown tools, invented pantry items, skipped steps and options are rejected',()=>{
 const r=createToolRunner();
 assert.throws(()=>r.run('pay',{}),/Unknown tool/);
 assert.throws(()=>r.run('compare_baskets',{recipeIds:['noodles']}),/Find recipes/);
 assert.throws(()=>r.run('find_recipes',{...constraints,pantryIds:['made-up']}),/Invalid pantry/);
 r.run('find_recipes',constraints);
 assert.throws(()=>r.run('compare_baskets',{recipeIds:['pasta']}),/Invalid recipe/);
 assert.throws(()=>r.run('prepare_basket',{optionId:'fabricated',explanation:'Pay now'}),/Choose an option/);
});
test('no fitting recipe and all-owned ingredients produce no payable basket',()=>{
 const r=createToolRunner();assert.equal(r.run('find_recipes',{...constraints,maxPrepMinutes:1}).recipes.length,0);
 assert.throws(()=>r.run('compare_baskets',{recipeIds:['noodles']}));
 r.run('find_recipes',{...constraints,pantryIds:['noodles','garlic','soy','sesame','chili','onion']});
 assert.equal(r.run('compare_baskets',{recipeIds:['noodles']}).options.length,0);
 assert.equal(r.plan,null);
});
test('an over-budget selection cannot hide a fitting compared option',()=>{
 const r=createToolRunner();r.run('find_recipes',{...constraints,budgetCents:1300});
 const {options}=r.run('compare_baskets',{recipeIds:['noodles','bowl']});
 assert.throws(()=>r.run('prepare_basket',{optionId:options.find(o=>!o.withinBudget).optionId,explanation:'Choose this'}),/fits the budget/);
});
test('real orchestration executes model-requested tools and preserves signed steps',async()=>{
 const requests=[];let turn=0;
 const calls=[
  {name:'find_recipes',arguments:constraints},
  {name:'compare_baskets',arguments:{recipeIds:['noodles']}},
  {name:'prepare_basket',arguments:{optionId:'noodles:fresh:green',explanation:'Garlic is already owned; this is the best estimated noodle basket.'}}
 ];
 const result=await planShop('Vegetarian noodles under $12; I own garlic.',{}, {apiKey:'test',fetchImpl:async(url,options)=>{
  const body=JSON.parse(options.body);requests.push(body);
  assert.equal(body.store,false);assert.equal(url,'https://generativelanguage.googleapis.com/v1beta/interactions');
  const steps=turn<3?[{type:'thought',signature:'test-signature'},{type:'function_call',id:'call_'+turn,...calls[turn]}]:[{type:'model_output',content:[{type:'text',text:'Ready for review.'}]}];
  turn++;return {ok:true,json:async()=>({steps})};
 }});
 assert.equal(result.plan.amount,1580);assert.equal(result.trace.length,3);
 assert.ok(requests[1].input.some(s=>s.type==='thought'&&s.signature==='test-signature'));
 assert.ok(requests[1].input.some(s=>s.type==='function_result'&&s.call_id==='call_0'));
});
test('provider failures and tool-less text never become an approved plan',async()=>{
 await assert.rejects(()=>planShop('Dinner',{}, {apiKey:''}),/Meal planning is unavailable/);
 await assert.rejects(()=>planShop('Dinner',{}, {apiKey:'test',fetchImpl:async()=>({ok:false,status:429,json:async()=>({})})}),/rate limit/);
 const result=await planShop('A question',{}, {apiKey:'test',fetchImpl:async()=>({ok:true,json:async()=>({steps:[{type:'model_output',content:[{type:'text',text:'A plain answer.'}]}]})})});
 assert.equal(result.reply,'A plain answer.');assert.equal(result.plan,null);assert.deepEqual(result.trace,[]);
});
test('follow-up context is passed to Gemini, with no hidden default budget',async()=>{
 let captured;
 const context={constraints,recipeId:'noodles',recentRequests:['Noodles under $12']};
 await assert.rejects(()=>planShop('I own soy sauce too.',context,{apiKey:'test',fetchImpl:async(url,options)=>{
  captured=JSON.parse(JSON.parse(options.body).input[0].content[0].text);
  return {ok:false,status:503,json:async()=>({})};
 }}));
 assert.deepEqual(captured.previousContext,context);assert.equal(captured.message,'I own soy sauce too.');
});

test('diffPlans reports pantry, basket, store, card and total changes from code',()=>{
 const mk=(pantryIds)=>{const r=createToolRunner();r.run('find_recipes',{...constraints,pantryIds});
  const {options}=r.run('compare_baskets',{recipeIds:['noodles']});
  return r.run('prepare_basket',{optionId:options[0].optionId,explanation:'x'});};
 const before=mk(['garlic']),after=mk(['garlic','soy']);
 const changes=diffPlans(before,after);
 assert.ok(changes.includes('Marked as already owned: Soy sauce'));
 assert.ok(changes.includes('Removed from basket: Soy sauce'));
 assert.ok(changes.some(c=>c.startsWith('Upfront total:')));
 assert.deepEqual(diffPlans(null,after),[]);
 assert.deepEqual(diffPlans(after,after),[]);
});

const pizzaDraft={name:'Homemade pizza',servings:4,prepMinutes:40,diet:'vegetarian',budgetCents:3000,maxPrepMinutes:0,ingredients:[{name:'All-purpose flour',amount:'300 g',owned:true},{name:'Mozzarella',amount:'200 g',owned:false},{name:'Tomato sauce',amount:'150 ml',owned:false},{name:'Water',amount:'180 ml',owned:false}],steps:['Prepare the dough, add toppings, and bake until fully cooked.'],explanation:'A homemade pizza draft for four. Review the ingredient quantities before shopping.'};
test('a generated pizza is unpriced and never replaced by a priced catalog meal',async()=>{
 const result=await planShop('Homemade pizza for four; I have flour.',{}, {apiKey:'test',fetchImpl:async()=>({ok:true,json:async()=>({steps:[{type:'function_call',id:'draft-1',name:'draft_recipe',arguments:{...pizzaDraft,amount:1,priceCents:100}}]})})});
 assert.equal(result.plan,null);
 assert.equal(result.generatedRecipe.name,'Homemade pizza');
 assert.equal(result.generatedRecipe.pricingStatus,'unpriced');
 assert.equal(result.generatedRecipe.ingredients[0].owned,true);
 assert.equal('priceCents' in result.generatedRecipe,false);
 assert.equal(result.trace.at(-1).tool,'draft_recipe');
});
test('generated recipe validation rejects invalid quantities, duplicate items and excessive time',()=>{
 for(const change of [{servings:0},{ingredients:[{name:'Flour',amount:'',owned:false}]},{ingredients:[pizzaDraft.ingredients[0],pizzaDraft.ingredients[0]]},{maxPrepMinutes:10}]) {
  const runner=createToolRunner();
  assert.throws(()=>runner.run('draft_recipe',{...pizzaDraft,...change}));
  assert.equal(runner.generatedRecipe,null);
 }
});
test('generated follow-ups receive the actual previous recipe and replace stale catalog context',async()=>{
 const {sessionPlan}=await import('./agent.mjs');
 let previous;
 const options={apiKey:'test',fetchImpl:async(url,init)=>{
  previous=JSON.parse(JSON.parse(init.body).input[0].content[0].text).previousContext;
  return {ok:true,json:async()=>({steps:[{type:'function_call',id:'draft-2',name:'draft_recipe',arguments:pizzaDraft}]})};
 }};
 const first=await sessionPlan({message:'Homemade pizza for four'},options);
 const second=await sessionPlan({message:'I already have flour',sessionId:first.sessionId},options);
 assert.equal(previous.previousGeneratedRecipe.name,'Homemade pizza');
 assert.equal(previous.previousGeneratedRecipe.servings,4);
 assert.equal(second.generatedRecipe.constraints.budgetCents,3000);
 assert.equal(previous.previousPlan,undefined);
});
test('mood remix omits chili without claiming ownership and prices requested servings',()=>{
 const r=createToolRunner();r.run('find_recipes',{...constraints,servings:4,omitIds:['chili']});
 const {options}=r.run('compare_baskets',{recipeIds:['noodles']});
 const p=options[0];assert.equal(p.servings,4);assert.ok(!p.ingredientIds.includes('chili'));assert.ok(!p.ownedIngredientIds.includes('chili'));assert.deepEqual(p.omittedIngredientIds,['chili']);
 assert.ok(p.groceries>0);
 assert.throws(()=>r.run('find_recipes',{...constraints,servings:99}),/Invalid servings/);
});
