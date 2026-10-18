import test from 'node:test';
import assert from 'node:assert/strict';
import {studioScan} from './pantry-adapter.mjs';
test('studio photo contract preserves reviewed names/amounts and maps only supported ingredient identities',()=>{
 const ingredients=[{name:'Garlic',amount:'2 bulbs'},{name:'Soy sauce',amount:''},{name:'Unknown fruit',amount:'3'},{name:'Yellow onion',amount:'1'}];
 const result=studioScan({ingredients,provider:'perceptron'});
 assert.equal(result.requiresConfirmation,true);assert.equal(result.provider,'perceptron');assert.deepEqual(result.ingredients,ingredients);
 assert.ok(result.ingredientIds.includes('garlic'));assert.ok(result.ingredientIds.includes('soy'));
 assert.ok(!result.ingredientIds.includes('onion'));assert.ok(!result.ingredientIds.includes('Unknown fruit'));
});
