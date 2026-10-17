# Recipe Wallet — agent demo

Pitch: “Tell Recipe Wallet what dinner needs to accomplish. Its AI agent builds a basket, compares where to buy it and which sample Visa card to use, then asks you to approve.”

1. Run the local backend with Gemini and Stripe test keys, and launch the Android app.
2. Enter: **Find me a vegetarian noodle dinner under $12. I already have garlic.**
3. Show the real tool activity: eligible recipes found, store/card combinations compared, basket prepared for review. The fixture produces a $7.79 upfront noodle basket, with $0.29 estimated future rewards and $7.50 cost after rewards.
4. Revise: **I already have soy sauce too.** The owned-ingredient list grows and the selected charge drops. Net-cost ties are deterministic; a card/store with more estimated rewards can have a slightly higher charge.
5. Optionally revise: **Actually my budget is $1.** The agent explains that no basket fits, shows the cheapest available charge, and disables approval. Change the budget back to $12 before checkout.
6. Tap **Approve basket and continue**. Show the upfront charge, future rewards and eligibility assumptions separately.
7. Pay using Stripe TEST card `4242 4242 4242 4242`, a future expiry and valid test CVC. Show the server-verified test receipt.

All prices are fictional portion estimates, exclude tax, and do not represent a full retail cart. No grocery order, delivery or actual rewards result. Card selection is advisory; the sample Visa card is not automatically loaded into Stripe. Never describe manual selection, mocked test fixtures, or confirmation previews as a live AI response or successful payment.

If Gemini is unavailable or rate-limited, show the error honestly and use manual recipe selection. Rehearse the request before judging. The local session lasts 20 minutes; “Start a new shop” clears its reference.

Technical evidence: the live backend completed planning, a pantry revision and an impossible-budget request with Gemini 3.5 Flash-Lite in approximately 2–3 seconds per turn. Stripe's test API created, confirmed and server-verified an idempotent $7.79 payment. Device test results are documented separately from those backend checks.

Ask the mentor whether a specific Visa API is mandatory and whether UX is weighted 20% or 25% in the conflicting rubric text. Confirm the submission deadline in the participant Hacker Guide or with organizers.
