# Visa presentation readiness

Review of `feat/live-grocery-data`, October 18, 2026, against the rubric supplied by the team. Mentor guidance: a Visa API integration is not required. The supplied rubric lists UX as both 20% and 25%; confirm the final weight with organizers.

## Priorities before judging

1. **Technical execution and UX: rehearse the real cart handoff.** The primary path now connects meal planning to automatic live Kroger matching, explicit product/package review, a refreshed signed basket and retailer cart actions. It no longer requires a separate lookup button. The first nearby store is selected transparently; users can change store and products. A completed customer-authorized cart transfer still needs a live rehearsal; quote checks and mocked cart API tests do not establish that it works with a customer's account. Stripe remains a separate illustrative test checkout and does not pay for the Kroger cart.
2. **Innovation: demonstrate a consequential agent revision.** Use one scenario: dinner for four, a budget and pantry ingredients; revise a constraint and show exactly what changed. Ground each price, fee and reward in code or a provider response. An AI explanation should cite its returned facts rather than claim invented savings. A budget-rescue suggestion is stronger than more screens, but should only offer fulfillment that the actual selected store supports.
3. **Wallet value: make card recommendations meaningful.** Current profiles are illustrative, and the highest grocery rate generally wins. Add user-selected owned profiles, reward caps and fulfillment/category eligibility, with a case where the winner changes. Keep reward value separate from the amount charged. This does not require a Visa API or real card numbers.
4. **Commerce impact: collect evidence.** Time a few volunteers completing the same shopping task with and without the app. Report the number of people, task, median time and completion count. Show a concrete before/after package or fee comparison using the same reviewed basket. Do not claim conversion increases, real earnings or savings that were not measured.
5. **Trust and responsibility: prove control.** Show photo detections being edited before saving, AI ingredient review, upfront charge versus future rewards, explicit cart approval and server verification of a Stripe test receipt. Explain what leaves the device and what syncs. Dietary filters do not guarantee allergy safety. Distinguish current retailer prices from illustrative estimates.

## Copy and behavior changes in this pass

- The installed app uses the Recipe Wallet product name; its separate preview package is retained for installation compatibility.
- Replaced developer-facing demo/MVP language in active screens and agent instructions with plain shopping language.
- Kept concise, accurate labels for illustrative pricing/offers, estimated rewards, Stripe test checkout and no grocery order placed.
- Removed unverified rating/distance claims from the selected store card.
- Student offer scenarios now start off. Offers remain explicitly illustrative, rather than suggesting retailer eligibility or promotions have been verified.
- Updated the privacy disclosure to mention signed-in recipe sync, submitted AI details and explicitly uploaded pantry photos.
- Internal data filenames, protocol flags and payment metadata retain their existing names. These enforce compatibility and test-payment checks and are not presentation copy.
- The inactive food-story preview retains its sample-data labels. Removing those would misrepresent fabricated meal history.

## Rehearsal

Request a meal with servings, budget and a pantry ingredient → review the AI plan → change one constraint → inspect the changed shopping needs → check current retailer products → review sufficient whole packages → approve retailer cart handoff if the customer connection is available.

Show Stripe separately as **test checkout**, with its server-verified receipt. Explain the boundary in one sentence. Run on the actual presentation device, confirm backend connectivity, and have a truthful recorded walkthrough available if Wi-Fi or a provider fails. No real order or live card charge is needed to demonstrate these capabilities.
