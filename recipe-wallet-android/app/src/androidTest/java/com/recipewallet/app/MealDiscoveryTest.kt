package com.recipewallet.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class MealDiscoveryTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun moodChoiceAndDietaryFiltersDriveRecipeCards(){
  val vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java];val state=vm.studio
  val oldCuisine=state.cuisine;val oldMinutes=state.minutes;val oldAvoid=state.avoid;val oldFaith=state.faith;val oldMood=state.mood;val oldPicked=state.pickedMood
  try {
   compose.runOnIdle{state.updateCuisine("Any")}
   compose.onNodeWithText("Build manually").performScrollTo().performClick()
   compose.onNodeWithText("Choose a mood").performScrollTo().performClick()
   val shown=state.mood
   compose.onNodeWithText("The flavor of your day",ignoreCase=true).performScrollTo().performTouchInput{swipeRight()}
   compose.runOnIdle{assertEquals(shown,state.pickedMood);assertEquals((shown+1)%mealMoods.size,state.mood);state.updateAvoid("milk, gluten");state.updateMinutes(45f)}
   compose.runOnIdle{assertTrue(state.rankedRecipes().all{state.conflicts(it).isEmpty()});assertTrue(state.rankedRecipes().isNotEmpty())}
   compose.onNodeWithText("Preferences & dietary needs").performScrollTo().performClick()
   compose.onNodeWithText("Kosher").performSemanticsAction(SemanticsActions.OnClick){it()}
   compose.runOnIdle{assertEquals("Kosher",state.faith)}
   compose.onNodeWithText("Choose a recipe").performScrollTo().assertIsDisplayed()
   compose.onNodeWithText(vm.catalog.recipes.first{it.id=="bowl"}.name).assertExists()
  } finally {compose.runOnIdle{state.updateCuisine(oldCuisine);state.updateMinutes(oldMinutes);state.updateAvoid(oldAvoid);state.updateFaith(oldFaith);state.updateMood(oldPicked);state.chooseMood();state.updateMood(oldMood)}}
 }
}
