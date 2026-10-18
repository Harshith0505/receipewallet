package com.recipewallet.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RetailerFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun storeSearchUsesSelectedLocationAndNeverInventsMissingPrices() {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val vm = RetailerViewModel { path, payload ->
            calls.add(path to payload)
            if (path.endsWith("stores")) JSONObject("""{"stores":[{"id":"70500123","name":"QFC Test Store","address":"123 Test St, Seattle"}]}""")
            else JSONObject("""{"storeId":"70500123","currency":"usd","fetchedAt":"2026-10-17T12:00:00Z","products":[{"name":"Test pasta","packageSize":"16 oz","regularPriceCents":249,"promotionalPriceCents":null,"stockLevel":null,"soldBy":null},{"name":"Test missing price","packageSize":null,"regularPriceCents":null,"promotionalPriceCents":null,"stockLevel":null,"soldBy":null}]}""")
        }
        compose.setContent { MaterialTheme { RetailerScreen(listOf("Pasta"), {}, vm) } }
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
}
