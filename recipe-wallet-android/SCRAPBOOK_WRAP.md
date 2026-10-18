# Scrapbook & food-story wrap-up

The Android app uses warm fibrous paper, gently torn edges, rose gingham tape and real bundled food photographs (see PHOTO_CREDITS.md). Mood cards support left-to-pass and right-to-choose, with a persistent chosen mood. Recipe discovery uses a draggable floating recipe garden with persistent hearts and a pinned filter. The catalog currently contains three recipes; it does not invent extra options.

Custom ingredient exclusions apply to catalog discovery, weekly-plan validation, AI-plan acceptance, and checkout eligibility, including ingredients marked as already owned. This is ingredient-name screening with a small alias list, not a product safety guarantee. Halal/kosher preferences enter agent requests and display certification-review reminders. No products are represented as certified. Store comparison cards use store-inspired colors and names with simulated pricing.

## My wrap-up

A food journal is separate from purchases, pantry scans, recipe history, and planned dinners. Nothing is counted as eaten until the person explicitly logs a supported recipe, day, personal servings (0.25–12), and ingredients actually consumed. Dates in the future cannot be saved through the app. Existing pantry ingredients remain part of the food's nutrition even when they are excluded from a shopping basket. Users can edit and remove logs, and select this or last week.

Five swipeable story pages show coverage, estimated macros (protein, carbohydrate, total sugar, fat, fiber and energy), recurring meals, ingredient variety and a gentle next-step story. Previous/Next controls and page-dot indicators accompany swiping. No daily targets, moral food scores, deficiency claims or diagnoses are inferred. Missing meals, snacks and drinks remain unknown. A clearly labeled sample story is available without keys and never persists into the journal.

## Nutrition precision

`app/src/main/assets/nutrition.json` and `backend/nutrition.json` are identical illustrative rounded ingredient profiles. They are **demo estimates, not certified nutrition facts or exact USDA food records**. Each profile states its basis quantity/unit and assumptions (dry grain versus cooked, canned chickpeas drained proxy, typical edible item weights, and liquid density approximations).

For each logged ingredient, code multiplies the recipe quantity by personal servings / recipe base servings / profile basis quantity. It sums raw values first and rounds to one decimal only after aggregation. Sugar means **total sugar**, including naturally occurring sugars; added sugar is not separately tracked. Brand, preparation, custom ingredient quantities and omitted/unlogged food change real intake. Supported saved/custom recipes outside the three-recipe catalog are not estimated yet.

Official methodology references (not claims of exact nutrient-data provenance):

- USDA FoodData Central foundation-food documentation: https://fdc.nal.usda.gov/Foundation_Foods_Documentation/
- USDA FoodData Central data descriptions: https://fdc.nal.usda.gov/data-documentation.html

## Agentic feature

`POST /weekly-wrap` accepts `{weekStart, entries:[{id,recipeId,date,servings,ingredientIds}], preferences, diet:"any"|"vegetarian"}`. It validates Monday-based dates, known recipes/ingredients, bounded portion amounts, duplicate identifiers and the log limit. It computes all nutrition in trusted code.

When `GEMINI_API_KEY` is configured, a bounded Interactions loop uses `inspect_log` → `compare_recipe_options` → `compose_wrapup`. Gemini chooses a story order, comparison goal, and returned next-recipe ID. It cannot supply arbitrary intake numbers, free-form nutritional judgments, new recipes, purchases, or changes to the journal. Numeric story text is generated from server data. The visible trace shows actual tool actions; the recipe suggestion opens a reviewable basket only after a user tap. Provider failures or missing keys produce an explicitly labeled local report. No AI is claimed in that fallback.

Meal logs and preferences are sent only when the user taps the food-agent button. The app checks server totals against its local calculations before showing a response. Recipe actions from completed or in-flight results are hidden if the current preferences differ from the request; a fresh request applies the new preferences. Configure the existing backend's `GEMINI_MODEL` for the Interactions API. No keys belong in the Android app.

## Preview

Virtual device: **Recipe Wallet Scrapbook Tablet API 36** (`Recipe_Wallet_Tablet_API_36`), Android 16, ARM64, 1600×1000 at 160 dpi. It is registered in the standard Android Studio Device Manager directory, with a separate data partition from the existing phone AVD. The official Google system image was downloaded from the SDK index and SHA-1 verified. No existing virtual device was overwritten.

The local Android Studio project uses JDK 17 via ignored `.gradle/config.properties` and the installed SDK via ignored `local.properties`. These machine paths are not committed. Run configuration is the `app` Android module.

Demo path: Build manually → heart a recipe → filter My pins → My wrap-up → Preview a sample food story → swipe through macros → return to own journal → log a meal and personal serving amount → Ask my food agent. Offline stories are available immediately; live Gemini needs the backend key. Stripe remains test-only.
