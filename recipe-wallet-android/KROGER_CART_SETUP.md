# Kroger cart handoff

This feature transfers reviewed packages to a shopper's existing Kroger-family cart. It does not place a grocery order, reserve inventory, schedule delivery, take payment, or earn card rewards. The shopper finishes and confirms everything on the retailer's website.

## Application setup

Use the same Production Kroger developer application and server credentials already used for catalog lookups. In its settings:

1. Keep **Products (Public)** and **Locations (Public)** enabled.
2. Add **Cart (Public)**, which grants the `cart.basic:write` scope. This is a new permission and needs the application owner's approval. Shopper access still requires a separate browser sign-in and consent; catalog client-credentials tokens cannot write a shopper's cart.
3. Register this exact **Web Redirect URI** for the local USB/emulator setup:

   ```text
   http://127.0.0.1:4242/kroger/callback
   ```

4. Set the same value in the ignored backend configuration, then restart the backend:

   ```dotenv
   KROGER_REDIRECT_URI=http://127.0.0.1:4242/kroger/callback
   ```

5. Keep `adb reverse tcp:4242 tcp:4242` active on the device. The browser's loopback callback reaches the computer through the same USB reverse used by the app. A hosted backend instead requires its registered HTTPS `/kroger/callback` URL. Never derive redirect URLs from request input or Host headers.

The backend's `krogerCartConfigured` health field indicates local configuration presence, not that the permission or callback has actually been registered or a shopper has signed in.

## Shopper flow

1. Build and refresh a live Kroger package basket.
2. Tap **Connect Kroger account**. The external browser opens Kroger's authorization page, requesting only cart-writing scope. The server generates one-time state and an S256 PKCE challenge. Application secret, verifier and customer bearer tokens stay off Android.
3. Sign in and approve on Kroger. Kroger redirects to the backend, which validates the one-time state, exchanges the code server-side, and redirects the browser to a static return page without the code in the final URL. Tap **Return to Recipe Wallet**, or return manually and tap **Check Kroger connection**. A browser return does not transfer products or leave a busy Stripe test checkout.
4. Choose pickup or delivery. Tap **Review cart transfer** and explicitly confirm **Add packages to Kroger cart**. Sign-in, dialog dismissal and opening the retailer cart do not call the Cart API.
5. The server validates the signed, ten-minute reviewed quote and fetches prices again. Changed prices/products, expired quotes, unavailable fulfillment, or a variant without an exact matching UPC block the transfer. Refresh and review again if needed.
6. On Kroger's documented HTTP 204 response, the app reports packages sent to the cart. Open the retailer cart and check existing items, quantities, account, shopping location, fulfillment, promotions, final prices, taxes and fees before paying there. The Cart API does not set the shopping location. QFC and Fred Meyer store names open their respective `/cart` pages; other banners currently default to Kroger and may need their own website opened manually.

## Retry and privacy behavior

- A transfer is bound to the signed quote, fulfillment and a generated attempt ID. Replaying a successful attempt returns the prior success without another Cart API write. The connection permits one successful basket transfer; review the cart before explicitly disconnecting/reconnecting for another.
- A timeout, network failure, unexpected successful status or upstream server error after submission produces an uncertain outcome. The app blocks edits and another transfer. **Check Kroger connection** can resolve a result if the backend recorded the acknowledgement. Otherwise open the retailer cart and inspect it yourself before using **I checked my cart · disconnect**. The public integration cannot read the retailer cart and never pretends that manually checking it is a server-verified receipt.
- Credentials are held on the backend. Each app gets a separate opaque connection proof; customer bearer tokens and refresh tokens are never returned to Android. No customer refresh tokens are retained. Connections expire after twenty minutes or sooner if the access token expires. Disconnecting forgets the server connection but does not sign out of the retailer website or remove items.
- This local prototype keeps connections/attempts in server memory and the connection proof in the Android ViewModel. Restarting the server loses connections and invalidates existing quote signatures; Android process death loses its connection proof. No transfer runs automatically after either restart. Inspect the retailer cart before repeating any uncertain previous submission. A hosted multi-user release still needs app authentication and durable, protected transfer records.

## Verification boundary

Backend tests use injected provider fixtures for OAuth state/PKCE, invalid callbacks, expired grants, quote tampering/freshness, exact UPC counts, fulfillment, idempotency, uncertain results and disconnect. Native tests verify that sign-in/dialog dismissal do not write, explicit approval does, uncertain results block edits/repeats, status checks resolve recorded results without resubmission, and the return link cannot interrupt busy Stripe checkout.

These tests do not establish a real shopper consent or real Cart API write. Those require owner-approved Cart API access/redirect registration and a shopper-controlled sign-in. No real order or payment is used for verification.

October 17, 2026 verification: 80 backend tests, seven Kotlin JVM tests and 17 offline native checks passed. The native live catalog/package-basket test also passed after adding signed quote support. The real customer OAuth grant and Cart API write remain unverified; the inspected developer app currently has Products/Locations access and no registered callback. No real shopper account was authorized or modified by these checks.

References: [Kroger public APIs](https://www.postman.com/kroger/the-kroger-co-s-public-workspace/documentation/ki6utqb/kroger-public-apis), [Kroger app registration](https://developer.kroger.com/documentation/support/website-help/applications), [QFC cart](https://www.qfc.com/cart), [Fred Meyer cart](https://www.fredmeyer.com/cart).
