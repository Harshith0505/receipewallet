# Recipe Wallet — Android AI grocery planner

Kotlin + Jetpack Compose, API 26+, JDK 17 and Android SDK 36. This version combines the Android home and saved recipe library, whole-package grocery planner, and basket-bound Stripe test checkout.

## Run

1. Open this directory in Android Studio and sync Gradle. Manual recipe selection, package pricing, store comparison and unpaid receipt preview work without a backend.
2. Install Node.js 22+. Copy `backend/.env.example` to `backend/.env` and set your Gemini key and matching Stripe test secret/publishable keys. Never commit `.env`. Run `npm start` in `backend/`.
3. Run `adb reverse tcp:4242 tcp:4242` for your connected emulator or tablet. The debug app defaults to `http://127.0.0.1:4242`; reverse forwards this to the computer's backend. Repeat after reconnection. Alternatively build an emulator APK with `-PBACKEND_URL=http://10.0.2.2:4242`.
4. Try “Find me a vegetarian noodle dinner under $30. I already have garlic.” Review the AI plan, then tap **Review package-priced basket**. Adjust servings, pantry items, organic-only or pickup/delivery before choosing a complete store.
5. Review the packages and **total charged today**, then tap **Approve & pay**. Use Stripe test card `4242 4242 4242 4242`, a future expiry, a three-digit test CVC and a test ZIP code. The server verifies the payment before showing a paid test receipt.

See [Grocery planner](GROCERY_PLANNER.md) and [Tablet setup](TABLET_SETUP.md) for details. Saved personal recipes stay on the device; priced shopping currently uses the three demo catalog recipes.

## Planning and pricing

Gemini defaults to `gemini-3.5-flash-lite` through the Interactions API. `find_recipes`, `compare_baskets` and `prepare_basket` ground its choices in catalog data; the model cannot order or pay. Follow-ups retain the latest constraints and four recent requests in local sessions that expire after 20 minutes. The app shows actual tool summaries and validates returned totals. Requests use `store: false`, which disables stored Interaction objects, rather than changing the provider's broader data policies. Payment credentials are never sent to Gemini.

AI initially compares two-serving pickup baskets. The store screen supports 1–12 servings, pantry exclusions, organic-only offers and pickup/delivery. The app purchases enough whole packages, handles store-specific stock and fees, and excludes incomplete baskets from checkout. Android and Node share a 288-scenario calculation matrix. Money uses integer cents; configured demo tax is 0%. Rewards exclude fulfillment fees and tax.

Store recommendations rank complete baskets by estimated cost after rewards, with deterministic ties. Today's charge is packages + fulfillment fees + configured tax; future rewards never reduce it. Agent budgets apply to today's charge and are checked again after shopping settings change. Card profiles remain illustrative; reward caps and owned-card selection are deferred.

Store addresses support Google Maps directions. Prices, availability, ratings, product attributes, fees and fulfillment options are fictional demo data, not retailer quotes. The three-recipe catalog has no verified nutrition or allergy safety. Parmesan rennet is unspecified, so the AI excludes pasta for vegetarian/vegan requests.

## Checkout and receipts

The backend requires `pricingVersion: "packages-v1"`, recalculates the basket, rejects incomplete orders and non-test keys, and creates card-only PaymentIntents with idempotency keys. Verification binds the intent to the recipe, ingredient IDs, store, advisory card, servings, fulfillment, organic preference, package selections and payment attempt. It checks test mode, USD, charge and amount received. The receipt snapshots the purchased packages and Stripe test reference.

Cancellation retains the basket for retry. An uncertain payment result blocks editing and new checkout until status verification resolves it. The unpaid preview explicitly states that no payment occurred. Sample card selection is advisory; Stripe collects test card details separately. No actual grocery order, delivery or rewards are created.

Update app and backend together. `/verify` requires the original payment request fields plus `paymentIntentId`. Old portion-priced APKs cannot use this checkout. The local demo has no authentication, persistent order database or production fulfillment. After process death, inspect Stripe's test dashboard before repeating a pending payment. Debug HTTP is limited to emulator/loopback hosts; use HTTPS for hosted release builds.

## Checks

- `cd backend && npm test`: pricing, grocery stock/quantity validation, API behavior, agent tools, fixture parity and payment identity checks.
- `sh gradlew :app:testDebugUnitTest :app:assembleDebug`: Kotlin pricing, package/server parity and debug build.
- `sh gradlew :app:connectedDebugAndroidTest`: home, saved recipes, package shopping, review, budget and unpaid receipt tests. Live AI/payment tests are skipped by default.
- With configured test keys and backend: enable live tests using `-Pandroid.testInstrumentationRunnerArguments.liveAgent=true`.
- `curl http://127.0.0.1:4242/health` reports configuration presence, not credential validity.

Merge validation on October 17, 2026: 42 backend tests, seven Kotlin tests and nine deterministic emulator tests passed. The optional live AI UI test was skipped; separate live backend checks verified Gemini planning and pantry revision ($15.80 → $12.15). The native Stripe UI test passed cancellation, verification/retry, locked shopping settings during an uncertain result, and a successful $15.80 package payment with the matching receipt.

## Judging

The team reports mentor confirmation that a Visa API is not required for this hackathon; Stripe remains the test checkout. Clarify the rubric's conflicting UX weights (20% in the table, 25% in prose) with organizers. Demonstrate grounded AI decisions, visible assumptions, human approval and verified test payment. Do not claim measured conversion improvements, real grocery savings or actual rewards.

References: [Gemini function calling](https://ai.google.dev/gemini-api/docs/function-calling), [Interactions API](https://ai.google.dev/gemini-api/docs/interactions-overview), [Stripe Android](https://github.com/stripe/stripe-android), [Stripe test cards](https://docs.stripe.com/testing), [Visa brief](https://dh26.dubhacks.co/#tracks).


## Saved recipes and the shopping flow

The native screens share the same typography, restrained green palette, borders, and navigation. The top-right overflow menu opens a scrollable About panel.

Save a personal recipe locally, open it from **Saved recipes**, and tap **Use this recipe**. The ingredient screen suggests matches from a separate 26-product demo grocery catalog. Review each product and its package size, choose pack counts, and uncheck anything not being purchased. Every included ingredient must be matched before comparing shops. Matching uses literal aliases, not dietary or quantity inference; quantities must be reviewed manually.

Personal baskets continue through shop comparison, advisory Visa selection, and Stripe test checkout or a clearly labeled receipt preview. Unlike the original three meal templates' portion estimates, the personal-recipe catalog uses sample full-package prices. Both exclude tax and real grocery fulfillment. All prices and card rules remain fictional.

Android and the backend share identical `products.json` fixtures. Custom checkout sends product IDs and integer pack counts in `customBasket.items`; the backend derives prices from its own catalog and rejects unknown products, duplicated IDs, and counts outside 1–20 per product. It does not accept a client-supplied payment amount. Existing backend key setup is still required for live AI and Stripe test payments.

`SavedRecipeFlowTest` verifies saved recipe selection through receipt preview, plus blocking unmatched ingredients. Backend tests cover custom basket amounts, quantity limits, and fixture parity. Tests create and remove only their own temporary recipes.
