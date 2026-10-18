# Pantry page — first step

Branch: `feat/perceptron-pantry`, created from the fetched `origin/main` at `3e10ec3`.

Open **My pantry** from the meal-planner home page. The screen reuses the existing page layout, green/cream colors, typography, cards, button styles and back navigation.

Implemented:

- Camera and photo-library controls, with a local image preview, retake and remove actions.
- Manual ingredient entry, optional free-text amount, editing and removal.
- Local ingredient persistence with validation and visible save/read errors.
- Empty and populated pantry states, image loading and unsupported-photo handling.
- Bounded gallery previews with image orientation correction. Photos stay in the app’s private cache and are not uploaded.

Perceptron recognition, Supabase pantry storage and automatic recipe exclusion are the next steps. The screen labels its current photo-preview and local-storage behavior explicitly. No API key is needed for this stage.

The camera currently uses Android’s thumbnail preview contract. Before integrating recognition, upgrade to full-resolution camera capture and add a review/confirmation step for detected ingredients and quantities. Gallery previews are downsampled for display.

`PantryScreenTest` exercises navigation, manual save/edit/remove and persistence while cleaning up only its own test ingredient.

## Separate tablet preview

To build a preview that can coexist with the existing Recipe Wallet app:

```sh
sh gradlew :app:assembleDebug :app:assembleDebugAndroidTest -I tools/pantry-preview.init.gradle
```

The optional init script changes only this build’s application ID and debug label to **Recipe Wallet Pantry**. A regular build retains the normal app identity. The script does not change the normal Gradle build configuration.
