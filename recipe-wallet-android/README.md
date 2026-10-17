# Recipe Wallet — Android grocery planner

Kotlin + Jetpack Compose, API 26+, JDK 17 and Android SDK 36. Open this directory
in Android Studio and sync Gradle. Choose a recipe manually to explore the grocery
planner immediately, without backend credentials.

- Whole-package prices for every recipe ingredient, with pantry and serving controls.
- Per-store stock, organic product preferences, pickup/delivery fees and clear totals.
- Google Maps walking directions to verified Seattle locations.
- A bounded Gemini agent that compares package-priced baskets for approval.
- Server-recalculated Stripe test checkout and a no-payment receipt preview.

See [Grocery planner](GROCERY_PLANNER.md) for functionality and contracts, and
[Tablet setup](TABLET_SETUP.md) for USB debugging, Android Studio and backend setup.

## Backend

With Node.js 22+, run `npm start` from `backend/`. For AI and Stripe checkout,
copy `.env.example` to `.env` and supply private Gemini and Stripe test credentials.
Manual grocery browsing works offline; AI and verified test payment require the
backend. Run `adb reverse tcp:4242 tcp:4242` for a connected tablet or emulator.
The debug app defaults to http://127.0.0.1:4242.

## Checks

```bash
cd backend
npm test
```

From this directory run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
The Android and Node calculators share 288 scenario expectations. No live retailer
API, real delivery or real rewards are integrated. Prices, stock, attributes and
fees are demo data. Real store addresses support directions only; configured tax
is 0% for this demo. Card recommendations remain illustrative.
