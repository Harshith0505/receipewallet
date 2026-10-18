# Kroger store and product lookup

This feature requests real catalog data from the Kroger API. It covers Kroger-family stores returned by the provider (including banners such as QFC/Fred Meyer where available). It does not provide a comparison across all grocery retailers.

## Enable

1. Sign in at [Kroger Developer](https://developer.kroger.com/) and create an application with the public Products and Locations APIs enabled. Get the application's client ID and client secret. Account/API approval is controlled by Kroger.
2. In the ignored `backend/.env` file, set:

   ```dotenv
   KROGER_CLIENT_ID=your_client_id
   KROGER_CLIENT_SECRET=your_client_secret
   ```

   These are server credentials. Never put them in Android resources, `supabase.properties`, source control, screenshots, or chat.
3. Restart the backend with `npm start` from `backend/`. `GET /health` reports `krogerConfigured` for presence only; an actual search verifies access.
4. Run `adb reverse tcp:4242 tcp:4242` for your chosen device (add `-s DEVICE_SERIAL` when multiple devices are connected). The device needs internet for Supabase; Kroger requests run through the computer's backend.
5. On the home screen tap **Check retailer prices**, enter a five-digit US ZIP code, and tap **Find nearby stores**. Select a store, then search for a product. The ingredient screen also has **Look up retailer prices**, with the selected recipe's remaining ingredients and required amounts. Pantry exclusions are preserved.
6. For a recipe, tap **Search [ingredient]** and explicitly choose **Use for [ingredient]**. Check the product's package size and set enough whole packages yourself; the app does not infer conversions or dietary/organic suitability. In general browsing, use **Add product** instead. Products need a listed regular unit price, package size, and `soldBy: UNIT`; reported out-of-stock products cannot be selected. Products sold by weight require a separate future quantity calculation.
7. Match every required ingredient and tap **Refresh & review live basket**. The backend fetches each selected product again at the chosen location and derives the item subtotal from those fresh regular prices. Review the latest amount. Product/quantity edits discard that quote; switching stores clears selections. Changing the recipe/servings/pantry context resets the live basket, and late results from an older context cannot replace it.

## Data contract and limitations

- `POST /retailers/stores` accepts `{ "postalCode": "98105" }`. The lookup requests up to five Kroger-family locations within 10 miles. Store names and addresses come from the API. No ratings or exact user distances are fabricated.
- `POST /retailers/products` accepts `{ "storeId": "PROVIDER_LOCATION_ID", "query": "pasta" }`. Every product lookup supplies `filter.locationId` to request the selected store's price and availability. Up to ten product results are requested; item variants are preserved.
- `POST /retailers/basket` accepts `{ "storeId": "PROVIDER_LOCATION_ID", "items": [{ "productId": "PROVIDER_PRODUCT_ID", "itemId": "PROVIDER_ITEM_ID", "quantity": 2 }] }`. Up to 12 distinct variants and 1–20 combined packages per variant are supported. Duplicate variants must be combined by the client. The backend re-fetches `/products/{id}?filter.locationId=...`, validates the exact variant and unit/price/size, and ignores client-supplied prices or totals. Results have `pricingScope: "item-subtotal-only"`, refreshed line subtotals and `subtotalCents`. Unknown tax, fees, final charge and rewards remain null. Unknown stock does not become a verified quantity. No retailer order is placed.
- Responses include `provider: "kroger"`, `demo: false` and `fetchedAt`. Regular and promotional prices are separate integer-cent fields. Missing prices, package sizes, fulfillment flags, inventory, or price basis remain null. A missing/zero price is not a free product. Empty results do not establish that a product is out of stock.
- Promotional prices can depend on conditions the API response does not establish. The UI does not assume eligibility or sum them into a guaranteed charge. Listed prices may apply per unit or by weight.
- No exact stock quantities, taxes, fulfillment fees, final charge, card eligibility, or real rewards are inferred. Catalog failures produce errors; they never fall back to demo results under a real-data label.
- This integration provides explicit product-to-ingredient matching, manually reviewed package counts, a freshly priced live basket and directions. If several ingredients share a product, package counts are added; users should review duplicates. Automatic package conversions, organic/dietary verification, authenticated retailer cart handoff and retailer checkout remain subsequent work. The existing AI recommendation, multi-store basket comparison and Stripe test payment still use the clearly labeled sample catalog. Live basket review is a separate route with no test-pay button, so it cannot silently change an approved demo checkout or pretend its item subtotal includes merchant fees.
- OAuth client credentials, token cache, expiry/401 refresh and timeouts run only on the backend. Raw upstream failures and tokens are not returned to Android. ZIP/product searches are sent to Kroger and are not persisted by this feature.

## Checks

`npm test` in `backend/` covers request validation, provider errors, price/inventory nullability, location scoping, OAuth reuse/refresh, fresh basket pricing and all existing agent/payment tests. `RetailerFlowTest` exercises native store/product selection, missing prices, setup errors, ingredient completeness, pack limits, quote invalidation and stale-result protection with injected provider fixtures. Its opt-in `runLiveRetailer=true` test makes real read-only store/product/basket requests through the native UI; it never orders or pays.

An actual configured search is still required before claiming live prices work. These credentials do not authorize grocery ordering or delivery.

Implementation checks on October 17, 2026: 63 backend tests, seven Kotlin JVM tests, and nine native UI/routing checks passed. The missing-credentials error was verified before setup. After local credentials were supplied, a live ZIP 98105 lookup returned five stores and a pasta search at University Village QFC returned ten products. The same store/product search was completed through the Android emulator UI, which displayed Kroger Penne Rigate, 16 oz, regular price $1.33, stock HIGH and sold by UNIT. This is a point-in-time catalog observation, not a guaranteed checkout price or a completed order. Local credentials remain ignored by Git.

Live basket verification, October 17, 2026: 69 backend tests, seven Kotlin JVM tests and 12 offline native UI/routing checks passed. The opt-in native live test also passed ZIP/store search → ingredient/product matching → two packages → freshly priced basket review. A separate live backend check priced two 16 oz packages of Kroger Penne Rigate at University Village QFC at $2.66, with tax, fees, final charge and rewards explicitly null. No grocery order or payment occurred. The native tests also verified that quantity edits discard the quote, unmatched ingredients/combined package limits block review, and late responses cannot restore an old recipe's basket.

References: [Kroger developer reference](https://developer.kroger.com/reference/), [Kroger's public API workspace](https://www.postman.com/kroger/the-kroger-co-s-public-workspace/documentation/ki6utqb/kroger-public-apis).
