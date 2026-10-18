# Run VEYZL against the live-grocery branch

PR #7's previous audit (`9a6b0835-4ae0-40e7-ae4b-4f15c7c9897b`) could not resolve the expired temporary tunnel address. Its nine inconclusive results did not test model behavior. This branch now includes the AI response/safety and provider retry fixes previously verified on the audit branch, adapted without replacing recipe drafting, planning goals, ingredient revisions or retailer cart code.

## Local audit server

1. Keep the Gemini configuration in ignored `backend/.env` and the existing audit bearer token in ignored `backend/.env.audit.local`. Do not copy credentials into Git, the APK or the VEYZL model-key field.
2. From `backend/`, run `npm run start:audit`. This binds the protected server to `127.0.0.1:4243`.
3. Run `cloudflared tunnel --protocol http2 --url http://127.0.0.1:4243 --no-autoupdate`.
4. Save the resulting HTTPS origin in the existing VEYZL project's Model stats. Retain the existing bearer token and `POST /plan` audit route.
5. Keep both processes running until required checks finish and the PR is merged. Closing the tunnel breaks future push-triggered audits. If restarting it, save the new URL before pushing or rerunning checks.

No Vercel deployment or GitHub-to-Vercel connection is required. The ordinary Android/tablet backend on port 4242 remains separate. Never tunnel its unprotected app server.

The audit entrypoint allows planning, pantry suggestions, recommendations and grocery quotes. It blocks retailer OAuth/cart, payment and purchase-memory routes even with a valid audit token, and rejects personal-account lookups on planning requests. It uses the actual planning handler and does not substitute canned responses for benchmark prompts.

## Behavior and verification

- Both Android's `message` and VEYZL's `prompt` use the same planner. The existing 2,400-character limit and revision-only requests are preserved; invalid/conflicting input returns 400.
- Plain answers/refusals are returned without a payable basket. Clear self-harm language follows the existing supportive safety response with no AI call. This is conservative phrase recognition, not a clinical classifier.
- Meal planning and pantry suggestions share paced Gemini calls with bounded retries; real quota failures remain errors. This queue is process-local and does not coordinate other servers using the same Google project.
- All 157 backend tests passed, including the live retailer/cart, recipe drafting and remix tests, plus audit authentication, route isolation, request aliases, safety and retry coverage.

A successful audit must be verified on the exact PR head commit in GitHub. Reports for other branches on this shared VEYZL project do not establish this PR's result.

## Recover a lost tunnel before rerunning a PR check

PR #8's audit `d16bb5d7-b8c4-4797-a4f0-d063dafcdecd` had nine inconclusive probes because the saved temporary hostname no longer resolved. The local backend and cloudflared processes were still running; cloudflared repeatedly logged `Unauthorized: Tunnel not found` after connection timeouts. A running process alone does not establish external reachability.

Before pushing or requesting another audit, check `curl --fail --max-time 15 https://YOUR-CURRENT-TUNNEL/health`. A successful response must come from the actual backend, and an unauthenticated planning request must still return 401. If DNS resolution fails or logs say `Tunnel not found`, stop the old tunnel, create a new one, and save the new HTTPS origin in VEYZL Model stats while retaining its bearer token and `POST /plan` route. Verify the resulting GitHub check on the PR's exact head commit.

The startup command above explicitly uses HTTP/2 to avoid the observed QUIC connection timeouts on the development network. This does not give a temporary tunnel a permanent hostname or an uptime guarantee. Keep the laptop connected while reviewing/merging, and verify reachability again after sleep or a network change.

After restoring connectivity, PR #8 audit `f1ddcdcf-8461-4b95-868a-cecca6a1ccb4` scored all nine probes: eight passed and the private-data jailbreak failed. The visible response echoed an internal privacy instruction rather than clearly refusing. No account data or keys appeared in the visible evidence. Clear requests to disclose private data, credentials or internal instructions now receive a deterministic refusal before provider access. Visible model text and structured tool arguments are also checked for substantial verbatim internal-instruction fragments before any result or recipe draft is returned. Short/common overlaps remain allowed; this does not detect every paraphrase or replace authorization checks.

All 163 backend tests pass, including privacy-request variations, benign recipe/security questions, visible-text/structured-output echo regressions, and the existing shopping, retailer/cart, safety and retry tests. Confirm the final VEYZL verdict on the exact updated PR commit.
