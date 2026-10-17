import {test} from 'node:test';
import assert from 'node:assert/strict';
import {catalog} from './pricing.mjs';
import {planGroceries, quoteGroceries} from './grocery.mjs';

const pasta = (quantity = 600, pantryQuantity = 0) => ({ingredientId: 'pasta', quantity, unit: 'g', pantryQuantity});
const market = requirements => quoteGroceries(requirements, 'market', catalog.productOffers.filter(o => o.ingredientId === 'pasta'));

test('pantry quantities reduce whole-package purchases, not the price of a portion', () => {
  const withPantry = market([pasta(600, 100)]);
  assert.equal(withPantry.grocerySubtotalCents, 349);
  assert.deepEqual(withPantry.items[0], {
    ingredientId: 'pasta', productId: 'market-pasta-500g', unit: 'g', requiredQuantity: 600,
    pantryQuantity: 100, quantityToBuy: 500, packageQuantity: 500, packagesNeeded: 1,
    purchasedQuantity: 500, priceCents: 349, linePriceCents: 349, tags: ['conventional']
  });
  assert.equal(market([pasta()]).items[0].packagesNeeded, 2);
  assert.equal(market([pasta()]).grocerySubtotalCents, 698);
});

test('package rounding changes which store offers the cheapest complete basket', () => {
  const request = pantryQuantity => ({requirements: [pasta(600, pantryQuantity)]});
  const noneOwned = planGroceries(request(0), catalog);
  assert.deepEqual(noneOwned.quotes.map(q => [q.storeId, q.grocerySubtotalCents]), [
    ['pantry', 599], ['fresh', 627], ['market', 698]
  ]);
  assert.equal(planGroceries(request(100), catalog).quotes[0].storeId, 'market');
  assert.equal(noneOwned.pricingScope, 'groceries-only');
});

test('fully owned ingredients require no product, stock, or payment', () => {
  const quote = quoteGroceries([pasta(600, 800)], 'market', []);
  assert.deepEqual(quote, {storeId: 'market', items: [], missingIngredients: [], complete: true, grocerySubtotalCents: 0});
});

test('an incomplete basket has no total and cannot outrank a complete basket', () => {
  const limited = {...catalog, productOffers: catalog.productOffers.map(o =>
    o.storeId === 'pantry' ? {...o, stockPackages: 0} : o)};
  const result = planGroceries({requirements: [pasta()]}, limited);
  assert.equal(result.quotes[0].storeId, 'fresh');
  const missing = result.quotes.at(-1);
  assert.equal(missing.storeId, 'pantry');
  assert.equal(missing.grocerySubtotalCents, null);
  assert.deepEqual(missing.missingIngredients, [{ingredientId: 'pasta', quantityToBuy: 600, unit: 'g', reason: 'insufficient-stock'}]);
});

test('multiple ingredients are priced together and unavailable ingredients stay explicit', () => {
  const oil = {id: 'market-oil-250ml', ingredientId: 'oil', storeId: 'market', packageQuantity: 250,
    unit: 'ml', priceCents: 499, stockPackages: 3, tags: []};
  const requirements = [pasta(500), {ingredientId: 'oil', quantity: 30, unit: 'ml'}];
  assert.equal(quoteGroceries(requirements, 'market', [...catalog.productOffers, oil]).grocerySubtotalCents, 848);
  const partial = quoteGroceries(requirements, 'market', catalog.productOffers.filter(o => o.ingredientId === 'pasta'));
  assert.equal(partial.complete, false);
  assert.equal(partial.grocerySubtotalCents, null);
  assert.equal(partial.missingIngredients[0].reason, 'not-stocked');
  assert.equal(partial.items[0].linePriceCents, 349);
});

test('one available package does not satisfy a two-package requirement', () => {
  const offer = catalog.productOffers.find(o => o.storeId === 'market');
  const quote = quoteGroceries([pasta(600)], 'market', [{...offer, stockPackages: 1}]);
  assert.equal(quote.complete, false);
  assert.equal(quote.grocerySubtotalCents, null);
  assert.equal(quote.items.length, 0);
  assert.equal(quote.missingIngredients[0].reason, 'insufficient-stock');
});

test('does not guess conversions between grams, milliliters, and item counts', () => {
  const quote = market([{...pasta(), unit: 'ml'}]);
  assert.equal(quote.complete, false);
  assert.equal(quote.missingIngredients[0].reason, 'unit-mismatch');
});

test('selects the cheapest sufficient offer and uses overbuy then product ID to break ties', () => {
  const offer = catalog.productOffers.find(o => o.storeId === 'market');
  const otherOffers = [
    {...offer, id: 'cheap-but-unavailable', priceCents: 1, stockPackages: 0},
    {...offer, id: 'b', packageQuantity: 500, priceCents: 300},
    {...offer, id: 'a', packageQuantity: 500, priceCents: 300},
    {...offer, id: 'larger', packageQuantity: 1000, priceCents: 300}
  ];
  assert.equal(quoteGroceries([pasta(500)], 'market', otherOffers).items[0].productId, 'a');
});

test('rejects invalid quantities, ambiguous units, duplicate IDs, and monetary overflow', () => {
  for (const requirements of [[], [pasta(), pasta()], [pasta(-1)], [pasta(0.5)],
    [pasta(600, -1)], [{...pasta(), unit: 'cup'}], [pasta(Infinity)], [null]]) {
    assert.throws(() => market(requirements));
  }
  const offer = catalog.productOffers[0];
  for (const offers of [[{...offer, packageQuantity: 0}], [{...offer, priceCents: -1}],
    [{...offer, stockPackages: 0.5}], [offer, offer], [{...offer, tags: 'organic'}]]) {
    assert.throws(() => quoteGroceries([pasta()], 'market', offers));
  }
  assert.throws(() => quoteGroceries([pasta(2)], 'market', [
    {...offer, packageQuantity: 1, priceCents: Number.MAX_SAFE_INTEGER, stockPackages: 2}
  ]));
});

test('store selection is explicit and client-supplied prices are ignored', () => {
  const result = planGroceries({requirements: [{...pasta(), priceCents: 1}], storeIds: ['market'], amount: 1,
    productOffers: []}, catalog);
  assert.equal(result.quotes.length, 1);
  assert.equal(result.quotes[0].grocerySubtotalCents, 698);
  for (const storeIds of [[], ['market', 'market'], ['invented']]) {
    assert.throws(() => planGroceries({requirements: [pasta()], storeIds}, catalog));
  }
});
