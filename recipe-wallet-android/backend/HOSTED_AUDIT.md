# VEYZL test backend

Prepared from updated `main` at `4060a53`; deployment changes are on `codex/veyzl-test-backend`. The test backend is live at https://recipe-wallet-veyzl-audit.vercel.app. This deployment serves the existing Recipe Wallet backend through a separate Vercel entrypoint; local `npm start` and the Android emulator's port forwarding continue to use `server.mjs` directly.

## Deployment

Deploy the `backend/` directory as a separate Vercel project named `recipe-wallet-veyzl-audit`, using the user-selected `codinglakshmis-projects` workspace. Select the Other framework preset. `package.json` pins Node.js 24 to prevent an automatic major-runtime upgrade; `vercel.json` routes requests to `api/index.mjs` with a 120-second maximum duration.

Configure these server-side secret environment variables before deploying:

- `GEMINI_API_KEY`: the project's Gemini key.
- `AUDIT_BEARER_TOKEN`: a newly generated cryptographically random token of at least 32 characters.
- `STRIPE_SECRET_KEY` and `STRIPE_PUBLISHABLE_KEY`: matching Stripe **test** keys, if testing checkout. The existing backend rejects live keys.
- Optional `GEMINI_MODEL`: defaults to `gemini-3.5-flash-lite`.

Keep credentials out of source uploads and Git. `.vercelignore` excludes local environment files, runtime databases, and tests. Do not deploy from the Android project root or copy `memory.db` into a deployment.

The hosted entrypoint fails closed if its audit token is missing. AI, pricing, and payment routes require `Authorization: Bearer <audit-token>`. The root discovery response and configured `/health` endpoint are public and do not disclose credentials.

## Connect VEYZL

In the existing Recipe Wallet VEYZL project's **Model stats**, enter the resulting HTTPS origin as the backend URL, then configure Backend auth as a bearer token. This tests the real backend; a provider key entered into VEYZL as a fallback would test the model directly instead.

The configured project is https://veyzl.ai/projects/22b7c5f4-c044-49b2-9554-b5e13b60ff82. Its manual audit route is `POST /plan`. Both Android's `message` field and VEYZL's `prompt` field enter the same planner; conflicting values and invalid input are rejected with HTTP 400.

Verify these behaviors before running an audit:

1. Public `GET /health` reports the expected configuration.
2. An unauthenticated `POST /plan` returns 401.
3. An authenticated `POST /plan` with `{ "message": "Find me a vegetarian noodle dinner under $30. I already have garlic." }` returns a grounded plan.
4. Authenticated pricing and payment endpoints retain the original validation and test-mode restrictions.

`npm test` covers authentication before provider calls, fail-closed configuration, Vercel route rewriting, pre-parsed JSON bodies, and the existing backend calculations.

## Limits

This remains a hackathon test backend. The existing planning sessions live in process memory; a serverless cold start or routing to another instance can expire a follow-up session before its nominal 20-minute limit. Persistent multi-instance sessions require a shared session store before relying on hosted conversational continuity. The audit token grants test-backend access and should be rotated after the event. It must not be embedded into a publicly distributed Android APK.

The initial deployment attempt through the connected Lasya account was blocked by Vercel account scope permissions (403). The user subsequently authorized the `codinglakshmi` CLI account. Deployment succeeded with Node.js 24. Public health returned 200, unauthenticated planning returned 401, and an authenticated live planning request returned the expected $15.80 noodle basket. An initial Gemini high-demand response was transient and reproduced locally.
