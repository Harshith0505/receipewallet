package com.recipewallet.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class WeeklySwapFlowTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private fun state()=ViewModelProvider(compose.activity)[WalletViewModel::class.java].studio
 @Before fun cleanProfile(){
  InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("meal-studio-v1",0).edit().clear().commit()
  compose.activityRule.scenario.recreate()
  compose.runOnIdle{state().updateCuisine("Mexican");state().updateVegetarian(true);state().updateAvoid("");state().updateMinutes(35f);state().buildLocalWeek();state().updateAutoCart(true)}
 }
 @Test fun aSingleMatchingRecipeOffersAnExplicitOneDinnerCuisineChoice(){
  var original=emptyList<String>();var replacement="";var name="";var monday=""
  compose.runOnIdle{original=state().weeklyIds;assertEquals(List(5){"mexican-rice"},original);val r=state().swapCandidates(0,true).first();replacement=r.id;name=r.name;monday=state().currentWeek}
  compose.onNodeWithText("My week").performScrollTo().performClick()
  compose.onNodeWithContentDescription("$monday dinner planned").performScrollTo().performClick()
  compose.onNodeWithText("↻ Swap dinner").performScrollTo().performClick()
  compose.onNodeWithText("Choose a different dinner").assertExists()
  compose.runOnIdle{assertEquals(original,state().weeklyIds)}
  compose.onNodeWithText("Show other cuisines for this dinner").performScrollTo().performClick()
  compose.onNodeWithText("Choose $name").performScrollTo().performClick()
  compose.runOnIdle{
   assertEquals(replacement,state().weeklyIds[0]);assertEquals(original.drop(1),state().weeklyIds.drop(1))
   assertEquals("Mexican",state().cuisine);assertTrue(state().vegetarian)
   assertEquals(state().weeklyIds,state().cartIds)
   assertTrue(state().swapNotice!!.contains(name))
  }
  compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  compose.waitForIdle();instrumentation.waitForIdleSync();Thread.sleep(450) // Allow the native dialog exit to finish before visual capture.
  java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"weekly-swap.png").outputStream().use{instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  compose.activityRule.scenario.recreate()
  compose.runOnIdle{assertEquals(replacement,state().weeklyIds[0]);assertEquals("Mexican",state().cuisine);assertEquals(state().weeklyIds,state().cartIds)}
 }
 @Test fun cancellingTheLimitedChoiceDialogKeepsTheWeekAndCart(){
  var original=emptyList<String>();compose.runOnIdle{original=state().weeklyIds}
  compose.onNodeWithText("My week").performScrollTo().performClick()
  compose.onNodeWithText("↻ Swap dinner").performScrollTo().performClick()
  compose.onNodeWithText("Keep current dinner").performClick()
  compose.runOnIdle{assertEquals(original,state().weeklyIds);assertEquals(original,state().cartIds)}
 }
 @Test fun cuisineExceptionsNeverBypassDietaryOrTimeFilters(){
  compose.runOnIdle{
   val s=state();val before=s.weeklyIds
   assertFalse(s.chooseDinner(0,"noodles"))
   s.updateAvoid("soy");assertFalse(s.chooseDinner(0,"noodles",true))
   assertFalse(s.chooseDinner(0,"pasta",true))
   s.updateMinutes(20f);assertFalse(s.chooseDinner(0,"chana",true))
   assertTrue(s.swapCandidates(0,true).all{it.prepTime<=20&&s.fits(it)&&it.id!="pasta"})
   assertEquals(before,s.weeklyIds);assertEquals(before,s.cartIds)
  }
 }
}
