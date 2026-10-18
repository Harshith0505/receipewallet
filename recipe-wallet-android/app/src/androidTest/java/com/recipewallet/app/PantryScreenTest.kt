package com.recipewallet.app

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PantryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun pantryOpensAndManualIngredientsCanBeSavedEditedAndRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PantryStore(context)
        val title = "Tomatoes " + UUID.randomUUID().toString().take(6)
        try {
            compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            compose.onNodeWithText("My pantry").performScrollTo().performClick()
            compose.onNodeWithText("Take a photo").assertIsDisplayed()
            compose.onNodeWithText("Choose a photo").assertIsDisplayed()
            screenshot("pantry-page-empty.png")
            compose.onNodeWithText("+ Add manually").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("Ingredient")).performTextReplacement(title)
            compose.onNode(hasSetTextAction() and hasText("Amount (optional)")).performTextReplacement("3 tomatoes")
            compose.onNodeWithText("Save ingredient").performClick()
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            val saved = store.read().first { it.name == title }
            assertEquals("3 tomatoes", PantryStore(context).read().first { it.id == saved.id }.amount)
            screenshot("pantry-page-filled.png")
            compose.onNodeWithText(title).performClick()
            compose.onNode(hasSetTextAction() and hasText("Amount (optional)")).performTextReplacement("2 tomatoes")
            compose.onNodeWithText("Save ingredient").performClick()
            assertEquals("2 tomatoes", store.read().first { it.id == saved.id }.amount)
            compose.onNodeWithContentDescription("Remove $title").performScrollTo().performClick()
            compose.onNodeWithText(title).assertDoesNotExist()
            assertFalse(store.read().any { it.id == saved.id })
            compose.onNodeWithText("← Back").performScrollTo().performClick()
            compose.onNodeWithText("What’s for dinner?").assertIsDisplayed()
        } finally { store.write(store.read().filterNot { it.name == title }) }
    }
}
