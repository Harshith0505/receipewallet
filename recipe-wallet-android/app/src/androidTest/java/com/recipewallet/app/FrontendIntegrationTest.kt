package com.recipewallet.app

import android.graphics.BitmapFactory
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class FrontendIntegrationTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @org.junit.Before fun keepTabletAwake(){
  compose.runOnUiThread{compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
  compose.waitUntil(25000){runCatching{compose.onAllNodesWithText("Build with AI").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false)}
 }
 @Test fun detailedPantryAndRecipeChecksShareConfirmedOwnership(){
  val store=PantryStore(InstrumentationRegistry.getInstrumentation().targetContext)
  val initial=store.read()
  lateinit var state:MealStudioState
  var oldOwned=emptySet<String>()
  try {
   compose.runOnIdle{
    state=ViewModelProvider(compose.activity)[WalletViewModel::class.java].studio;oldOwned=state.owned
    state.confirmOwnedIds(state.owned+"rice")
    assertTrue(store.read().any{it.name==state.ingredients.first{it.id=="rice"}.name})
    store.write(store.read().filterNot{it.name==state.ingredients.first{it.id=="rice"}.name})
    state.importSavedPantry();assertFalse("rice" in state.owned)
   }
  } finally {store.write(initial);compose.runOnIdle{state.replaceOwned(oldOwned)}}
 }
 @Test fun comparisonOpensTheLiveBasketWithPantryExclusions(){
  lateinit var vm:WalletViewModel
  compose.runOnIdle{
   vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
   vm.studio.updateAvoid("");vm.choose("noodles");vm.selected=vm.selected-"garlic";vm.compare()
   assertEquals(2,vm.step)
  }
  compose.onNodeWithText("Check live retailer prices").performScrollTo().performClick()
  compose.onNodeWithText("US ZIP code").assertExists()
  compose.runOnIdle{
   val retailer=ViewModelProvider(compose.activity)[RetailerViewModel::class.java]
   assertEquals(vm.selected,retailer.needs.map{it.id}.toSet());assertFalse(retailer.needs.any{it.id=="garlic"})
  }
 }
 @Test fun liveStudioPhotoUsesBackendAndOnlyConfirmedSuggestionsAffectThePantry(){
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveStudio")=="true")
  lateinit var state:MealStudioState
  var initial=emptySet<String>()
  val store=PantryStore(InstrumentationRegistry.getInstrumentation().targetContext)
  val initialItems=store.read()
  compose.runOnIdle{
   val vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
   state=vm.studio;initial=state.owned
   state.scan(BitmapFactory.decodeStream(InstrumentationRegistry.getInstrumentation().context.assets.open("sample-fridge.jpg")))
  }
  try{
   compose.waitUntil(90000){!state.scanBusy}
   compose.runOnIdle{assertNull(state.scanError);assertTrue(state.scanDetections.isNotEmpty());assertEquals(initial,state.owned)}
   compose.onNodeWithText("My pantry").performScrollTo().performClick()
   compose.onNodeWithText("Review your ingredients").performScrollTo().assertIsDisplayed()
   val selected=state.scanDetections.filter{it.selected}.map{it.item}
   compose.onNodeWithText("Save ${selected.size} to pantry").performScrollTo().performClick()
   compose.runOnIdle{assertTrue(selected.all { item->store.read().any{it.name==item.name} });assertEquals(state.owned,ViewModelProvider(compose.activity)[WalletViewModel::class.java].pantry)}
  }finally{compose.runOnIdle{store.write(initialItems);state.replaceOwned(initial);state.clearPhoto()}}
 }
 @Test fun liveWeeklyPlanReachesTheFrontendCalendar(){
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveStudio")=="true")
  lateinit var state:MealStudioState
  compose.runOnIdle{state=ViewModelProvider(compose.activity)[WalletViewModel::class.java].studio;state.updateCuisine("Any");state.updateAvoid("");state.updateMinutes(45f);state.buildWeek()}
  compose.waitUntil(60000){!state.weekBusy}
  compose.runOnIdle{assertNull(state.weekError);assertEquals(5,state.weeklyIds.size);assertEquals("gemini",state.weekSource)}
  compose.onNodeWithText("My week").performScrollTo().performClick()
  compose.onNodeWithText("Your weekly plan").performScrollTo().assertIsDisplayed()
 }
 @Test fun livePizzaDraftReachesEditableIngredientsAndRetailerSearch(){
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveStudio")=="true")
  lateinit var vm:WalletViewModel
  compose.runOnIdle{vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java];vm.studio.updateCuisine("Any");vm.studio.updateAvoid("")}
  compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Homemade pizza for four under $30. I already have flour and salt.")
  compose.onNodeWithText("Build my meal").performScrollTo().performClick()
  compose.waitUntil(95000){!vm.aiBusy}
  compose.runOnIdle{assertNull(vm.aiError);assertNotNull(vm.generatedRecipe);assertTrue(vm.generatedRecipe!!.getString("name").contains("pizza",true));assertNull(vm.agentPlan)}
  compose.onNodeWithText("Review ingredients →").performScrollTo().performClick()
  compose.runOnIdle{assertTrue(vm.isPersonal);assertTrue(vm.recipe.name.contains("pizza",true));assertEquals(1,vm.step);assertEquals(3000L,vm.planBudget)}
  compose.onNodeWithText("Look up retailer prices").performScrollTo().performClick()
  compose.onNodeWithText("US ZIP code").assertExists()
 }

}
