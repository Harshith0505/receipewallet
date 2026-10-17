# Recipe Wallet — combined demo

Pitch: “Tell Recipe Wallet what dinner needs to accomplish. Its AI agent builds a package-priced basket, compares stores and sample Visa rewards, and asks you to review before payment.”

1. Start the backend, run `adb reverse tcp:4242 tcp:4242`, and launch the app. The home screen offers Build with AI, Build manually and Saved recipes.
2. Request **Find me a vegetarian noodle dinner under $30. I already have garlic.** Show actual recipe search, basket comparison and preparation tool summaries.
3. Review the two-serving pickup basket. Current fixture: $15.80 charged today, $0.79 illustrative future rewards, $15.01 estimated cost after rewards. The grocery subtotal buys whole packages.
4. Revise **I already have soy sauce too.** The agent remembers garlic and removes the soy sauce purchase. The live package plan falls to $12.15.
5. Optionally set a $1 budget. Approval is disabled when no basket fits. Restore the $30 budget before continuing.
6. Tap **Review package-priced basket**. Show servings, pantry exclusions, pickup/delivery and organic-only controls. Incomplete stores cannot be selected. Complete store recommendations rank by estimated cost after rewards; upfront cost remains visible.
7. For the stock example, manually choose Tomato basil pasta and enable organic-only. The QFC demo option cannot supply organic basil; another complete store remains available. Delivery availability is also explicit.
8. Choose **Review checkout**. Review store, package counts, fulfillment fees, configured 0% demo tax and today's charge. Future rewards are shown separately.
9. Tap **Approve & pay**. Use Stripe test card `4242 4242 4242 4242`, a future expiry, a three-digit test CVC and test ZIP. The paid receipt appears only after server verification and contains the purchased packages and test reference.

Cancellation keeps the basket. Uncertain payment results block a new checkout until verification resolves the existing attempt. Servings and fulfillment changes cannot bypass an agent's upfront budget. Preview confirmation is explicitly labeled no payment made.

The agent defaults to two servings and pickup; adjust those choices in the store screen. Recipes, package prices, stock, product attributes, fees, ratings and rewards are illustrative. Real store addresses support directions only. No actual grocery order, delivery or rewards result. Saved personal recipes are local notes; shopping comparison uses the demo catalog.

The team reports Visa mentor confirmation that no Visa API is required. Clarify the conflicting UX rubric weights and confirm submission deadlines with organizers. If Gemini is unavailable, show its error honestly and use manual browsing; do not present it as live AI planning.

The updated shopping screens use the same design as the home page. To demonstrate a personal recipe, save pasta and cheddar, select **Use this recipe**, confirm products and packs, compare stores, and review checkout. A one-pack-each pickup basket currently costs $5.83 at the best sample store. The follow-up AI review also shows a computed **What changed** list.
