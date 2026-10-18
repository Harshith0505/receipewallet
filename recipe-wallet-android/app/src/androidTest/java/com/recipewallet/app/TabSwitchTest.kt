package com.recipewallet.app

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class TabSwitchTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @org.junit.Before fun keepDisplayAwakeDuringTest(){compose.runOnUiThread{compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
 @Test fun allDestinationsRemainReachableAndReportSwitchTimes(){
  val state=ViewModelProvider(compose.activity)[WalletViewModel::class.java].studio
  val oldCuisine=state.cuisine;val oldAuto=state.autoWeek
  compose.runOnIdle{state.updateCuisine("Any");state.updateAutoWeek(false)}
  val frames=java.util.concurrent.ConcurrentLinkedQueue<Pair<Int,Long>>()
  compose.runOnIdle{TabSwitchTrace.listener={mode,millis->frames.add(mode to millis)}}
  try {
   repeat(3){round->
    listOf("Build manually" to "Cuisine · Any","My pantry" to "Add pantry items","My week" to "Your dinner calendar","Build with AI" to "What sounds good?").forEach{(tab,marker)->
     val node=compose.onNodeWithText(tab).performScrollTo()
     val started=SystemClock.elapsedRealtime()
     node.performClick()
     compose.onNodeWithText(marker).assertExists()
     compose.waitForIdle()
     val elapsed=SystemClock.elapsedRealtime()-started
     val mode=listOf("Build with AI","Build manually","My pantry","My week").indexOf(tab)
     val frame=frames.lastOrNull{it.first==mode}
     org.junit.Assert.assertNotNull("The selected tab must draw",frame)
     InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply{putString("stream","FIRST_DRAW|$round|$tab|${frame!!.second} ms\n")})
     InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply{putString("stream","TAB_SWITCH|$round|$tab|$elapsed ms\n")})
    }
   }
  }finally{compose.runOnIdle{TabSwitchTrace.clear();state.updateCuisine(oldCuisine);state.updateAutoWeek(oldAuto)}}
 }
 @Test fun deferredRecipesAndWeeklyDetailsRemainReachable(){
  val vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
  val state=vm.studio
  val oldCuisine=state.cuisine;val oldAvoid=state.avoid;val oldMinutes=state.minutes;val oldVegetarian=state.vegetarian;val oldAuto=state.autoWeek
  compose.runOnIdle{state.updateAutoWeek(false);state.updateCuisine("Any");state.updateAvoid("");state.updateMinutes(45f);state.updateVegetarian(false)}
  try {
   compose.onNodeWithText("Build manually").performScrollTo().performClick()
   val lastRecipe=vm.catalog.recipes.last().name
   compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(lastRecipe))
   compose.onNodeWithText(lastRecipe).assertIsDisplayed()
   compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
   compose.onNodeWithText("My pantry").performScrollTo().performClick()
   compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(lastRecipe))
   compose.onNodeWithText(lastRecipe).assertIsDisplayed()
   compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
   compose.onNodeWithText("My week").performScrollTo().performClick()
   compose.onNodeWithText("Your dinner calendar").assertIsDisplayed()
   if(state.weeklyIds.isNotEmpty()){
    compose.onNodeWithContentDescription(state.currentWeek+" dinner planned").performClick()
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Monday",ignoreCase=true))
    compose.onNodeWithText("Monday",ignoreCase=true).assertIsDisplayed()
    compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("One weekly grocery list"))
    compose.onNodeWithText("One weekly grocery list").assertIsDisplayed()
   }
  }finally{compose.runOnIdle{state.updateCuisine(oldCuisine);state.updateAvoid(oldAvoid);state.updateMinutes(oldMinutes);state.updateVegetarian(oldVegetarian);state.updateAutoWeek(oldAuto)}}
 }

}
