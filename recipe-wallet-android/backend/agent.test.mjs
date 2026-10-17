import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createToolRunner,planShop,diffPlans} from './agent.mjs';

const constraints={mealPreference:'noodle dinner',diet:'vegetarian',maxPrepMinutes:0,budgetCents:3000,pantryIds:['garlic']};
test('pantry exclusion, diet filtering and pricing are grounded in catalog tools',()=>{
 const r=createToolRunner();
 const found=r.run('find_recipes',constraints);
 assert.deepEqual(found.recipes.map(x=>x.id),['noodles','bowl']);
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
 await assert.rejects(()=>planShop('Dinner',{}, {apiKey:''}),/GEMINI_API_KEY/);
 await assert.rejects(()=>planShop('Dinner',{}, {apiKey:'test',fetchImpl:async()=>({ok:false,status:429,json:async()=>({})})}),/rate limit/);
 await assert.rejects(()=>planShop('Dinner',{}, {apiKey:'test',fetchImpl:async()=>({ok:true,json:async()=>({steps:[{type:'model_output',content:[{type:'text',text:'Paid!'}]}]})})}),/did not check/);
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
