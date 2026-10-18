package com.recipewallet.app

import android.graphics.Bitmap
import android.provider.Settings
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeLoginOpensAccountAndReturnsToTheMealDraft() {
        compose.onNodeWithText("Use my taste profile ✨").performScrollTo().performClick()
        compose.onNodeWithText("Login").assertIsDisplayed().performClick()
        compose.onNodeWithText("Your recipe library").assertIsDisplayed()
        compose.onNodeWithText("← Home").performClick()
        compose.onNodeWithText("Login").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("I want comforting and familiar",substring=true)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

 @org.junit.Before fun resetLocalProfile() {
  InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("meal-studio-v1",android.content.Context.MODE_PRIVATE).edit().clear().commit()
  compose.activityRule.scenario.recreate()
 }
    @Test fun switchingBuildModesPreservesTheMealDraft() {
        compose.onNodeWithText("Build with AI").assertIsSelected()
        compose.onNodeWithText("Use my taste profile ✨").performClick()
        compose.onNodeWithText("Build my meal").assertIsEnabled()
        screenshot("front-page-ai.png")
        compose.onNode(hasSetTextAction()).performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val keyboardPackage = Settings.Secure.getString(compose.activity.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).substringBefore("/")
        assertTrue("Keyboard did not open", device.wait(Until.hasObject(By.pkg(keyboardPackage)), 5000))
        compose.onNodeWithText("Build manually").performScrollTo().performClick()
        compose.onNodeWithText("Build manually").assertIsSelected()
        compose.onNodeWithText("Choose a recipe").performScrollTo().assertIsDisplayed()
        compose.waitUntil(5000) {
            val insets = compose.activity.window.decorView.rootWindowInsets
            insets != null && !WindowInsetsCompat.toWindowInsetsCompat(insets).isVisible(WindowInsetsCompat.Type.ime())
        }
        assertTrue("Keyboard stayed open in manual mode", device.wait(Until.gone(By.pkg(keyboardPackage)), 5000))
        screenshot("front-page-manual.png")
        compose.onNodeWithText("Build with AI").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("I want comforting and familiar",substring=true)
    }

    @Test fun manualRecipeOpensAnEditableBasket() {
        compose.onNodeWithText("Build manually").performClick()
        compose.onNodeWithText("Spicy garlic noodles").performScrollTo().performClick()
        compose.onNodeWithText("Compare complete baskets →").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            val vm = ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            assertEquals(1, vm.step)
            assertEquals("noodles", vm.recipeId)
        }
        compose.onNodeWithText("Garlic").performScrollTo().performClick()
        compose.onNodeWithText("Compare complete baskets →").performScrollTo().performClick()
        compose.onNodeWithText("Your shopping basket").assertExists()
        compose.onNodeWithText("Check live retailer prices").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, ViewModelProvider(compose.activity)[WalletViewModel::class.java].step) }
    }

    @Test fun mealShortcutsUseDistinctGoalsAndTypingReturnsToTheMealRequest() {
        lateinit var vm:WalletViewModel
        compose.runOnIdle { vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java] }
        compose.onNodeWithText("Use my taste profile ✨").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("profile",vm.planningGoal) }
        compose.onNodeWithText("Use what I have 🥬").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("pantry",vm.planningGoal) }
        compose.onNodeWithText("Surprise me").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("surprise",vm.planningGoal) }
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("Homemade pizza")
        compose.runOnIdle { assertEquals("balanced",vm.planningGoal);assertEquals("Homemade pizza",vm.preference) }
    }

    @Test fun failedPlanningOffersAWorkingManualAlternative() {
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[WalletViewModel::class.java].aiError = "The demo backend is unavailable."
        }
        compose.onNodeWithText("Choose a recipe instead").performScrollTo().performClick()
        compose.onNodeWithText("Build manually").assertIsSelected()
        compose.onNodeWithText("Choose a recipe").performScrollTo().assertIsDisplayed()
    }

    @Test fun retailerLookupReturnsToTheEditedIngredientBasket() {
        compose.onNodeWithText("Build manually").performClick()
        compose.onNodeWithText("Spicy garlic noodles").performScrollTo().performClick()
        compose.onNodeWithText("Garlic").performScrollTo().performClick()
        compose.onNodeWithText("Compare complete baskets →").performScrollTo().performClick()
        compose.onNodeWithText("Your shopping basket").assertExists()
        compose.onNodeWithText("← Back").performScrollTo().performClick()
        compose.runOnIdle {
            val vm = ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            assertEquals(1, vm.step)
            assertEquals(false, "garlic" in vm.selected)
        }
        compose.onNodeWithText("Compare complete baskets →").performScrollTo().assertIsEnabled()
    }

    @Test fun expiredPlanningCanBeResetFromTheError() {
        lateinit var vm: WalletViewModel
        compose.runOnIdle {
            vm = ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            vm.preference = "Make it cheaper"
            vm.aiError = "Your planning session expired. Start a new shop."
        }
        compose.onNodeWithText("Start a new shop").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(null, vm.aiError)
            assertEquals("", vm.preference)
            assertEquals(null, vm.agentPlan)
        }
        compose.onNodeWithText("Use my taste profile ✨").performScrollTo().performClick()
        compose.onNodeWithText("Build my meal").performScrollTo().assertIsEnabled()
    }
    @Test fun generatedPizzaOpensItsOwnIngredientsAndExcludesOwnedFlour() {
        lateinit var vm: WalletViewModel
        compose.runOnIdle {
            vm = ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            vm.generatedRecipe = org.json.JSONObject("""{"id":"pizza-test","name":"Homemade pizza","servings":4,"prepMinutes":40,"constraints":{"budgetCents":3000},"ingredients":[{"name":"Flour","amount":"300 g","owned":true},{"name":"Mozzarella","amount":"200 g","owned":false}],"steps":["Prepare dough and bake the pizza."]}""")
            vm.agentReply = "A pizza draft for four."
        }
        compose.onNodeWithText("Homemade pizza").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review ingredients →").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Homemade pizza", vm.recipe.name)
            assertEquals(setOf("line-1"), vm.selected)
            assertEquals(3000L, vm.planBudget)
            assertEquals(4, vm.servings)
            assertEquals(1, vm.step)
        }
        compose.onNodeWithText("Compare complete baskets →").performScrollTo().performClick()
        compose.onNodeWithText("Your shopping basket").assertExists()
    }

    @Test fun pantrySuggestionReplacesAPersonalRecipeWithOnlyMissingCatalogItems() {
        compose.runOnIdle {
            val vm = ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            vm.choosePersonal(PersonalRecipe("prior", "Prior personal meal", listOf("Pasta")))
            vm.cookFromPantry(WalletViewModel.PantrySuggestion("noodles", listOf("garlic"), listOf("noodles", "soy"), false, "Two missing ingredients"))
            assertEquals(false, vm.isPersonal)
            assertEquals("noodles", vm.recipeId)
            assertEquals(setOf("noodles", "soy"), vm.selected)
            assertEquals(1, vm.step)
        }
    }

}
