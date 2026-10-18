package com.recipewallet.app

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class SavedRecipeFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun savedRecipeContinuesThroughShopsCardsAndReceipt() {
        val store = PersonalRecipeStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val recipe = PersonalRecipe(UUID.randomUUID().toString(), "Family cheese pasta", listOf("200 g pasta", "100 g cheddar"), 20)
        store.save(recipe)
        try {
            compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            compose.onNodeWithText("Saved recipes").performClick()
            compose.onNodeWithText(recipe.name).performScrollTo().performClick()
            compose.onNodeWithText("Use this recipe").performScrollTo().performClick()
            compose.onNodeWithText("Compare complete baskets →").performScrollTo().assertIsEnabled()
            lateinit var vm: WalletViewModel
            compose.runOnIdle {
                vm = ViewModelProvider(compose.activity)[WalletViewModel::class.java]
                assertFalse(vm.studentEligible)
                vm.changeStudentEligibility(true)
                assertTrue(vm.isPersonal)
                assertEquals(648L, vm.subtotal)
                assertEquals(2, vm.customBasketJson().getJSONArray("items").length())
            }
            screenshot("saved-ingredients.png")
            compose.onNodeWithText("Compare complete baskets →").performClick()
            compose.onAllNodesWithText("Review checkout →").onFirst().performScrollTo().assertIsDisplayed()
            screenshot("shopping-comparison.png")
            compose.onAllNodesWithText("Review checkout →").onFirst().performClick()
            compose.onNodeWithText("Approve & pay $5.54").performScrollTo().assertIsEnabled()
            screenshot("shopping-checkout.png")
            compose.onNodeWithText("Preview confirmation without payment").performScrollTo().performClick()
            compose.onNodeWithText("Receipt preview").assertIsDisplayed()
            compose.onNodeWithText("No payment was made.").assertIsDisplayed()
            compose.runOnIdle { assertFalse(vm.paymentVerified); assertEquals(554L, vm.receipt!!.charge); assertEquals(62L, vm.receipt!!.studentDiscount) }
            screenshot("shopping-receipt.png")
        } finally { store.remove(recipe.id) }
    }

    @Test fun unmatchedIngredientsRequireAnExplicitProductChoice() {
        compose.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ViewModelProvider(compose.activity)[WalletViewModel::class.java].choosePersonal(
                PersonalRecipe(UUID.randomUUID().toString(), "Unmatched recipe", listOf("mystery ingredient")))
        }
        compose.onNodeWithText("Compare complete baskets →").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            val vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            vm.compare(); assertEquals(1,vm.step)
            vm.matchProduct("line-0","flour");vm.setPacks("line-0",2)
            assertTrue(vm.canCompare);assertEquals(598L,vm.subtotal)
        }
        compose.onNodeWithText("Compare complete baskets →").assertIsEnabled()
    }
    @Test fun aboutDisclosureExplainsTheDemoAndPrivacy() {
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.onNodeWithText("About prices & privacy").performScrollTo().performClick()
        compose.onNodeWithText("Preferences and pantry are stored on this device.",substring=true).assertExists()
        screenshot("shopping-about.png")
        compose.onNodeWithText("Build with AI").performScrollTo()
        compose.onNodeWithText("Build with AI").assertExists()
    }

}
