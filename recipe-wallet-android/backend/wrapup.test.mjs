import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {buildWeeklyWrap, calculateWeeklyNutrition, createWrapupToolRunner, nutrientKeys, nutrition, validateWeeklyInput} from './wrapup.mjs';

const catalog = JSON.parse(readFileSync(new URL('./demo.json', import.meta.url), 'utf8'));
const recipe = id => catalog.recipes.find(r => r.id === id);
const log = (recipeId = 'pasta', changes = {}) => ({id: 'dinner-1', recipeId, date: '2026-10-12', servings: 1, ingredientIds: recipe(recipeId).ingredients.map(i => i.id), ...changes});
const input = (entries = [log()]) => ({weekStart: '2026-10-12', entries});
const response = steps => ({ok: true, json: async () => ({steps})});
const call = (name, args, id = name) => ({type: 'function_call', id, name, arguments: args});

test('shared nutrition fixture covers all catalog ingredients with positive matching unit bases and transparent assumptions', () => {
  assert.deepEqual(nutrition, JSON.parse(readFileSync(new URL('../app/src/main/assets/nutrition.json', import.meta.url), 'utf8')));
  const ids = [...new Set(catalog.recipes.flatMap(r => r.ingredients.map(i => i.id)))].sort();
  assert.deepEqual(nutrition.ingredients.map(p => p.id).sort(), ids);
  assert.equal(new Set(nutrition.ingredients.map(p => p.id)).size, ids.length);
  for (const profile of nutrition.ingredients) {
    assert.ok(profile.basisQuantity > 0);
    assert.ok(profile.assumption.length > 10);
    for (const key of nutrientKeys) assert.ok(Number.isFinite(profile[key]) && profile[key] >= 0);
    for (const ingredient of catalog.recipes.flatMap(r => r.ingredients).filter(i => i.id === profile.id)) assert.equal(profile.unit, ingredient.unit);
  }
  assert.match(nutrition.note, /not exact USDA/);
  assert.match(nutrition.note, /added sugar is not available/i);
});

test('known single ingredient quantity and personal servings determine nutrients; package purchases do not enter calculation', () => {
  const report = calculateWeeklyNutrition(input([log('pasta', {ingredientIds: ['pasta']})]));
  // 227 g dry pasta / 2 base servings = 113.5 g actually eaten.
  assert.deepEqual(report.totals, {proteinG: 14.8, carbsG: 85.1, sugarG: 3.4, fatG: 2.3, fiberG: 3.4, kcal: 421.1});
  assert.equal(report.loggedMeals, 1);
  assert.equal(report.loggedServings, 1);
  assert.equal(report.daysLogged, 1);
  assert.equal(report.daily.length, 7);
  assert.equal(report.daily.filter(d => d.loggedMeals === 0).length, 6);
  assert.match(report.coverage.note, /not your complete intake/);
  assert.equal(report.assumptions.length, 1);
  assert.equal(report.sugarLabel, 'Total sugar, not added sugar');
});

test('all three recipes, quantity fractions and explicit omissions are deterministic', () => {
  for (const r of catalog.recipes) {
    const all = calculateWeeklyNutrition(input([log(r.id, {servings: 2})]));
    const half = calculateWeeklyNutrition(input([log(r.id, {servings: 0.5})]));
    for (const k of nutrientKeys) assert.ok(Math.abs(all.totals[k] / 4 - half.totals[k]) <= 0.075);
  }
  const complete = calculateWeeklyNutrition(input([log()]));
  const withoutOil = calculateWeeklyNutrition(input([log('pasta', {ingredientIds: ['pasta', 'tomato', 'basil', 'cheese']})]));
  assert.ok(complete.totals.fatG > withoutOil.totals.fatG);
  assert.ok(complete.totals.kcal > withoutOil.totals.kcal);
  assert.equal(complete.totals.proteinG, withoutOil.totals.proteinG);
});

test('weekly totals round once and coverage counts unique days, not meal count', () => {
  const report = calculateWeeklyNutrition(input([
    log('pasta', {id: 'one', servings: 0.25, ingredientIds: ['pasta']}),
    log('pasta', {id: 'two', servings: 0.25, ingredientIds: ['pasta']}),
    log('bowl', {id: 'three', date: '2026-10-18', servings: 1.25})
  ]));
  assert.equal(report.loggedServings, 1.75);
  assert.equal(report.loggedMeals, 3);
  assert.equal(report.daysLogged, 2);
  assert.equal(report.favoriteRecipeId, 'pasta');
  assert.equal(report.favoriteRecipeLogs, 2);
  assert.equal(report.daily[0].totals.proteinG, 7.4);
});

test('invalid dates, wrong week, duplicates, unknown recipes and malformed log quantities are rejected', () => {
  for (const bad of [
    {...input(), weekStart: '2026-10-13'}, {...input(), weekStart: '2026-02-30'}, {...input(), weekStart: '12/10/2026'},
    input([log('pasta', {date: '2026-10-19'})]), input([log('pasta', {date: '2026-10-11'})]), input([log('pasta', {date: '2026-10-32'})]),
    input([log(), log()]), input([log('pasta', {recipeId: 'invented'})]),
    ...[0, -1, 0.24, 12.01, NaN, Infinity, '1'].map(servings => input([log('pasta', {servings})])),
    input([log('pasta', {ingredientIds: ['chickpeas']})]), input([log('pasta', {ingredientIds: ['pasta', 'pasta']})]),
    input([log('pasta', {ingredientIds: []})]), input([log('pasta', {ingredientIds: null})]),
    input(Array.from({length: 101}, (_, i) => log('pasta', {id: `log-${i}`}))),
    {...input(), preferences: 'a'.repeat(601)}, {...input(), totals: {proteinG: 9999}},
    input([log('pasta', {totals: {kcal: 0}})])
  ]) assert.throws(() => validateWeeklyInput(bad));
  assert.doesNotThrow(() => validateWeeklyInput(input([log('pasta', {servings: 12})])));
});

test('empty logs have zero known intake, no inferred purchases, and honest local status', async () => {
  const result = await buildWeeklyWrap(input([]), {apiKey: ''});
  assert.equal(result.source, 'local');
  assert.deepEqual(result.totals, Object.fromEntries(nutrientKeys.map(k => [k, 0])));
  assert.equal(result.daysLogged, 0);
  assert.equal(result.favoriteRecipeId, null);
  assert.equal(result.cards[0].kind, 'coverage');
  assert.ok(result.cards.every(c => !/you ate nothing|0 calories eaten/i.test(c.body)));
  assert.equal(result.trace.length, 3);
});

test('tool runner enforces evidence before storytelling and prevents fabricated numeric arguments', () => {
  const runner = createWrapupToolRunner(input());
  assert.throws(() => runner.run('compose_wrapup', {}), /Inspect/);
  runner.run('inspect_log', {});
  assert.throws(() => runner.run('compose_wrapup', {storyIds: ['protein', 'sugar'], nextRecipeId: 'bowl'}), /Compare/);
  const options = runner.run('compare_recipe_options', {goal: 'protein'});
  assert.equal(options.options[0].perServing.proteinG, Math.max(...options.options.map(o => o.perServing.proteinG)));
  assert.throws(() => runner.run('compose_wrapup', {storyIds: ['protein', 'sugar'], nextRecipeId: 'bowl', totals: {proteinG: 9999}}), /Invalid/);
  assert.throws(() => runner.run('compose_wrapup', {storyIds: ['protein', 'diagnosis'], nextRecipeId: 'bowl'}), /stories/);
  assert.throws(() => runner.run('compose_wrapup', {storyIds: ['protein', 'sugar'], nextRecipeId: 'steak'}), /returned/);
  assert.throws(() => runner.run('run_arbitrary_code', {}), /Unknown/);
  runner.run('compose_wrapup', {storyIds: ['sugar', 'protein'], nextRecipeId: options.options[0].recipeId});
  assert.equal(runner.result.cards[0].kind, 'coverage');
  assert.equal(runner.result.cards[1].kind, 'sugar');
  assert.throws(() => runner.run('inspect_log', {}), /already composed/);
});

test('real agent loop gets server observations and curates a grounded story and next recipe', async () => {
  let round = 0;
  const result = await buildWeeklyWrap({...input(), preferences: 'More fiber next week'}, {apiKey: 'test-only', fetchImpl: async (url, options) => {
    assert.match(url, /generativelanguage.googleapis.com/);
    const request = JSON.parse(options.body);
    assert.equal(request.store, false);
    assert.equal(request.tools.length, 3);
    round++;
    if (round === 1) return response([call('inspect_log', {})]);
    if (round === 2) {
      const observation = request.input.find(item => item.type === 'function_result');
      assert.equal(JSON.parse(observation.result[0].text).report.totals.proteinG, calculateWeeklyNutrition(input()).totals.proteinG);
      return response([call('compare_recipe_options', {goal: 'fiber'})]);
    }
    const compared = JSON.parse(request.input.filter(item => item.type === 'function_result').at(-1).result[0].text);
    return response([call('compose_wrapup', {storyIds: ['palette', 'protein', 'sugar'], nextRecipeId: compared.options[0].recipeId})]);
  }});
  assert.equal(round, 3);
  assert.equal(result.source, 'gemini');
  assert.equal(result.comparisonGoal, 'fiber');
  assert.deepEqual(result.trace.map(t => t.tool), ['inspect_log', 'compare_recipe_options', 'compose_wrapup']);
  assert.deepEqual(result.totals, calculateWeeklyNutrition(input()).totals);
});

test('malformed provider, tool misuse and invented nutrition safely fall back to trusted local report', async () => {
  const fixtures = [
    {ok: false}, {ok: true, json: async () => ({})},
    response([{type: 'model_output', content: [{type: 'text', text: 'You ate 99999 grams of protein.'}]}]),
    response([call('compose_wrapup', {storyIds: ['protein', 'sugar'], nextRecipeId: 'bowl'})]),
    response([call('purchase_groceries', {})]),
    response([call('inspect_log', {totals: {proteinG: 9999}})])
  ];
  for (const fixture of fixtures) {
    const result = await buildWeeklyWrap(input(), {apiKey: 'test-only', fetchImpl: async () => fixture});
    assert.equal(result.source, 'local');
    assert.deepEqual(result.totals, calculateWeeklyNutrition(input()).totals);
    assert.ok(!JSON.stringify(result).includes('99999'));
  }
});

test('provider can never overwrite nutrition with its final narrative', async () => {
  const result = await buildWeeklyWrap(input(), {apiKey: 'test-only', fetchImpl: async () => response([
    call('inspect_log', {}), call('compare_recipe_options', {goal: 'variety'}),
    call('compose_wrapup', {storyIds: ['protein', 'sugar'], nextRecipeId: 'bowl'}),
    {type: 'model_output', content: [{type: 'text', text: 'Protein: 99999 g. Doctor-approved diagnosis.'}]}
  ])});
  assert.equal(result.source, 'gemini');
  assert.ok(!JSON.stringify(result).includes('99999'));
  assert.ok(!JSON.stringify(result).includes('Doctor-approved'));
});

test('agent calls are bounded and invalid user logs never invoke the provider', async () => {
  let calls = 0;
  const result = await buildWeeklyWrap(input(), {apiKey: 'test-only', fetchImpl: async () => {
    calls++; return response([call('inspect_log', {})]);
  }});
  assert.equal(result.source, 'local');
  assert.equal(calls, 6);
  let invoked = false;
  await assert.rejects(buildWeeklyWrap(input([log('pasta', {servings: 100})]), {apiKey: 'test-only', fetchImpl: async () => {invoked = true;}}));
  assert.equal(invoked, false);
});

test('vegetarian next-meal comparison excludes Parmesan pasta',()=>{
 const runner=createWrapupToolRunner({weekStart:'2026-10-12',entries:[],diet:'vegetarian'});
 runner.run('inspect_log',{});
 const {options}=runner.run('compare_recipe_options',{goal:'protein'});
 assert.ok(options.length>0);assert.ok(options.every(o=>o.recipeId!=='pasta'));
 assert.throws(()=>runner.run('compose_wrapup',{storyIds:['coverage','palette'],nextRecipeId:'pasta'}),/returned/);
});
