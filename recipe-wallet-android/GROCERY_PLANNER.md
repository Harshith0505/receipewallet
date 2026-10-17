# Task 3: grocery planner

The Android manual flow, AI basket comparison, and server payment calculation now
use whole-package product offers. All 16 unique recipe ingredients have offers at
three demo stores. Quantities are curated for two servings and scale to 1–12.

## What to try on the tablet

1. Choose Tomato basil pasta. Adjust servings and uncheck olive oil to mark it as
   already owned. Compare the package counts and leftover quantities.
2. Switch pickup to delivery. Fees are included in today's total. The Trader
   Joe's demo option disables delivery and cannot be checked out.
3. Enable organic-only. The QFC demo has organic basil out of stock; it appears
   unavailable rather than silently substituting conventional basil. Another
   complete store remains selectable.
4. Open walking directions. The app launches Google Maps at the chosen address.
5. Review package line items and today's charge. Future rewards are separate.
   Preview the confirmation without keys, or use Stripe test checkout with the
   local backend. No grocery order or delivery is actually submitted.

Real locations used for directions:
- [QFC University Village](https://www.qfc.com/stores/grocery/wa/seattle/university-village/705/00807)
- [Trader Joe's U District](https://locations.traderjoes.com/wa/seattle/)
- [Whole Foods Roosevelt Square](https://www.wholefoodsmarket.com/stores/rooseveltsquare)

Prices, stock, product attributes, ratings, fees, and fulfillment options are
fictional demo data, not quotes or integrations with those retailers. The configured
0% demo tax is displayed explicitly; it does not determine real tax liability.

## API contracts

`POST /grocery-quotes` accepts normalized requirements:

```json
{"requirements":[{"ingredientId":"pasta","quantity":600,"pantryQuantity":100,"unit":"g"}],"fulfillment":"pickup","organicOnly":false}
```

Optional `storeIds` selects stores. Units are integer `g`, `ml`, or `each`; the
caller converts other display units. The planner does not guess ingredient
 densities. Duplicate requirements and invalid quantities are rejected.

Each quote returns items, package counts, purchased quantities, prices in cents,
missing ingredients and completeness. With a fulfillment choice it also returns
fees, configured tax, availability and payable total. Without fulfillment, the
response remains groceries-only for compatibility. Incomplete grocery baskets have
null subtotals; fulfillment-unavailable baskets have null payable totals. Complete
baskets rank first by payable total (or grocery subtotal for groceries-only calls).

For each ingredient the planner selects a sufficient single-SKU offer, preferring
lowest package cost, least overbuy, then product ID. Mixing pack sizes, multi-store
optimization, live stock reservation and real delivery dispatch are outside this
single-store prototype.

`POST /payment-intent` requires `pricingVersion: "packages-v1"`, recipeId,
ingredientIds, storeId, cardId, servings, fulfillment, organicOnly, and attempt.
The server recalculates from its own catalog and rejects incomplete baskets. The
Android client checks that amount before opening PaymentSheet. Legacy price math
remains only for compatibility tests, not the current payment endpoint.

The AI defaults to a two-serving pickup basket; manual controls adjust the approved
shopping plan. Person 4 can extend the existing illustrative reward profiles without
changing the grocery subtotal. Person 2 can extend intent parsing using these
structured inputs. Keep Android and backend demo catalogs identical.

## Verification

Run `npm test` in `backend`. `generate-grocery-parity.mjs` regenerates the shared
288-scenario matrix when catalog data changes. Android JVM tests read the same
catalog and matrix to compare its calculator with server totals. Run
`./gradlew :app:testDebugUnitTest :app:assembleDebug` with JDK 17 and SDK 36.
