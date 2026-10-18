package com.recipewallet.app
import org.junit.Assert.*
import org.junit.Test
class DietaryRulesTest {
 @Test fun aliasesCatchKnownCatalogIngredients(){assertEquals(listOf("Wheat noodles","Soy sauce","Sesame oil","Parmesan"),excludedIngredientNames(listOf("Wheat noodles","Soy sauce","Sesame oil","Parmesan","Lemon")," GLUTEN, milk, sesame "))}
 @Test fun customExclusionsAreCaseInsensitiveAndCommaSeparated(){assertEquals(listOf("Fresh basil","Cherry tomatoes"),excludedIngredientNames(listOf("Fresh basil","Cherry tomatoes","Brown rice"),"basil, TOMATO, ,"))}
 @Test fun cuisinesUseExplicitCatalogMappings(){assertEquals("Italian",recipeCuisine("pasta"));assertEquals("Asian",recipeCuisine("noodles"));assertEquals("Mediterranean",recipeCuisine("bowl"));assertEquals("Other",recipeCuisine("custom"))}
 @Test fun emptyExclusionsDoNotExcludeIngredients(){assertTrue(excludedIngredientNames(listOf("Milk","Peanuts")," , ").isEmpty())}
}
