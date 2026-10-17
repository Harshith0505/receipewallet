# Recipe Wallet — native Android MVP

Kotlin + Jetpack Compose. Minimum Android API 26. Built for DubHacks 2026 — Visa: Reimagine Shopping. Three recipes, three fictional stores, three illustrative Visa cards. No maps, routing, account signup, or grocery fulfillment.

## Open and run

1. Install Android Studio with Android SDK 36 and JDK 17. Open this folder as a project and allow Gradle sync.
2. Start an Android emulator (API 26+), then run the app configuration. Recipe selection, customization, store comparisons, card comparisons, and confirmation previews work without a backend.
3. For Stripe PaymentSheet, install Node.js 22+, open `backend/`, copy `.env.example` to `.env`, and set your own Stripe test secret and publishable keys from the same account. Do not put the secret in the app.
4. For the generative-AI recipe matcher, also set `GEMINI_API_KEY` in `backend/.env` (optional `GEMINI_MODEL`, default `gemini-2.5-flash`). Your meal preference is sent to Gemini, which returns a known recipe ID and a short explanation. Without a key, the app clearly reports that AI is unavailable; manual selection works. Run `npm start` from `backend/`. It binds to 127.0.0.1:4242. The Android emulator reaches it at `http://10.0.2.2:4242` (the app default).
5. On a USB-connected physical Android device, use `adb reverse tcp:4242 tcp:4242` and build with `sh gradlew :app:assembleDebug -PBACKEND_URL=http://127.0.0.1:4242`. Debug builds allow HTTP only for emulator/loopback hosts. Release builds use HTTPS; configure a hosted backend URL with the same Gradle property.
6. Choose a recipe → remove chili flakes → compare stores → continue → switch sample Visa cards → tap Stripe test pay. In PaymentSheet use Stripe's test Visa number `4242 4242 4242 4242`, a future expiration date and any valid CVC. Do not enter real card information.

Without test keys, payment reports a setup error and the separate **Preview confirmation without payment** action demonstrates the final screen. It does not claim payment success.

## Calculations

Money uses integer cents. Store multipliers use basis points. Store grocery totals and reward estimates round to the nearest cent. Delivery is added to the charge and earns no rewards. Cashback cards use a percentage; points cards use points per $1 multiplied by assumed dollars per point. Each store is paired with its best sample card and ranked by net cost only; rating and distance do not affect ranking. Ties are deterministic.

The actual test charge equals groceries + delivery. Rewards are hypothetical future value and do not reduce the Stripe payment. Prices estimate portions used, not full retail packages; tax is excluded. Stores, addresses, card programs and eligibility assumptions are fictional. No actual rewards or grocery orders result.

Sample card choice advises which card to use; PaymentSheet collects test payment details independently. The selected sample card is not automatically loaded into Stripe. It is recorded only as advisory metadata.

The server recalculates amounts from trusted fixture data, validates ingredient IDs, rejects non-test keys, creates card-only PaymentIntents with idempotency keys, and checks Stripe's payment status before the app shows a verified test-payment receipt. No card numbers or secret keys are stored in Android. PaymentSheet handles payment details.

This is a local hackathon payment backend, not a production order service. It has no user authentication or persistent order history. ViewModel state survives rotation, but not process death; after process death, check Stripe's test dashboard before repeating a payment. No webhook fulfillment is needed because this app places no real orders.

## Validation

The debug APK compiled successfully with JDK 17, SDK 36, AGP 8.13.2, Kotlin 2.3.10, and Compose BOM 2025.08.00. Five Kotlin unit tests and nine Node.js tests pass. On an Android 15 / API 35 Pixel 7 emulator, the app was installed and the recipe → ingredient removal → store comparison → card switch → confirmation preview → restart flow was verified, along with missing-key errors for Stripe and Gemini. No Android runtime crash was logged. Stripe and Gemini live calls have not been exercised because account keys are not configured.


- Backend: `cd backend && npm test`
- Android: `sh gradlew :app:testDebugUnitTest :app:assembleDebug`
- Backend health: `curl http://127.0.0.1:4242/health`

Fixtures live in `app/src/main/assets/demo.json` and `backend/demo.json`. A test verifies they remain identical.

## Judging checklist

- Run the full flow on an emulator or physical device.
- Configure your own test keys and complete a Stripe test transaction.
- Keep the simulation labels visible. No earned rewards or real orders are claimed.
- Verify the actual event's Visa track requirements. The public Visa brief explicitly asks for generative AI that improves shopping. The app includes AI discovery and explainable card comparison. A real Gemini response and Stripe test transaction still require your keys. The public Devpost Hacker Guide link returns 404, so the full rubric/API-specific requirements remain unverified.

References: [Stripe Android SDK](https://github.com/stripe/stripe-android), [PaymentIntent API](https://docs.stripe.com/api/payment_intents), [Stripe test cards](https://docs.stripe.com/testing), [Compose BOM](https://developer.android.com/develop/ui/compose/bom).

## DubHacks 2026 submission notes

Checked against the [official event site](https://dh26.dubhacks.co/#tracks) and [Devpost](https://dubhacks-2026.devpost.com/) on October 17, 2026.

The Visa track is **Reimagine Shopping**, with a $5,000 prize listed. The official brief calls for generative AI to improve a stage of commerce, including discovery, decisions, personalization, checkout, payments, or rewards. This app uses Gemini for personalized recipe discovery and ordinary calculations for prices and rewards.

Devpost requires a draft before **Saturday October 17 at 10:30 PM Pacific**, with teammates/Discords and selected tracks, and a final submission before **Sunday October 18 at 10:00 AM Pacific**. The event site shows 10:30 AM for submissions, so use the earlier Devpost deadline until organizers clarify. Devpost says to select exactly one title track. Confirm Visa's classification and all detailed requirements in the participant Hacker Guide; the public link is broken.

No project has been submitted on your behalf.
