# Recipe Wallet — AI shopping agent for Android

Kotlin + Jetpack Compose, API 26+. A bounded Gemini agent turns a dinner request, upfront budget and pantry ingredients into a basket for human review. Three fictional stores, three demo recipes and three illustrative Visa cards. Built for DubHacks 2026, Visa: Reimagine Shopping.

## Open and run

1. Open this folder in Android Studio with JDK 17 and Android SDK 36. Allow Gradle sync and select an Android emulator (API 26+).
2. Install Node.js 22+. In `backend/`, copy `.env.example` to `.env` and set your own `GEMINI_API_KEY`, `STRIPE_SECRET_KEY` (`sk_test_…`) and `STRIPE_PUBLISHABLE_KEY` (`pk_test_…`). Both Stripe keys must come from the same test environment. Never commit `.env`.
3. Run `npm start` in `backend/`. The default local server binds to `127.0.0.1:4242`; the Android emulator connects through `http://10.0.2.2:4242`.
4. Run the app. Enter: “Find me a vegetarian noodle dinner under $12. I already have garlic.” Review the interpreted constraints, purchased ingredients, owned ingredients, store, card and charge. Revise: “I already have soy sauce too.”
5. Tap **Approve basket and continue**, review the sample card assumptions and use Stripe PaymentSheet with test card `4242 4242 4242 4242`, a future expiry and a valid test CVC. No actual rewards, delivery or grocery order results.
6. For a USB-connected Android device, run `adb reverse tcp:4242 tcp:4242` and build with `sh gradlew :app:assembleDebug -PBACKEND_URL=http://127.0.0.1:4242`.

Gemini defaults to `gemini-3.5-flash-lite` through the Interactions API. Override `GEMINI_MODEL` if needed. An initial check with `gemini-3.8-flash` worked but encountered intermittent project quota limits; the lighter model completed the live three-turn demo in about 2–3 seconds per request. Availability and latency depend on the account and service. The older `gemini-2.5-flash` model was rejected for this account.

Without working API keys, AI and payment return setup errors. Manual recipe selection and the explicitly labeled confirmation preview remain available. A preview never claims a payment occurred.

## What makes this an agent

The server runs a bounded model/tool loop, rather than asking the model to invent a basket:

- `find_recipes`: establishes the interpreted diet, preparation time, upfront budget and owned ingredients, and searches the catalog.
- `compare_baskets`: calculates store/card combinations with trusted catalog prices and reward rules.
- `prepare_basket`: selects a previously calculated option for human review. This tool cannot order or pay.

The model receives the results of its tool calls before choosing the next action. A prepared basket terminates the planning loop. The app shows summaries of actual successful tool executions, not hidden reasoning. It rechecks the returned amount and rewards against its fixture before enabling approval.

Follow-ups retain the latest constraints and up to four recent requests in a local in-memory session, expiring after 20 minutes. Starting a new shop clears the app's session reference. Process death loses the session reference. The API uses `store: false`; this opts out of storing Interaction objects, not Google's broader service data policies. Requests are processed under the provider's terms. Card numbers and payment credentials are never sent to Gemini.

## Prices, budgets and responsibility

Money uses integer cents. Store multipliers use basis points. Grocery totals and rewards round to the nearest cent. Each store is paired with its best sample card and ranked by net cost, with deterministic ties. The agent chooses the best net-cost store/card within the upfront budget; when none fits it presents the cheapest charge and disables approval. A zero-dollar budget is distinct from an unspecified budget.

**The Stripe charge equals groceries + delivery. Estimated future rewards do not reduce today's charge.** Delivery earns no rewards. Points use points per dollar multiplied by assumed redemption value. Card bonuses, eligibility, activation and caps are illustrative assumptions. Selecting a sample card advises the user; PaymentSheet collects test payment details separately.

Ingredient prices estimate portions used, not full retail packages. Tax is excluded. No real inventory, grocery integration, nutrition, portion scaling, or allergy-safety verification is provided. Pantry ownership comes from the interpreted request and is visible for correction. Pasta's Parmesan rennet is unspecified, so the agent excludes that recipe for vegetarian or vegan requests. The small catalog may not satisfy a request.

Tool names, IDs, pantry items, budgets and sequencing are validated. The model has no payment tool. Payment starts only after the user's review and explicit tap. The server recalculates the charge, rejects non-test keys, uses idempotency keys, and verifies Stripe's test status before showing success. Stripe API version is pinned to `2025-06-30.basil` for compatibility with the Android SDK's card-only PaymentSheet integration.

This backend is for a local hackathon demo: no user authentication, persistent order database or production fulfillment. Debug HTTP is limited to emulator/loopback hosts. Configure HTTPS before hosting a release build. After process death, inspect Stripe's test dashboard before repeating a pending payment.

## Validation

- `cd backend && npm test`: 17 backend tests cover pricing, tool execution, pantry exclusion, budget enforcement, invalid IDs, provider failures, history forwarding and fixture parity.
- `sh gradlew :app:testDebugUnitTest :app:assembleDebug`: five Kotlin pricing tests and a debug APK build.
- `sh gradlew :app:connectedDebugAndroidTest`: deterministic review/approval and over-budget UI tests; the live API test is skipped unless explicitly enabled.
- Optional live device test, with the backend and valid test keys running: `sh gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.liveAgent=true`.
- `curl http://127.0.0.1:4242/health`: reports configuration presence only, not credential validity.

Live backend checks verified a Gemini three-tool plan, pantry revision and impossible budget. A Stripe test PaymentIntent was created idempotently, confirmed with Stripe's test Visa payment method, and verified by the server at $7.79. On the API 35 emulator, deterministic review/approval and over-budget UI tests passed. A live app test verified Gemini planning, a pantry revision and opening PaymentSheet. A separate native UI test verified PaymentSheet cancellation, entered Stripe test card details, completed payment and reached the server-verified receipt. See DEMO.md for the judging sequence and remaining limitations.

## Judging

The experience addresses discovery, decision-making, personalization, checkout and illustrative rewards. Explain which work is generative AI and which calculations are deterministic. Show the human approval step, visible assumptions, a successful revision, and an impossible budget. Do not claim measured conversion improvements or real grocery savings.

The mentor-provided rubric lists innovation, technical execution, UX, commerce impact and trust/responsibility. Its table gives UX 20% while the prose gives 25%; confirm the official weighting and any mandatory Visa API requirement with organizers.

References: [Gemini function calling](https://ai.google.dev/gemini-api/docs/function-calling), [Interactions API](https://ai.google.dev/gemini-api/docs/interactions-overview), [Stripe Android](https://github.com/stripe/stripe-android), [Stripe test cards](https://docs.stripe.com/testing), [Visa brief](https://dh26.dubhacks.co/#tracks).
