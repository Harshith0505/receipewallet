# VEYZL audit evidence

Project: https://veyzl.ai/projects/22b7c5f4-c044-49b2-9554-b5e13b60ff82

Test backend: https://recipe-wallet-veyzl-audit.vercel.app

## Initial run: request-schema mismatch

Audit ID: `9cf60c42-128d-4625-b1f9-3be24f5766b2`. Suite fingerprint: `04bd0a65adac4176`.

The hosted audit reported nine errored tests and 18 blocking issues. Each safety metric had zero scored probes and was marked inconclusive. The probe details showed that VEYZL sent `{ "prompt": "..." }` to `POST /plan`, while Recipe Wallet expected `{ "message": "..." }`. The endpoint rejected the request before model behavior could be evaluated.

These errors do **not** demonstrate successful prompt injection, secret leakage, or other unsafe model behavior, despite the dashboard's generic remediation paragraphs. They demonstrate an integration failure that prevented a meaningful behavioral audit.

## Change prompted by the run

The real `/plan` handler now accepts either `message` or `prompt`, uses the same validation and planning path for both, and returns HTTP 400 for missing, overlong, or conflicting input. There is no special audit-only safety response and no hard-coded handling of the auditor's probe strings.

A regression test verifies both request shapes reach the planner and malformed/ambiguous requests are rejected. All 61 backend tests pass, including hosted authentication, fail-closed configuration, Vercel rewrites and the original pricing/payment checks.

## Interpretation limits

VEYZL's repository profile still labels this as Food / Nutrition Tracker, while its stated intent is a lower-impact meal and grocery shopping assistant. Its report also displays `gpt-4` as the model label; this backend actually calls Gemini `gemini-3.5-flash-lite`. The UI offers no model/profile override in this project. These labels should be clarified with VEYZL before making framework or model-specific claims.

A subsequent audit is needed to establish behavioral results after the request-schema fix. Do not describe the initial inconclusive run as a passing audit or a confirmed safety vulnerability.
