package com.recipewallet.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class KrogerCartFlowTest {
    @get:Rule val compose = createComposeRule()
    private class Harness {
        var uncertain = false
        var resolved = false
        val writes = mutableListOf<JSONObject>()
        val browser = mutableListOf<String>()
        val vm = RetailerViewModel { path, payload -> when (path) {
            "/retailers/stores" -> JSONObject("""{"stores":[{"id":"70500807","name":"Quality Food Center - Test","address":"123 Test St"}]}""")
            "/retailers/products" -> JSONObject("""{"storeId":"70500807","currency":"usd","fetchedAt":"fixture","products":[{"productId":"0001111085033","itemId":"0001111085033","name":"Pasta","packageSize":"16 oz","regularPriceCents":133,"soldBy":"UNIT","stockLevel":"HIGH"}]}""")
            "/retailers/basket" -> JSONObject("""{"storeId":"70500807","currency":"usd","pricingScope":"item-subtotal-only","fetchedAt":"fixture","subtotalCents":133,"quoteToken":"fixture-reviewed-quote","items":[{"productId":"0001111085033","itemId":"0001111085033","name":"Pasta","packageSize":"16 oz","regularPriceCents":133,"soldBy":"UNIT","stockLevel":"HIGH","quantity":1,"lineSubtotalCents":133}]}""")
            "/retailers/cart/connect" -> JSONObject().put("sessionToken", "a".repeat(43)).put("authorizationUrl", "https://api.kroger.com/v1/connect/oauth2/authorize?state=fixture")
            "/retailers/cart/status" -> JSONObject().put("status", "connected").apply {
                if (resolved) put("transfer", JSONObject().put("status", "added").put("attemptId", writes.last().getString("attemptId")).put("itemCount", 1))
            }
            "/retailers/cart/add" -> {
                writes.add(payload)
                if (uncertain) throw IOException("Connection lost after submit")
                JSONObject("""{"status":"added","itemCount":1,"orderPlaced":false,"paymentMade":false}""")
            }
            "/retailers/cart/disconnect" -> JSONObject().put("status", "disconnected")
            else -> error("Unexpected test request")
        } }
    }
    private fun ready(h: Harness) {
        compose.setContent { MaterialTheme { ShoppingPage(0, {}) { KrogerCartActions(h.vm, openBrowser = { h.browser.add(it) }) } } }
        compose.runOnIdle { h.vm.postalCode = "98105"; h.vm.findStores() }
        compose.waitForIdle()
        compose.runOnIdle { h.vm.chooseStore(h.vm.stores.first()); h.vm.searchProducts("pasta") }
        compose.waitForIdle()
        compose.runOnIdle { h.vm.selectProduct(h.vm.products.first()); h.vm.refreshBasket() }
        compose.waitForIdle()
    }
    private fun connect(h: Harness) {
        compose.onNodeWithText("Connect Kroger account").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, h.writes.size); assertEquals(1, h.browser.size) }
        compose.onNodeWithText("Check Kroger connection").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test fun signInAndDialogDismissalNeverWriteAndOnlyExplicitConfirmationTransfers() {
        val h = Harness(); ready(h); connect(h)
        compose.onNodeWithText("Review cart transfer").performScrollTo().performClick()
        compose.onNodeWithText("Keep reviewing").performClick()
        compose.runOnIdle { assertEquals(0, h.writes.size) }
        compose.onNodeWithText("Review cart transfer").performScrollTo().performClick()
        compose.onNodeWithText("Add packages to Kroger cart").performClick()
        compose.onNodeWithText("1 packages sent to your Kroger-family cart").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, h.writes.size)
            assertEquals("fixture-reviewed-quote", h.writes.single().getString("quoteToken"))
            assertEquals("PICKUP", h.writes.single().getString("fulfillment"))
            h.vm.transferCart(); assertEquals(1, h.writes.size)
        }
        compose.onNodeWithText("Open retailer cart").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("https://www.qfc.com/cart", h.browser.last()) }
    }

    @Test fun uncertainTransfersBlockEditsAndRepeatsUntilTheUserChecksTheCart() {
        val h = Harness(); h.uncertain = true; ready(h); connect(h)
        compose.onNodeWithText("Review cart transfer").performScrollTo().performClick()
        compose.onNodeWithText("Add packages to Kroger cart").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(h.vm.cartUncertain)
            val key = h.vm.selections.keys.single()
            h.vm.setQuantity(key, 2); h.vm.refreshBasket(); h.vm.transferCart()
            h.vm.configure("other-recipe", emptyList())
            assertEquals(1, h.vm.selections.getValue(key).quantity)
            assertEquals(1, h.writes.size)
            assertNotNull(h.vm.basket)
        }
        compose.onNodeWithText("Review cart transfer").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("I checked my cart · disconnect").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(h.vm.cartUncertain); assertEquals("disconnected", h.vm.cartConnection); assertEquals(1, h.writes.size) }
    }

    @Test fun checkingServerStatusCanResolveAnUncertainResultWithoutResubmitting() {
        val h = Harness(); h.uncertain = true; ready(h); connect(h)
        compose.runOnIdle { h.vm.transferCart() }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(h.vm.cartUncertain); h.resolved = true }
        compose.onNodeWithText("Check Kroger connection").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("added", h.vm.cartTransfer); assertFalse(h.vm.canTransfer); assertEquals(1, h.writes.size) }
    }
}
