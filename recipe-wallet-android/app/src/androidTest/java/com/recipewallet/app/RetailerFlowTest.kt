package com.recipewallet.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue

class RetailerFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun storeSearchUsesSelectedLocationAndNeverInventsMissingPrices() {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val vm = RetailerViewModel { path, payload ->
            calls.add(path to payload)
            if (path.endsWith("stores")) JSONObject("""{"stores":[{"id":"70500123","name":"QFC Test Store","address":"123 Test St, Seattle"}]}""")
            else JSONObject("""{"storeId":"70500123","currency":"usd","fetchedAt":"2026-10-17T12:00:00Z","products":[{"name":"Test pasta","packageSize":"16 oz","regularPriceCents":249,"promotionalPriceCents":null,"stockLevel":null,"soldBy":null},{"name":"Test missing price","packageSize":null,"regularPriceCents":null,"promotionalPriceCents":null,"stockLevel":null,"soldBy":null}]}""")
        }
        compose.setContent { MaterialTheme { RetailerScreen(listOf(RetailerNeed("pasta", "Pasta", "300 g needed")), {}, vm) } }
        compose.onNodeWithText("US ZIP code").performTextInput("98105")
        compose.onNodeWithText("Find nearby stores").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Check prices here").performScrollTo().performClick()
        compose.onNodeWithText("Search Pasta").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("$2.49").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not provided").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Stock: not provided · Price basis not provided").onFirst().assertExists()
        compose.runOnIdle {
            assertEquals("98105", calls[0].second.getString("postalCode"))
            assertEquals("70500123", calls[1].second.getString("storeId"))
            assertEquals("Pasta", calls[1].second.getString("query"))
        }
    }

    @Test fun unavailableProviderShowsAnActionableErrorAndBackWorks() {
        val vm = RetailerViewModel { _, _ -> error("Kroger is not connected yet. Add credentials to backend/.env.") }
        var returned = false
        compose.setContent { MaterialTheme { RetailerScreen(emptyList(), { returned = true }, vm) } }
        compose.onNodeWithText("US ZIP code").performTextInput("98105")
        compose.onNodeWithText("Find nearby stores").performScrollTo().performClick()
        compose.onNodeWithText("Kroger is not connected yet. Add credentials to backend/.env.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Find nearby stores").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("← Back").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, returned) }
    }

    private fun basketVm(onBasket: (JSONObject) -> Unit = {}, pending: CompletableDeferred<JSONObject>? = null) = RetailerViewModel { path, payload ->
        when {
            path.endsWith("stores") -> JSONObject("""{"stores":[{"id":"70500123","name":"QFC Test Store","address":"123 Test St, Seattle"}]}""")
            path.endsWith("products") -> JSONObject("""{"storeId":"70500123","currency":"usd","fetchedAt":"2026-10-17T12:00:00Z","products":[{"productId":"0001","itemId":"v1","name":"Test pasta","packageSize":"16 oz","regularPriceCents":133,"stockLevel":"HIGH","soldBy":"UNIT"}]}""")
            else -> {
                onBasket(payload)
                pending?.await() ?: JSONObject("""{"storeId":"70500123","currency":"usd","pricingScope":"item-subtotal-only","fetchedAt":"2026-10-17T12:01:00Z","subtotalCents":${149 * payload.getJSONArray("items").getJSONObject(0).getInt("quantity")},"items":[{"productId":"0001","itemId":"v1","name":"Test pasta","packageSize":"16 oz","regularPriceCents":149,"stockLevel":"HIGH","soldBy":"UNIT","quantity":${payload.getJSONArray("items").getJSONObject(0).getInt("quantity")},"lineSubtotalCents":${149 * payload.getJSONArray("items").getJSONObject(0).getInt("quantity")}}]}""")
            }
        }
    }

    private fun selectPasta(vm: RetailerViewModel, needs: List<RetailerNeed> = listOf(RetailerNeed("pasta", "Pasta", "300 g needed"))) {
        compose.setContent { MaterialTheme { RetailerScreen(needs, {}, vm) } }
        compose.onNodeWithText("US ZIP code").performTextInput("98105")
        compose.onNodeWithText("Find nearby stores").performScrollTo().performClick()
        compose.onNodeWithText("Check prices here").performScrollTo().performClick()
        compose.onNodeWithText("Search Pasta").performScrollTo().performClick()
        compose.onNodeWithText("Use for Pasta").performScrollTo().performClick()
    }

    @Test fun selectedPackagesAreRepricedBeforeReviewAndEditsDiscardTheOldQuote() {
        var submitted: JSONObject? = null
        val vm = basketVm(onBasket = { submitted = it })
        selectPasta(vm)
        compose.onNodeWithContentDescription("More packs for Pasta").performScrollTo().performClick()
        compose.onNodeWithText("Refresh & review live basket").performScrollTo().performClick()
        compose.onNodeWithText("Your live grocery basket").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("$2.98").onLast().performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, submitted!!.getJSONArray("items").getJSONObject(0).getInt("quantity"))
            assertEquals(false, submitted!!.getJSONArray("items").getJSONObject(0).has("priceCents"))
            assertEquals(298L, vm.basket!!.subtotal)
        }
        compose.onNodeWithText("Edit products & quantities").performScrollTo().performClick()
        compose.onNodeWithContentDescription("More packs for Pasta").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, vm.basket) }
    }

    @Test fun unmatchedIngredientsAndCombinedPackLimitsBlockReviewAndStoreChangesClearSelections() {
        val vm = basketVm()
        selectPasta(vm, listOf(RetailerNeed("pasta", "Pasta", "300 g needed"), RetailerNeed("sauce", "Sauce", "100 g needed")))
        compose.onNodeWithText("Refresh & review live basket").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Search Sauce").performScrollTo().performClick()
        compose.onNodeWithText("Use for Sauce").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(true, vm.canReview)
            vm.setQuantity("pasta", 20); vm.setQuantity("sauce", 1)
            assertEquals(false, vm.canReview)
        }
        compose.onNodeWithText("Change store · clears live selections").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(null, vm.selectedStore)
            assertEquals(0, vm.selections.size)
            assertEquals(null, vm.basket)
        }
    }

    @Test fun aLateQuoteCannotOverwriteAChangedRecipeContext() {
        val pending = CompletableDeferred<JSONObject>()
        val vm = basketVm(pending = pending)
        selectPasta(vm)
        compose.onNodeWithText("Refresh & review live basket").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(true, vm.busy)
            vm.configure("another-recipe", emptyList())
            pending.complete(JSONObject("""{"storeId":"wrong-store"}"""))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(null, vm.basket)
            assertEquals(false, vm.reviewing)
            assertEquals(null, vm.error)
            assertEquals(false, vm.busy)
        }
    }

    @Test fun liveKrogerProductsReachTheReviewedPackageBasket() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLiveRetailer") == "true")
        val vm = RetailerViewModel()
        compose.setContent { MaterialTheme { RetailerScreen(listOf(RetailerNeed("pasta", "Pasta", "300 g needed")), {}, vm) } }
        compose.onNodeWithText("US ZIP code").performTextInput("98105")
        compose.onNodeWithText("Find nearby stores").performScrollTo().performClick()
        compose.waitUntil(60000) { !vm.busy && (vm.stores.isNotEmpty() || vm.error != null) }
        compose.runOnIdle { assertEquals(null, vm.error); assertTrue(vm.stores.isNotEmpty()) }
        compose.onAllNodesWithText("Check prices here").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Search Pasta").performScrollTo().performClick()
        compose.waitUntil(60000) { !vm.busy && (vm.products.isNotEmpty() || vm.error != null) }
        compose.runOnIdle { assertEquals(null, vm.error); assertTrue(vm.products.first().selectable) }
        compose.onAllNodesWithText("Use for Pasta").onFirst().performScrollTo().performClick()
        compose.onNodeWithContentDescription("More packs for Pasta").performScrollTo().performClick()
        compose.onNodeWithText("Refresh & review live basket").performScrollTo().performClick()
        compose.waitUntil(95000) { !vm.busy && (vm.basket != null || vm.error != null) }
        compose.runOnIdle {
            assertEquals(null, vm.error)
            val basket = vm.basket!!
            assertEquals(vm.selectedStore!!.id, basket.storeId)
            assertEquals(2, basket.lines.single().quantity)
            assertEquals(basket.lines.single().price * 2, basket.subtotal)
        }
        compose.onNodeWithText("Your live grocery basket").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Estimated item subtotal").performScrollTo().assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.getExternalFilesDir(null), "kroger-live-basket.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
