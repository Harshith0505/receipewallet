import {writeFileSync} from 'node:fs';
import {catalog,calculate} from './pricing.mjs';
const rows=['recipe\tstore\tservings\tfulfillment\torganic\tpantryId\tcomplete\tgroceries\tfees\tamount'];
for(const recipe of catalog.recipes) for(const store of catalog.stores) for(const servings of [1,2,4,12]) for(const fulfillment of ['pickup','delivery']) for(const organicOnly of [false,true]) for(const pantryId of ['',recipe.ingredients[0].id]) {
 const input={pricingVersion:'packages-v1',recipeId:recipe.id,ingredientIds:recipe.ingredients.filter(i=>i.id!==pantryId).map(i=>i.id),storeId:store.id,cardId:catalog.cards[0].id,servings,fulfillment,organicOnly};
 let q;try{q=calculate(input)}catch{}
 rows.push([recipe.id,store.id,servings,fulfillment,organicOnly,pantryId,!!q,q?.groceries??0,q?.groceryQuote.feesCents??0,q?.amount??0].join('\t'));
}
writeFileSync(new URL('../app/src/test/resources/grocery-parity.tsv',import.meta.url),rows.join('\n')+'\n');
