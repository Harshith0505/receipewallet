# 60-second demo — DubHacks 2026

**Pitch:** “Recipe Wallet turns what you're craving into a grocery basket, compares where to buy it, and shows which sample Visa card gives the lowest estimated cost.”

1. **0–10 seconds:** Enter “A quick spicy noodle dinner” and use the Gemini recipe match. Show its explanation and select the noodles. Requires a configured Gemini key; do not describe manual selection as AI.
2. **10–20 seconds:** Uncheck chili flakes. Explain that the basket and comparisons update from the actual selected ingredients.
3. **20–35 seconds:** Compare stores. With the demo fixture, Neighborhood Market wins: $6.13 groceries + $1.99 delivery = $8.12 charge. Its best sample Visa card estimates $0.31 in rewards, giving $7.81 after rewards.
4. **35–45 seconds:** Open checkout and switch between cards. The fee stays fixed; the rewards and after-rewards cost change. Show the 5% assumption and point-redemption explanation.
5. **45–60 seconds:** Complete Stripe PaymentSheet using a test card. Show the server-verified receipt and simulation notice. No store receives an order and no actual rewards are earned.

If keys are unavailable, use the explicitly labeled confirmation preview and state that no payment occurred. This is useful for UI rehearsal but does not demonstrate a working AI or Stripe integration.

## Before submission

- Configure both services and complete one real API call to Gemini and one Stripe test payment.
- Add teammates/Discords and track choices to the Devpost draft by October 17, 10:30 PM Pacific.
- Submit the final project before October 18, 10:00 AM Pacific (the earlier deadline shown on Devpost).
- Confirm the full Visa rubric in the participant Hacker Guide. The public description asks for generative AI; the public Devpost guide link is broken.

Sources: [Event/track brief](https://dh26.dubhacks.co/#tracks), [submission requirements](https://dubhacks-2026.devpost.com/).
