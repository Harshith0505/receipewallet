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
5. On the home screen tap **Check retailer prices**, enter a five-digit US ZIP code, and tap **Find nearby stores**. Select a store, then search for a product. The ingredient screen also has **Look up retailer prices**, with quick searches for the ingredients still being purchased.

## Data contract and limitations

- `POST /retailers/stores` accepts `{ "postalCode": "98105" }`. The lookup requests up to five Kroger-family locations within 10 miles. Store names and addresses come from the API. No ratings or exact user distances are fabricated.
- `POST /retailers/products` accepts `{ "storeId": "PROVIDER_LOCATION_ID", "query": "pasta" }`. Every product lookup supplies `filter.locationId` to request the selected store's price and availability. Up to ten product results are requested; item variants are preserved.
- Responses include `provider: "kroger"`, `demo: false` and `fetchedAt`. Regular and promotional prices are separate integer-cent fields. Missing prices, package sizes, fulfillment flags, inventory, or price basis remain null. A missing/zero price is not a free product. Empty results do not establish that a product is out of stock.
- Promotional prices can depend on conditions the API response does not establish. The UI does not assume eligibility or sum them into a guaranteed charge. Listed prices may apply per unit or by weight.
- No exact stock quantities, taxes, fulfillment fees, final charge, card eligibility, or real rewards are inferred. Catalog failures produce errors; they never fall back to demo results under a real-data label.
- This first integration provides lookup and directions. The existing recipe recommendation, basket comparison, and Stripe test payment still use the clearly labeled sample catalog. Live product-to-ingredient matching, validated package conversions, authenticated retailer cart handoff, and retailer checkout remain subsequent work. A live lookup does not silently change an approved basket.
- OAuth client credentials, token cache, expiry/401 refresh and timeouts run only on the backend. Raw upstream failures and tokens are not returned to Android. ZIP/product searches are sent to Kroger and are not persisted by this feature.

## Checks

`npm test` in `backend/` covers request validation, provider errors, price/inventory nullability, location scoping, OAuth reuse/refresh, and all existing agent/payment tests. `RetailerFlowTest` exercises native store/product selection, missing prices, setup errors and return navigation with injected provider fixtures; fixtures do not establish live API access.

An actual configured search is still required before claiming live prices work. These credentials do not authorize grocery ordering or delivery.

Implementation checks on October 17, 2026: 63 backend tests, seven Kotlin JVM tests, and nine native UI/routing checks passed. The missing-credentials error was verified before setup. After local credentials were supplied, a live ZIP 98105 lookup returned five stores and a pasta search at University Village QFC returned ten products. The same store/product search was completed through the Android emulator UI, which displayed Kroger Penne Rigate, 16 oz, regular price $1.33, stock HIGH and sold by UNIT. This is a point-in-time catalog observation, not a guaranteed checkout price or a completed order. Local credentials remain ignored by Git.

References: [Kroger developer reference](https://developer.kroger.com/reference/), [Kroger's public API workspace](https://www.postman.com/kroger/the-kroger-co-s-public-workspace/documentation/ki6utqb/kroger-public-apis).
