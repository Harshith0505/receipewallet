# Recipe Wallet

An Android AI shopping agent for DubHacks 2026 — Visa: Reimagine Shopping.

Tell it what dinner should accomplish: a meal preference, an upfront budget, and ingredients you already own. Gemini searches the demo catalog, compares store/card combinations with server-calculated prices, and prepares a basket for your approval. Follow-up requests revise the plan before checkout.

The native Android project and local Node.js backend live in **[recipe-wallet-android/](recipe-wallet-android/)**. Open that directory in Android Studio. See its **[setup guide](recipe-wallet-android/README.md)** and **[demo script](recipe-wallet-android/DEMO.md)**.

Example: “Find me a vegetarian noodle dinner under $12. I already have garlic.” Then: “I already have soy sauce too.” Review the changed basket and approve it before entering Stripe test checkout.

Prices, store locations and Visa reward programs are illustrative. Ingredient estimates price portions and exclude tax. No real grocery order, delivery or earned rewards result. The agent has no payment tool; Stripe payment details remain outside the AI conversation.
