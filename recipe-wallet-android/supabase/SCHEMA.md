# Recipe Wallet database fields

The first version uses Supabase’s built-in `auth.users` table and one app-owned table, `public.recipes`.

## Users → recipes

One signed-in user can own many recipes. `recipes.user_id` references `auth.users.id`. Supabase Auth manages account IDs, email addresses, password verification and sessions. The Android client never writes directly to `auth.users`.

## `public.recipes`

| Field | PostgreSQL type | Required / default | Purpose |
|---|---|---|---|
| `user_id` | `uuid` | Required; signed-in user’s ID | Recipe owner; foreign key to `auth.users.id` |
| `id` | `uuid` | Required; generated UUID | Stable recipe ID, including recipes created offline |
| `name` | `text` | Required; 1–80 characters after trimming | Recipe title |
| `ingredients` | `text[]` | Required; 1–40 ingredient lines | The editable ingredient list, including quantities as written |
| `prep_minutes` | `integer` | Optional; null, or 1–999 | Preparation time in minutes |
| `version` | `integer` | Required; starts at 1 | Increases on every update; detects stale edits |
| `created_at` | `timestamptz` | Required; server time | When the recipe was first saved to the cloud |
| `updated_at` | `timestamptz` | Required; server time | Most recent cloud update |

Primary key: `(user_id, id)`. Index: `(user_id, updated_at DESC, id)`. Deleting an Auth user also deletes their recipes.

Ingredient lines must each contain 1–200 characters and cannot be null or blank. Example: `['200 g pasta', '100 g cheddar', '1 cup milk']`. Keeping quantities with the ingredient text matches the current editor and preserves recipes written in the user’s own words.

## Saving and editing

The app calls `public.save_recipe(p_recipe_id, p_name, p_ingredients, p_prep_minutes, p_expected_version)`.

- New recipe: expected version `0`.
- Existing recipe: send the version last downloaded.
- Success: receive the saved recipe with its new server version and timestamps.
- Version mismatch: HTTP `409`; the Android app preserves the local edit as a separate copy.

The function gets the owner from the signed-in session. It uses the caller’s permissions, with Row Level Security applied. Users may select, create, update and delete only their own rows. Anonymous callers have no recipe-table access. Recipe IDs and ownership cannot be reassigned by an update.

## What is stored on the tablet

The guest recipe book stays local until the user explicitly imports it into their account. Each account has a separate local cache containing recipes, server versions, modification times and a pending-sync flag. Access and refresh tokens are encrypted using Android Keystore. The database password is never included in the app.

## Applying the schema

Open the new project’s Supabase SQL Editor and run `migrations/202610170001_recipes.sql` once. It creates the table, constraints, index, policies, trigger and save function in one transaction. The table will then appear in Table Editor. An empty Table Editor is expected until a signed-in user saves or imports recipes.

The checked-in migration is the source of truth. The Android build needs the project URL and **publishable** key in the ignored `supabase.properties` file; see `supabase.properties.example` and `README.md`.
