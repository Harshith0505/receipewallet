package com.recipewallet.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import android.view.WindowManager
import android.graphics.Bitmap
import java.io.File

class RecipeLibraryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun cloudAccountIsReachableFromSavedRecipes() {
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.onNodeWithText("Saved recipes ↗").performClick()
        compose.onNodeWithText("Back up & sync").performScrollTo().performClick()
        compose.onNodeWithText("Your recipe library").assertIsDisplayed()
        if (BuildConfig.SUPABASE_URL.isBlank()) compose.onNodeWithText(
            "Cloud sync isn’t connected in this build yet. You can keep adding recipes on this device.").assertIsDisplayed()
        else compose.onNodeWithText("Sign in", useUnmergedTree = true).assertExists()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "cloud-account.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("← Saved recipes").performClick()
        compose.onNodeWithText("Saved recipes").assertIsDisplayed()
    }

    @Test fun personalRecipeCanBeSavedReopenedAndEdited() {
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val store = PersonalRecipeStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val title = "Lemon noodles " + UUID.randomUUID().toString().take(8)
        try {
            compose.onNodeWithText("Build manually").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("+ Add a recipe"))
            compose.onNodeWithText("+ Add a recipe").performClick()
            compose.onNode(hasSetTextAction() and hasText("Recipe name")).performTextReplacement(title)
            compose.onNode(hasSetTextAction() and hasText("Ingredients")).performScrollTo()
                .performTextReplacement("200 g noodles\n1 lemon\n2 garlic cloves")
            compose.onNodeWithText("Save recipe").performScrollTo().performClick()
            compose.waitUntil(5000) { store.read().any { it.name == title } }
            compose.onNodeWithText("Saved recipes").assertIsDisplayed()
            val saved = store.read().first { it.name == title }
            assertEquals(3, saved.ingredients.size)
            // A new store instance must load the same recipe from disk.
            assertEquals(saved, PersonalRecipeStore(InstrumentationRegistry.getInstrumentation().targetContext).read().first { it.id == saved.id })
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithText("1 lemon").assertIsDisplayed()
            compose.onNodeWithText("Edit recipe").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("Ingredients")).performScrollTo()
                .performTextReplacement("200 g noodles\n2 lemons\n2 garlic cloves")
            compose.onNodeWithText("Save changes").performScrollTo().performClick()
            compose.waitUntil(5000) { store.read().first { it.id == saved.id }.ingredients.contains("2 lemons") }
            assertTrue(store.read().first { it.id == saved.id }.ingredients.contains("2 lemons"))
            assertEquals(1, store.read().count { it.id == saved.id })
        } finally {
            store.read().filter { it.name == title }.forEach { store.remove(it.id) }
        }
    }
}
