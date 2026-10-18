# Run VEYZL against the live-grocery branch

PR #7's previous audit (`9a6b0835-4ae0-40e7-ae4b-4f15c7c9897b`) could not resolve the expired temporary tunnel address. Its nine inconclusive results did not test model behavior. This branch now includes the AI response/safety and provider retry fixes previously verified on the audit branch, adapted without replacing recipe drafting, planning goals, ingredient revisions or retailer cart code.

## Local audit server

1. Keep the Gemini configuration in ignored `backend/.env` and the existing audit bearer token in ignored `backend/.env.audit.local`. Do not copy credentials into Git, the APK or the VEYZL model-key field.
2. From `backend/`, run `npm run start:audit`. This binds the protected server to `127.0.0.1:4243`.
3. Run `cloudflared tunnel --url http://127.0.0.1:4243 --no-autoupdate`.
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
