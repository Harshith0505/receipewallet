# Email confirmation verification

Branch: `new-features-added-by-lakshmi`, originally created as `test/email-confirmation-flow`, based on `origin/integration/supabase-recipes` (`4060a53`).

## Automated checks

On October 17, 2026, all 10 `CloudStorageTest` tests passed on `Medium_Phone` (Android 17/API 37), including both new regression tests. The debug app and instrumentation APK also build successfully.

All 13 tests (`CloudStorageTest` and `ConfirmationRoutingTest`) then passed on `Medium_Phone_API_36` (Android 16/API 36), using `adb shell am instrument -w -e class com.recipewallet.app.CloudStorageTest,com.recipewallet.app.ConfirmationRoutingTest com.recipewallet.app.test/androidx.test.runner.AndroidJUnitRunner`. The emulator required a cold start with `-gpu host` after the default graphics configuration went offline during startup. The live connectivity test was skipped because Supabase configuration was absent.

Run on the Android API 36 emulator (set `ANDROID_SERIAL` to its actual serial):

```sh
ANDROID_SERIAL=emulator-5556 sh gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.recipewallet.app.CloudStorageTest,com.recipewallet.app.ConfirmationRoutingTest
```

The tests use synthetic accounts and transport responses. They cover encrypted verifier persistence, the PKCE challenge and exchange, account matching, invalid and expired links, returning to the account screen, and preserving an active checkout. Additional regression tests check that a failed exchange retains the verifier for retry, a completed callback cannot be exchanged again, and a rejected signup restores the previous pending signup.

The existing Android 17/API 37 emulator cannot run these Compose UI tests with the current test dependencies: Espresso fails while looking up `android.hardware.input.InputManager.getInstance`. This is a test-framework failure before the UI assertions execute.

## Real email check

Prerequisites: ignored `supabase.properties` populated from `supabase.properties.example`, a test email address, and the exact callback `com.recipewallet.app://auth/confirmed` configured in Supabase Auth redirect settings. This checkout did not contain the local configuration at the start of this test run.

1. Install the configured build. Open **Saved recipes → Back up & sync → New here? Create an account**.
2. Submit the test email and a strong password. Confirm the app shows a pending email and does not yet show a signed-in account.
3. Open the newly received confirmation link on the same emulator. Confirm it returns to **Your recipe library**, signs into the expected email, and clears the pending confirmation.
4. Restart the app and confirm the account remains signed in.
5. Reopen the consumed link. Confirm it does not switch accounts or create another session, and gives a recovery message.
6. For a separate fresh signup, close the app before opening the email. Confirm the link starts the app and completes sign-in using the persisted verifier.
7. Check an expired link and a link opened on another device. Confirm the app offers password sign-in after email verification rather than importing URL bearer tokens.

Actual email delivery, hosted redirect settings, and successful live sign-in remain deferred at the user's request while Supabase setup is in progress. A test email has been supplied, but this checkout still lacks the public configuration. No signup emails were sent by the automated tests.
