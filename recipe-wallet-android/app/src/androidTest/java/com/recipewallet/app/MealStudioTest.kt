package com.recipewallet.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MealStudioTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun vm()=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
 private fun screenshot(name:String){compose.waitForIdle();val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(compose.activity.getExternalFilesDir(null),name).outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}
 @org.junit.Before fun resetLocalProfile() {
  InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("meal-studio-v1",android.content.Context.MODE_PRIVATE).edit().clear().commit()
  compose.activityRule.scenario.recreate()
 }
 @Test fun profileAndPantryCarryIntoBasketAndSurviveRecreation(){
  compose.runOnIdle {vm().studio.updateMood(1);vm().studio.updatePeople(4);if("garlic" !in vm().studio.owned)vm().studio.toggleOwned("garlic")}
  compose.onNodeWithText("Use my taste profile ✨").performScrollTo().performClick()
  compose.onNode(hasSetTextAction()).assertTextContains("for 4 people")
  compose.onNode(hasSetTextAction()).assertTextContains("Garlic")
  compose.onNodeWithText("Build manually").performScrollTo().performClick()
  compose.onNodeWithText("Spicy garlic noodles").performScrollTo().performClick()
  compose.runOnIdle{assertEquals(4,vm().servings);assertFalse("garlic" in vm().selected);vm().restart()}
  compose.activityRule.scenario.recreate()
  compose.runOnIdle{assertEquals(4,vm().studio.people);assertTrue("garlic" in vm().studio.owned);vm().studio.toggleOwned("garlic");vm().studio.updatePeople(2);vm().studio.updateMood(0)}
 }
 @Test fun weeklyQuantitiesCombineFiveMealsAndSwapsUpdateTheList(){
  compose.onNodeWithText("My week").performScrollTo().performClick()
  compose.onNodeWithText("Build a local catalog plan instead").performScrollTo().performClick()
  compose.runOnIdle {
   val s=vm().studio;assertEquals(5,s.weeklyIds.size)
   val before=s.weeklyIds;s.swapDay(0);assertNotEquals(before,s.weeklyIds)
   val recipe=s.weeklyRecipe()!!
   val expected=s.weeklyIds.flatMap{id->vm().catalog.recipes.first{it.id==id}.let{r->r.ingredients.map{i->i.id to kotlin.math.ceil(i.quantity.toDouble()*s.people/r.baseServings).toLong()}}}.groupBy{it.first}.mapValues{(_,items)->items.sumOf{it.second}}
   assertEquals(expected,recipe.ingredients.associate{it.id to it.quantity})
  }
  screenshot("studio-week.png")
 }
 @Test fun pantryAndManualScreensAreReachable(){
  screenshot("studio-home.png")
  compose.onNodeWithText("My pantry").performScrollTo().performClick()
  compose.onNodeWithText("Meet your ingredients.").assertExists()
  screenshot("studio-pantry.png")
  compose.onNodeWithText("Build manually").performScrollTo().performClick()
  compose.onNodeWithText("Next mood →").performScrollTo().performClick()
  screenshot("studio-manual.png")
  compose.onNodeWithText("Spicy garlic noodles").performScrollTo().performClick()
  compose.onNodeWithText("Compare complete baskets →").performScrollTo().performClick()
  compose.onNodeWithText("Find your basket").assertExists()
  screenshot("studio-map.png")
  compose.onNodeWithText("Review checkout →").performScrollTo().performClick()
  compose.onNodeWithText("Preview confirmation without payment").performScrollTo().performClick()
  compose.onNodeWithText("No payment was made.").assertExists()
 }
}
