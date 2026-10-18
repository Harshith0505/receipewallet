import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createToolRunner,sessionPlan} from './agent.mjs';
import {inferPlanningGoal} from './meal-selection.mjs';
import {catalog} from './pricing.mjs';
const constraints={mealPreference:'dinner',diet:'any',maxPrepMinutes:0,budgetCents:10000,pantryIds:[]};
const compare=(policy,args={})=>{const runner=createToolRunner(policy);const found=runner.run('find_recipes',{...constraints,...args});return {runner,found,...runner.run('compare_baskets',{recipeIds:found.recipes.map(r=>r.id)})};};
test('pantry goal finds the strongest pantry overlap instead of the default cheapest meal',()=>{
 const pantryIds=['rice','avo','chickpeas','greens'];
 const {runner,options,recommendedOptionId}=compare({goal:'pantry'},{pantryIds});
 assert.equal(options[0].recipeId,'bowl');assert.equal(options[0].pantryUsed,4);
 assert.equal(recommendedOptionId,options[0].optionId);
 const other=options.find(o=>o.recipeId!=='bowl');
 assert.throws(()=>runner.run('prepare_basket',{optionId:other.optionId,explanation:'Cheaper'}),/planning goal/);
});
test('surprise excludes recent suggestions while still applying diet and time constraints',()=>{
 const recentRecipeIds=['noodles','bowl'];
 const {found,options}=compare({goal:'surprise',recentRecipeIds},{diet:'vegetarian',maxPrepMinutes:30});
 assert.ok(found.recipes.length>0);assert.ok(found.recipes.every(r=>!recentRecipeIds.includes(r.id)&&r.id!=='pasta'&&r.prepTime<=30));
 assert.ok(!recentRecipeIds.includes(options[0].recipeId));
});
test('profile follows the selected mood and plain meal preferences affect comparison',()=>{
 assert.equal(compare({goal:'profile',preferredRecipeId:'chana'}).options[0].recipeId,'chana');
 assert.equal(compare({}, {mealPreference:'chilled Japanese sesame noodles'}).options[0].recipeId,'sesame-noodles');
 assert.equal(compare({}, {mealPreference:'Tomato basil pasta'}).options[0].recipeId,'pasta');
});
test('cheapest is an explicit goal and never silently replaces budget constraints',()=>{
 const {options}=compare({goal:'cheapest'});
 assert.equal(options[0].amount,Math.min(...options.map(o=>o.amount)));
 const limited=compare({goal:'profile',preferredRecipeId:'chana'},{budgetCents:1200});
 if(limited.options.some(o=>o.withinBudget))assert.ok(limited.options[0].withinBudget);
});
test('invalid selection controls cannot reach a model request',async()=>{
 await assert.rejects(()=>sessionPlan({message:'Dinner',goal:'invented'}),/planning goal/);
 await assert.rejects(()=>sessionPlan({message:'Dinner',recentRecipeIds:['invented']}),/recent recipe/);
});
test('shortcut ranking cannot override cuisine or ingredient exclusions',()=>{
 const {found,options}=compare({goal:'profile',preferredRecipeId:'pasta',cuisine:'Indian',avoid:'cucumber'});
 assert.deepEqual(found.recipes.map(r=>r.id),['chana']);assert.equal(options[0].recipeId,'chana');
 const filtered=compare({goal:'pantry',avoid:'soy'});
 assert.ok(filtered.found.recipes.every(r=>!r.ingredients.some(i=>i.id==='soy')));
});

test('typed requests express the same planning intent as shortcut buttons',()=>{
 assert.equal(inferPlanningGoal('balanced','Use what I have for dinner'),'pantry');
 assert.equal(inferPlanningGoal('balanced','Surprise me with something different'),'surprise');
 assert.equal(inferPlanningGoal('balanced','Find a cheaper dinner'),'cheapest');
 assert.equal(inferPlanningGoal('balanced','Homemade pizza for four'),'balanced');
});

test('pantry-ready meals do not produce an unnecessary shopping recommendation',()=>{
 const {runner,options,readyToCook}=compare({goal:'pantry'},{pantryIds:['rice','avo','chickpeas','greens','lemon']});
 assert.equal(options.length,0);assert.ok(readyToCook.some(r=>r.id==='bowl'));assert.equal(runner.plan,null);
});
test('meal explanations use actual recipe facts instead of model claims about totals or extra ingredients',()=>{
 const {runner,options}=compare({goal:'profile',preferredRecipeId:'chana'},{pantryIds:['chickpeas','rice']});
 const plan=runner.run('prepare_basket',{optionId:options[0].optionId,explanation:'Rice is part of your basket and a real order is under $50.'});
 assert.ok(plan.explanation.includes('Chickpeas'));assert.ok(!plan.explanation.includes('Already have: Rice'));assert.ok(!plan.explanation.includes('$50'));
 assert.ok(plan.explanation.includes('check the shopping total'));
});


test('surprise asks for a new draft when the only cuisine match was just seen',()=>{
 const runner=createToolRunner({goal:'surprise',recentRecipeIds:['mexican-rice'],cuisine:'Mexican'});
 const found=runner.run('find_recipes',constraints);
 assert.equal(found.recipes.length,0);
 assert.match(found.selectionRule,/draft_recipe/);
});
test('profile offers another fitting meal after its first suggestion',()=>{
 const first=compare({goal:'profile',preferredRecipeId:'chana'}).options[0].recipeId;
 const next=compare({goal:'profile',preferredRecipeId:'chana',recentRecipeIds:[first]}).options[0].recipeId;
 assert.notEqual(next,first);
});
