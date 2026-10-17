# Run Recipe Wallet on an Android tablet

## Android Studio

Open the `recipe-wallet-android` directory (the one containing settings.gradle.kts),
not the parent repository. Select JDK 17, install SDK 36 if prompted, and sync Gradle.

On your tablet enable Developer options and USB debugging. Connect it with a data
USB cable and approve the computer's debugging prompt. Select it in Android Studio's
device menu and Run the app configuration.

Manual recipe selection, package prices, store comparison, product preferences,
and receipt preview work without Gemini, Stripe, or a running backend. Maps needs
a browser/maps handler and an internet connection.

## Optional AI and Stripe test checkout

In `backend`, copy `.env.example` to `.env`, configure private Gemini and Stripe
TEST credentials, and run `npm start` (Node 22+). Do not commit .env.

In a terminal with Android platform-tools on PATH:

```bash
adb devices
adb reverse tcp:4242 tcp:4242
```

The debug app defaults to http://127.0.0.1:4242; USB reverse forwards that to your
computer's backend. The same reverse command works for an emulator. Run it again
if the tablet disconnects. For multiple devices use `adb -s SERIAL reverse ...`.

The prebuilt debug APK can also be installed with `adb install -r recipe-wallet-task3-debug.apk`.
This is a debug build intended for testing. No actual groceries, delivery, or card
rewards result. Use only Stripe test card details for PaymentSheet.

## Demo sequence

Tomato basil pasta → adjust servings → mark olive oil already owned → compare
pickup and delivery → organic-only → choose a complete store → review package
line items → preview confirmation. Then try the Maps button from store comparison.
