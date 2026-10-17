# Grocery planner: package-pricing foundation

This is Person 3's first slice: price the packages that must actually be bought,
subtract pantry quantities, and compare complete grocery baskets across stores.
The three new pasta offers are fictional demo data, mirrored in the Android asset.

The planner is available now through a separate backend endpoint. The Android
screens, AI planner, reward calculator, and Stripe checkout still use the existing
portion-price flow. Do not send a new grocery quote to the old payment flow: its
amount is calculated using the legacy catalog. Wiring the full recipe catalog and
approved checkout amount together is the next integration step.

## Try it

From `recipe-wallet-android/backend`, run `npm start`. No Gemini or Stripe key is
needed for grocery quotes. In another terminal:

```bash
curl http://127.0.0.1:4242/grocery-quotes \
  -H 'Content-Type: application/json' \
  -d '{"requirements":[{"ingredientId":"pasta","quantity":600,"pantryQuantity":100,"unit":"g"}]}'
```

For this example the recipe needs 600 g and the shopper owns 100 g:

| Store ID | Product package | Packages to buy | Grocery subtotal |
| --- | --- | --- | --- |
| market | 500 g for $3.49 | 1 | $3.49 |
| fresh | 250 g for $2.09 | 2 | $4.18 |
| pantry | 1,000 g for $5.99 | 1 | $5.99 |

With `pantryQuantity: 0`, the quotes are market $6.98, fresh $6.27, and pantry
$5.99. The cheapest store changes because the shopper buys whole packages.

## Input contract

```json
{
  "requirements": [
    {"ingredientId": "pasta", "quantity": 600, "pantryQuantity": 100, "unit": "g"}
  ],
  "storeIds": ["market", "fresh", "pantry"]
}
```

- `storeIds` is optional; omitting it compares all demo stores.
- Requirements must have unique ingredient IDs and non-negative integer
  quantities in `g`, `ml`, or `each`. `pantryQuantity` defaults to zero.
- The caller normalizes recipe quantities before calling. The planner does not
  parse display text such as `8 oz`, infer densities, or convert cups to grams.
- Product prices, package sizes, and stock come from the server's `productOffers`
  catalog. Client-supplied prices and totals cannot change the calculation.
- Each product offer has `id`, `ingredientId`, `storeId`, `packageQuantity`, `unit`,
  `priceCents`, `stockPackages`, and descriptive `tags`.

## Output contract

The response has `demo: true`, `currency: "usd"`, `pricingScope: "groceries-only"`,
and `quotes`. Each quote contains:

- `storeId`, `complete`, and `grocerySubtotalCents`.
- `items`: product ID, required/pantry/purchased quantities, packages needed,
  package price, line price, unit, and tags.
- `missingIngredients`: quantity still needed and a reason of `not-stocked`,
  `insufficient-stock`, or `unit-mismatch`.

An incomplete basket has a **null subtotal**, not a misleading partial total.
Complete baskets sort first by grocery subtotal, then store ID. Fully pantry-owned
ingredients require no product purchase, even when a store does not stock them.
For each ingredient the first version picks the cheapest sufficient single-SKU
offer, breaking ties by least overbuy and then product ID. It does not mix package
sizes for the same ingredient.

This endpoint does not reserve inventory or place an order. Stock is a demo
snapshot. Fees, taxes, fulfillment options, quality filtering, substitutions,
rewards, and route planning are subsequent work; none are silently included in
the grocery subtotal. Tags describe fictional products, not inferred quality.

## Team handoff

- Person 1 can consume `/grocery-quotes` for package counts and store comparisons.
- Person 2 supplies explicit quantities after servings and pantry adjustments.
  Missing ingredients must be resolved or reviewed, never silently omitted.
- Person 4 adds applicable fees/taxes and rewards after the grocery quote is
  selected, with server recalculation before payment.
- Keep `backend/demo.json` and `app/src/main/assets/demo.json` identical.

Run `npm test` in `backend` for package, pantry, stock, validation, catalog parity,
HTTP-contract checks, and the existing AI and payment-calculation regressions.
