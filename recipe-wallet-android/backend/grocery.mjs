// Quantities use whole grams, milliliters, or item counts. Money uses integer cents.
const units = new Set(['g', 'ml', 'each']);

function identifier(value, label) {
  if (typeof value !== 'string' || !value.trim()) throw new Error(`${label} must be a non-empty string`);
}

function quantity(value, label, minimum = 0) {
  if (!Number.isSafeInteger(value) || value < minimum) {
    throw new Error(`${label} must be a safe integer of at least ${minimum}`);
  }
  return value;
}

function validateRequirements(requirements) {
  if (!Array.isArray(requirements) || !requirements.length) throw new Error('Choose at least one ingredient requirement');
  const seen = new Set();
  for (const item of requirements) {
    if (!item || typeof item !== 'object') throw new Error('Invalid ingredient requirement');
    identifier(item.ingredientId, 'ingredientId');
    if (seen.has(item.ingredientId)) throw new Error('Ingredient requirements must be unique');
    seen.add(item.ingredientId);
    if (!units.has(item.unit)) throw new Error('Use normalized units: g, ml, or each');
    quantity(item.quantity, 'Required quantity');
    quantity(item.pantryQuantity ?? 0, 'Pantry quantity');
  }
}

function validateOffers(offers) {
  if (!Array.isArray(offers)) throw new Error('Product offers must be an array');
  const seen = new Set();
  for (const offer of offers) {
    if (!offer || typeof offer !== 'object') throw new Error('Invalid product offer');
    for (const key of ['id', 'ingredientId', 'storeId']) identifier(offer[key], key);
    if (seen.has(offer.id)) throw new Error('Product offer IDs must be unique');
    seen.add(offer.id);
    if (!units.has(offer.unit)) throw new Error('Product offers must use g, ml, or each');
    quantity(offer.packageQuantity, 'Package quantity', 1);
    quantity(offer.priceCents, 'Package price');
    quantity(offer.stockPackages, 'Available packages');
    if (!Array.isArray(offer.tags) || offer.tags.some(tag => typeof tag !== 'string')) {
      throw new Error('Product tags must be an array of strings');
    }
  }
}

function quoteStore(requirements, storeId, offers) {
  const items = [];
  const missingIngredients = [];
  let subtotal = 0;
  for (const requirement of requirements) {
    const pantryQuantity = requirement.pantryQuantity ?? 0;
    const quantityToBuy = Math.max(0, requirement.quantity - pantryQuantity);
    if (!quantityToBuy) continue;

    const matches = offers.filter(offer => offer.storeId === storeId && offer.ingredientId === requirement.ingredientId);
    const matchingUnits = matches.filter(offer => offer.unit === requirement.unit);
    const candidates = matchingUnits.filter(offer => !requirement.organicOnly || offer.tags.includes('organic')).map(offer => {
      // Keep ceiling division exact even at the supported safe-integer boundary.
      const packagesNeeded = Number((BigInt(quantityToBuy) + BigInt(offer.packageQuantity) - 1n) / BigInt(offer.packageQuantity));
      if (packagesNeeded > offer.stockPackages) return null;
      const purchasedQuantity = quantity(packagesNeeded * offer.packageQuantity, 'Purchased quantity');
      const linePriceCents = quantity(packagesNeeded * offer.priceCents, 'Line price');
      return {
        ingredientId: requirement.ingredientId,
        productId: offer.id,
        unit: requirement.unit,
        requiredQuantity: requirement.quantity,
        pantryQuantity,
        quantityToBuy,
        packageQuantity: offer.packageQuantity,
        packagesNeeded,
        purchasedQuantity,
        priceCents: offer.priceCents,
        linePriceCents,
        tags: [...offer.tags]
      };
    }).filter(Boolean).sort((a, b) =>
      a.linePriceCents - b.linePriceCents ||
      a.purchasedQuantity - b.purchasedQuantity ||
      a.productId.localeCompare(b.productId));

    if (!candidates.length) {
      missingIngredients.push({
        ingredientId: requirement.ingredientId,
        quantityToBuy,
        unit: requirement.unit,
        reason: !matches.length ? 'not-stocked' : !matchingUnits.length ? 'unit-mismatch' : requirement.organicOnly && !matchingUnits.some(o => o.tags.includes('organic')) ? 'preference-unavailable' : 'insufficient-stock'
      });
      continue;
    }
    const chosen = candidates[0];
    items.push(chosen);
    subtotal = quantity(subtotal + chosen.linePriceCents, 'Grocery subtotal');
  }
  const complete = missingIngredients.length === 0;
  // A partial basket must never masquerade as a cheaper complete meal.
  return {storeId, items, missingIngredients, complete, grocerySubtotalCents: complete ? subtotal : null};
}

export function quoteGroceries(requirements, storeId, offers) {
  validateRequirements(requirements);
  identifier(storeId, 'storeId');
  validateOffers(offers);
  return quoteStore(requirements, storeId, offers);
}

export function planGroceries(input, catalog) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('Provide a grocery planning request');
  validateRequirements(input.requirements);
  validateOffers(catalog.productOffers);
  const storeIds = input.storeIds ?? catalog.stores.map(store => store.id);
  if (!Array.isArray(storeIds) || !storeIds.length || new Set(storeIds).size !== storeIds.length ||
      storeIds.some(id => !catalog.stores.some(store => store.id === id))) {
    throw new Error('Choose valid, unique store IDs');
  }
  if (input.organicOnly !== undefined && typeof input.organicOnly !== 'boolean') throw new Error('organicOnly must be boolean');
  if (input.fulfillment !== undefined && !['pickup', 'delivery'].includes(input.fulfillment)) throw new Error('Choose pickup or delivery');
  const requirements = input.requirements.map(r => ({...r, organicOnly: input.organicOnly === true}));
  const quotes = storeIds.map(id => {
    const q = quoteStore(requirements, id, catalog.productOffers);
    if (!input.fulfillment) return q;
    const store = catalog.stores.find(s => s.id === id);
    const available = input.fulfillment !== 'delivery' || store.deliveryEnabled;
    const feesCents = input.fulfillment === 'delivery' ? store.packageDeliveryFee + store.serviceFee : store.pickupFee;
    const taxCents = q.complete ? Math.round(q.grocerySubtotalCents * store.taxBps / 10000) : null;
    return {...q, storeName: store.name, fulfillment: input.fulfillment, fulfillmentAvailable: available,
      complete: q.complete && available, feesCents, taxCents,
      payableCents: q.complete && available ? q.grocerySubtotalCents + feesCents + taxCents : null};
  });
  quotes.sort((a, b) => Number(b.complete) - Number(a.complete) ||
    (a.complete ? (a.payableCents ?? a.grocerySubtotalCents) - (b.payableCents ?? b.grocerySubtotalCents) : 0) || a.storeId.localeCompare(b.storeId));
  return {demo: true, currency: 'usd', pricingScope: input.fulfillment ? 'checkout-total' : 'groceries-only', quotes};
}

export function recipeGroceries(input, catalog) {
  const recipe = catalog.recipes.find(r => r.id === input.recipeId);
  if (!recipe) throw new Error('Unknown recipe');
  const servings = input.servings ?? recipe.baseServings;
  if (!Number.isInteger(servings) || servings < 1 || servings > 12) throw new Error('Choose 1–12 servings');
  if (!Array.isArray(input.ingredientIds) || !input.ingredientIds.length || new Set(input.ingredientIds).size !== input.ingredientIds.length || input.ingredientIds.some(id => !recipe.ingredients.some(i => i.id === id))) throw new Error('Choose valid, unique ingredients');
  return planGroceries({requirements: recipe.ingredients.filter(i => input.ingredientIds.includes(i.id)).map(i => ({ingredientId:i.id, unit:i.unit, quantity:Math.ceil(i.quantity*servings/recipe.baseServings)})), storeIds:[input.storeId], fulfillment:input.fulfillment ?? 'pickup', organicOnly:input.organicOnly ?? false}, catalog).quotes[0];
}
