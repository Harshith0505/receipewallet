package com.recipewallet.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class RecipeLibraryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun personalRecipeCanBeSavedReopenedAndEdited() {
        val store = PersonalRecipeStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val title = "Lemon noodles " + UUID.randomUUID().toString().take(8)
        var id: String? = null
        try {
            compose.onNodeWithText("Build manually").performClick()
            compose.onNodeWithText("+ Add a recipe").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("Recipe name")).performTextReplacement(title)
            compose.onNode(hasSetTextAction() and hasText("Ingredients")).performScrollTo()
                .performTextReplacement("200 g noodles\n1 lemon\n2 garlic cloves")
            compose.onNodeWithText("Save recipe").performScrollTo().performClick()
            compose.onNodeWithText("Saved recipes").assertIsDisplayed()
            val saved = store.read().first { it.name == title }
            id = saved.id
            assertEquals(3, saved.ingredients.size)
            // A new store instance must load the same recipe from disk.
            assertEquals(saved, PersonalRecipeStore(InstrumentationRegistry.getInstrumentation().targetContext).read().first { it.id == saved.id })
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithText("1 lemon").assertIsDisplayed()
            compose.onNodeWithText("Edit recipe").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("Ingredients")).performScrollTo()
                .performTextReplacement("200 g noodles\n2 lemons\n2 garlic cloves")
            compose.onNodeWithText("Save changes").performScrollTo().performClick()
            assertTrue(store.read().first { it.id == saved.id }.ingredients.contains("2 lemons"))
            assertEquals(1, store.read().count { it.id == saved.id })
        } finally {
            id?.let(store::remove)
        }
    }
}
