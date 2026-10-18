# Meal studio · frontend UX

Start from `frontend-ux` (based on main `3e10ec3`). No changes are pushed to main.

## Judge path

1. Open **Build with AI**. Explore the mood deck with a swipe or Previous/Next buttons. Set people, time, comfort zone, budget and vegetarian preference. Tap **Use my taste profile**, review the draft, then **Build my meal**.
2. Review **Your meal**. Separate numbered panels show the shopping list, already-owned items, and ingredient remix actions. **What changed** is expanded when revisions exist. Mild/spicy actions revise supported chili inclusion through the agent; arbitrary ingredient substitutions are not supported.
3. Open **My pantry**. Take or choose a fridge photo. Perceptron returns ingredient names and amounts for review, editing and selected-only saving. Foods outside the recipe catalog are retained; supported identities also map to catalog ingredient IDs. Review the photo suggestions, exclude mistakes and confirm items. Pending detections never affect a basket. Confirmed pantry items are saved on this device. Recipe suggestions rank by pantry coverage.
4. Open **My week**. Generate five dinners from preferences, pantry and recent choices. AI results are constrained to the catalog; without Gemini the backend returns a clearly labeled local plan. Swap a day and see the combined quantity list and whole-package pickup estimate update. Weekly quantities are not deducted from unspecified pantry stock. Shop each meal separately; combined weekly checkout is not implemented.
5. Choose a recipe manually or approve the AI basket. Compare shops on the interactive map. Tap price markers or store cards. Price tickets distinguish payable charge, fees and future rewards. Green/amber/red status is also expressed in text. Open walking directions, review checkout, and use the no-payment receipt preview or Stripe test payment.

## Behavior and persistence

- Tablet home uses two columns from 840 dp; phone uses one scrollable column. Large controls, section headings and button alternatives accompany the swipe interaction.
- On-device preferences: mood, people, time, novelty, budget and diet. Actual recipe selections/recommendations form a capped 20-item history; there is no invented history. **Clear meal history** appears after choices exist.
- Manual recipe ordering uses mood, pantry, time, vegetarian eligibility and familiar/new preference. Seven fixture recipes exist, so variety is deliberately limited.
- Pantry is presence only. Users must verify they own enough. Unchecked basket ingredients are meal-only exclusions, not automatically added to saved pantry inventory.
- The most recent photo is stored in app-private storage and can be removed. Only an explicit camera/gallery action sends its resized JPEG to the backend/Perceptron. Provider output is allowlisted, not trusted for quantities or freshness.
- Optional **Auto-plan on my first visit each week** sends preferences, recent choices and pantry IDs when the user first opens the app in a new Monday-based week. It does not run while the app is closed, schedule notifications, or purchase anything.
- Maps use Leaflet 1.9.4 and OpenStreetMap tiles over HTTPS with attribution. Pins are approximate Seattle store coordinates. Map tiles require internet. Store selection, costs and checkout remain usable without tiles; this is not live user geolocation.
- Existing package pricing, budget approval checks, server recalculation, Stripe test mode and server-verified receipts remain in use. No API keys ship in the Android app.

## Backend

Run Node 22+ in `backend` with `npm start`. Set `GEMINI_API_KEY` in `backend/.env` for AI planning; use backend-only `PERCEPTRON_API_KEY` for pantry recognition. `GEMINI_MODEL` configures the existing Interactions meal agent. `GEMINI_VISION_MODEL` configures the JSON GenerateContent calls (default: the configured `GEMINI_MODEL`, then `gemini-3.5-flash-lite`). Keep Stripe **test** keys server-side for test payments.

- `POST /pantry-scan`: `{mimeType:"image/jpeg",image:"base64"}`. JPEG only, bounded request size. Existing `imageBase64` scanner requests remain compatible. Returns full `ingredients`, mapped `ingredientIds`, `provider:"perceptron"` and `requiresConfirmation:true`. No image logging or server persistence.
- `POST /weekly-plan`: `{preferences,history,pantryIds,vegetarian,maxMinutes}`. Returns five validated catalog `recipeIds` and `source:"gemini"|"local"`.
- Agent tools now support requested servings 1–12 and explicit `omitIds`. Omissions stay separate from ownership, appear in revisions and get repriced by code. Existing two-serving requests remain compatible.

For a USB tablet: enable USB debugging and run `adb reverse tcp:4242 tcp:4242` after starting the backend. See TABLET_SETUP.md. Manual pantry, local weekly planning and shopping preview do not need backend credentials.

## Reference documentation

- Gemini GenerateContent / inline images: https://ai.google.dev/api/generate-content
- Leaflet map API: https://leafletjs.com/reference.html
- OpenStreetMap tile usage: https://operations.osmfoundation.org/policies/tiles/

## Verification

Backend unit tests cover image validation, provider mocking, allowlisted output, weekly diet eligibility, invalid AI plans, servings and chili omission without false pantry ownership. Existing payment and package parity tests remain in place. Android MealStudioTest covers persisted profile/pantry, weekly quantity aggregation and swaps, and manual shop-to-receipt navigation. Live Perceptron recognition/Gemini planning and Stripe payment require configured keys; mocked-provider tests do not prove live provider behavior.

Verified for this change: 61 backend tests; 7 Android JVM tests (including the shared 288 package-pricing scenarios); debug APK and instrumentation APK builds; and StudioDeviceSmokeTest on an emulator at 1600 × 1000 / 160 dpi, through pantry → local week → manual meal → store comparison → checkout → unpaid receipt preview. The API 37 emulator cannot run the existing Compose/Espresso test harness (`InputManager.getInstance` was removed); those Compose tests compile but were not validated on a supported device in this run. Live Gemini and Stripe requests were not run.

## Integrated backend behavior

The combined branch retains Supabase account/library callbacks, reviewed Kroger product prices and cart transfer, generated recipes beyond the sample catalog, pantry purchase-memory hints, and sample student discounts. The new home includes Login/Account and retailer-price entry points. Pantry recognition uses the existing Perceptron key, and both pantry UIs share the scan endpoint. The detailed editor and studio share pantry storage and synchronize confirmed supported ingredients; freeform confirmed pantry names also reach the meal planner. Weekly planning uses the configured Gemini model and filters ingredient exclusions before model selection. Generated recipes stay unpriced until products are selected. Stripe test checkout remains separate from actual retailer checkout.
