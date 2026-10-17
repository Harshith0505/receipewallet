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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun switchingBuildModesPreservesTheMealDraft() {
        compose.onNodeWithText("Build with AI").assertIsSelected()
        compose.onNodeWithText("Try: vegetarian noodles under $12").performClick()
        compose.onNodeWithText("Build my meal").assertIsEnabled()
        screenshot("front-page-ai.png")
        compose.onNode(hasSetTextAction()).performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val keyboardPackage = Settings.Secure.getString(compose.activity.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).substringBefore("/")
        assertTrue("Keyboard did not open", device.wait(Until.hasObject(By.pkg(keyboardPackage)), 5000))
        compose.onNodeWithText("Build manually").performScrollTo().performClick()
        compose.onNodeWithText("Build manually").assertIsSelected()
        compose.onNodeWithText("Choose a recipe").assertIsDisplayed()
        compose.waitUntil(5000) {
            val insets = compose.activity.window.decorView.rootWindowInsets
            insets != null && !WindowInsetsCompat.toWindowInsetsCompat(insets).isVisible(WindowInsetsCompat.Type.ime())
        }
        assertTrue("Keyboard stayed open in manual mode", device.wait(Until.gone(By.pkg(keyboardPackage)), 5000))
        screenshot("front-page-manual.png")
        compose.onNodeWithText("Build with AI").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Find me a vegetarian noodle dinner under $12. I already have garlic.")
    }

    @Test fun manualRecipeOpensAnEditableBasket() {
        compose.onNodeWithText("Build manually").performClick()
        compose.onNodeWithText("Spicy garlic noodles").performScrollTo().performClick()
        compose.onNodeWithText("Compare stores  →").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            val vm = ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            assertEquals(1, vm.step)
            assertEquals("noodles", vm.recipeId)
        }
        compose.onNodeWithText("Garlic").performScrollTo().performClick()
        compose.onNodeWithText("Compare stores  →").performScrollTo().performClick()
        compose.onNodeWithText("Continue with this store  →").performScrollTo().performClick()
        compose.onNodeWithText("Total charged today · test").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Approve & pay $7.79").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Preview confirmation without payment").performScrollTo().performClick()
        compose.onNodeWithText("WALKTHROUGH · NO PAYMENT MADE").assertExists()
    }

    @Test fun failedPlanningOffersAWorkingManualAlternative() {
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[WalletViewModel::class.java].aiError = "The demo backend is unavailable."
        }
        compose.onNodeWithText("Choose a recipe instead").performScrollTo().performClick()
        compose.onNodeWithText("Build manually").assertIsSelected()
        compose.onNodeWithText("Choose a recipe").assertIsDisplayed()
    }
}
