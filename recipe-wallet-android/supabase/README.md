# Recipe Wallet database

Branch: `feat/supabase-recipes`, based on the Android UI branch.

The migration creates private recipes owned by Supabase Auth users. A recipe contains its UUID, name, ingredient lines, optional preparation time, server timestamps, and a version. The existing grocery product catalog and prices remain independent; no card numbers or payment credentials belong in this table.

Every select, insert, update, and delete is restricted to the authenticated owner using Row Level Security. No anonymous database access is granted. The `save_recipe` RPC derives ownership from the signed-in user and checks the expected version to prevent stale edits from silently overwriting another device.

## Hosted setup

1. Connect a Supabase account and select/create a Recipe Wallet project.
2. Apply the migration in `supabase/migrations/`.
3. Keep email/password authentication enabled and email verification enabled.
4. Copy `supabase.properties.example` to `supabase.properties` in the Android project root. Add the project HTTPS URL and publishable key from the project Connect dialog. The real configuration file is ignored by Git.
5. Never use a secret or service-role key in the Android app.

Official references: [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), [publishable keys](https://supabase.com/docs/guides/api/api-keys), [email/password auth](https://supabase.com/docs/guides/auth/passwords).

The hosted **Recipe Wallet** project is live: `vxfifvgnkoaezobuaidv`, region `us-east-1`.

Dashboard: https://supabase.com/dashboard/project/vxfifvgnkoaezobuaidv

Migration `202610170001_recipes.sql` was applied once through SQL Editor on October 17, 2026. Do not rerun it on this project. For a fresh project, follow the setup steps above. SQL Editor application does not register a Supabase CLI migration history entry; reconcile migration history before adopting CLI database pushes against this existing project.

This laptop has the public project URL and publishable key in ignored `supabase.properties`. Each teammate can copy the example and obtain the same public values from the project’s Connect dialog. Database passwords and secret/service-role keys are not needed by Android.

## Android behavior

Saved recipes → Back up & sync opens email/password sign-in. Cloud sync is optional. Without project configuration the app clearly says cloud sync is unavailable and continues storing device recipes.

After sign-in, the private account library is separate from the existing device book. Use **Import recipes from this device** to copy local recipes to the account explicitly. Signing out restores the device book. Cloud caches are scoped to both project and user. Session tokens are encrypted by Android Keystore; passwords are not persisted.

An edit is written to the local account cache before uploading. Failed sync keeps a pending edit for retry through **Sync**; this prototype syncs when the library opens, when saving and on explicit refresh, rather than in a background service. Stale versions preserve both recipes by uploading a copy instead of overwriting newer server data. The migration exposes owner-only CRUD; the current UI supports adding and editing recipes, without a delete control.

## Verification

From `supabase/tests`, run `npm ci` then `npm test`. PGlite runs the real PostgreSQL migration against synthetic Auth roles and users. Checks cover owner CRUD, denial across users, unauthenticated access, input constraints, immutable identity and version conflicts. The synthetic `auth.uid()` fixture is test-only; the hosted migration uses Supabase Auth.

Android `CloudStorageTest` checks encrypted session persistence, account/project cache isolation, token rotation, refresh owner checks, versioned saves, email confirmation and offline sign-out using a fake transport. These tests do not contact Supabase or send email. `RecipeLibraryTest` and `SavedRecipeFlowTest` cover local save/edit and continuation through grocery checkout.

Hosted verification passed: the real database save function inserts and updates with version increments; stale versions fail; cross-user reads, updates, deletes and forged-owner inserts are blocked. `tests/hosted-smoke.sql` runs these checks transactionally and rolls back all synthetic users and recipes. The live REST API also rejects anonymous reads and saves with database permission code `42501`.

`LiveSupabaseConnectionTest` is separate from the fake-transport storage tests and is opt-in with instrumentation argument `-e runLiveSupabase true`. It contacts the configured public Auth settings endpoint and checks anonymous recipe access is denied, without creating users or sending email.

A real account’s email confirmation and signed-in tablet-to-cloud recipe sync still require a user-created account for final end-to-end verification. Use Saved recipes → Back up & sync, create/confirm an account, sign in, then explicitly import device recipes.


## Current tablet check

The configured build is installed on Samsung SM-T225. Account-page navigation and recipe save/edit checks pass. After the tablet was connected to internet, the opt-in live connection check passed on October 17, 2026: the tablet reached the hosted Auth settings endpoint, confirmed email sign-in and sign-up are enabled, and verified anonymous recipe reads are denied by the database. Real-user sign-in and recipe import remain the final end-to-end check.

For email confirmation, Supabase’s default SMTP service sends only to project organization team addresses and currently limits sending to two emails per hour. Use an organization member’s email for the initial demo account, or configure custom SMTP before inviting other users. Keep email verification enabled. [Supabase SMTP documentation](https://supabase.com/docs/guides/auth/auth-smtp).
