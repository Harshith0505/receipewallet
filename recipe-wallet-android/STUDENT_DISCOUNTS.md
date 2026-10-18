# MVP student offers

The shop comparison and checkout have a **Student discounts** switch, enabled by default for the demo. The shared store fixtures configure QFC at 10%, Whole Foods at 5%, and Trader Joe's at 0%. These are simulated offers, clearly labelled in the app; no actual retailer student policy or student status is verified.

Discounts apply to whole-package groceries. Fees are unchanged. Demo tax and advisory card rewards are calculated from discounted groceries. Store rankings, checkout approval, budget checks, saved recipe baskets, agent-generated plans, and receipt previews use those amounts. The original subtotal and a separate negative discount line stay visible.

Android and the backend share the rates. Payment pricing recalculates the discount from the selected store and ignores client-supplied rates/totals. The payment fingerprint binds eligibility and the computed discount, including stores that have no student offer. Changing student mode invalidates a pending payment attempt, and is blocked during payment/verification.

No student documents or personal data are collected. Production student verification and actual retailer offer rules are outside this MVP.

Checks: backend student pricing/payment/agent tests; Android discount, fee, tax and reward tests; tablet comparison → checkout → switch off/on → receipt preview; existing saved/manual recipe flow regression checks.
