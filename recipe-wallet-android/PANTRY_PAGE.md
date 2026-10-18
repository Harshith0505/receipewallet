# Pantry photo scanning

Branch: `feat/perceptron-pantry`, started from `origin/main` at `3e10ec3`.

Open **My pantry** from the home page. The page uses the shared green/cream theme, typography, cards and buttons.

## What works

- Take a full-resolution camera photo or choose an existing image.
- Orientation-corrected, bounded JPEG preview; originals are not sent.
- Tap **Scan ingredients** to send the prepared image to Perceptron Mk1.5 through the backend.
- Review detected names and suggested amounts, edit mistakes, and uncheck unwanted items.
- Save only the selected ingredients. Scans do not save automatically.
- Repeat scans skip matching names without overwriting confirmed amounts.
- Add, edit and remove pantry items manually. Items persist on this device.
- Loading, empty detection, connection, configuration, provider and save errors.
- Scan/photo/review state survives activity recreation through the ViewModel. Unconfirmed scans are not persisted across app process death.

Amounts are suggested only from visible counts or readable labels, and must be reviewed. Photo recognition can miss or misidentify foods. Recipe exclusion and Supabase pantry sync are the next step; this branch does not claim to implement them.

## Local backend and tablet

Requires Node 22+. Save the provider key only in ignored `backend/.env`:

```
PERCEPTRON_API_KEY=your_private_key
```

Run `npm start` from `backend/`. For the USB tablet run `adb reverse tcp:4242 tcp:4242`. The default Android backend URL is `http://127.0.0.1:4242`.

The server binds to loopback by default. This is a local hackathon setup, not a public unauthenticated service. A hosted deployment needs HTTPS, caller authentication and per-user quotas before exposure. The key is never compiled into Android, returned by the API or logged. Images are sent inline for inference and not written to disk by our backend; provider retention follows Perceptron's service policies. JPEGs are limited to 3 MB; the endpoint allows two concurrent scans with a 75-second provider timeout.

The app clearly identifies the upload action. Selecting a photo alone does not send it. Prepared photos are kept temporarily in app-private cache and removed on replacement/removal. Temporary full-resolution camera files are removed after preparing a preview or cancelling.

## Separate tablet preview

```
sh gradlew :app:assembleDebug :app:assembleDebugAndroidTest -I tools/pantry-preview.init.gradle
```

The optional init script changes only this build's application ID and label to **Recipe Wallet Pantry**. A regular build retains the normal app identity.

## Checks

- `npm test`: backend tests include request bounds, invalid/empty/partial model output, upstream failure handling, schema requests and route compatibility.
- `:app:testDebugUnitTest`: includes repeat-scan duplicate protection and confirmed amount preservation.
- `PantryScreenTest`: manual add/edit/remove and local persistence.
- `PantryScanLiveTest`: opt-in real tablet → backend → Perceptron → editable review → selected-only local save. Run with `-e liveScan true`; default test runs skip the external call.

The live test uses Yinan Chen's public-domain [fridge photo](https://commons.wikimedia.org/wiki/File:Gfp-fridge-contents.jpg), included only in the test APK. It never uses personal photos and removes only its own test ingredient.

API references: [Quickstart](https://docs.perceptron.inc/perceptron-mk1.5/index), [structured outputs](https://docs.perceptron.inc/perceptron-mk1.5/capabilities/structured-outputs).
