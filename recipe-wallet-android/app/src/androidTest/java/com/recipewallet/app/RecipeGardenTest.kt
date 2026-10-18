package com.recipewallet.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class RecipeGardenTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun moodChoiceAndDietaryFiltersDriveTheGarden(){
  val vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java];val state=vm.studio
  val oldMinutes=state.minutes;val oldAvoid=state.avoid;val oldFaith=state.faith;val oldMood=state.mood;val oldPicked=state.pickedMood
  try {
   compose.onNodeWithText("Build manually").performScrollTo().performClick()
   val shown=state.mood
   compose.onNodeWithText("The flavor of your day",ignoreCase=true).performScrollTo().performTouchInput{swipeRight()}
   compose.runOnIdle{assertEquals(shown,state.pickedMood);state.updateAvoid("milk, gluten");state.updateMinutes(45f)}
   compose.runOnIdle{assertTrue(state.rankedRecipes().all{it.id=="bowl"});assertTrue(state.rankedRecipes().isNotEmpty())}
   compose.onNodeWithText("Kosher").performScrollTo().performClick()
   compose.runOnIdle{assertEquals("Kosher",state.faith)}
   compose.onNodeWithText("Choose a recipe").performScrollTo().assertIsDisplayed()
   compose.onNodeWithText("Recenter recipe garden ↺").performScrollTo().performClick()
  } finally {compose.runOnIdle{state.updateMinutes(oldMinutes);state.updateAvoid(oldAvoid);state.updateFaith(oldFaith);state.updateMood(oldPicked);state.chooseMood();state.updateMood(oldMood)}}
 }
}
