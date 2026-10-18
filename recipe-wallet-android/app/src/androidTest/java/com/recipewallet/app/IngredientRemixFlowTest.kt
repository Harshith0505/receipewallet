package com.recipewallet.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IngredientRemixFlowTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun remixChangesTheOpenShoppingListAndKeepsTheSameMeal(){
  lateinit var vm:WalletViewModel
  val fixture=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("agent-plan.json").bufferedReader().use{it.readText()})
  compose.runOnIdle{
   compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
   vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
   vm.newPlan();vm.agentPlan=fixture.getJSONObject("plan");vm.agentReply="Your meal is ready.";vm.planningGoal="surprise"
  }
  compose.onNodeWithText("01  To buy").performScrollTo().performClick()
  compose.onNodeWithText("+ Chili flakes").assertExists()
  compose.onNodeWithText("03  Ingredient remix").performScrollTo().performClick()
  compose.onNodeWithText("Mild · remove chili").performScrollTo().assertIsDisplayed().performClick()
  compose.waitForIdle()
  compose.waitUntil(20000){!vm.aiBusy}
  compose.runOnIdle{
   assertNull(vm.aiError);assertEquals("noodles",vm.plannedRecipe!!.id)
   assertEquals("balanced",vm.planningGoal)
   assertFalse("Expected chili removed: ${vm.agentPlan}","chili" in vm.planIds("ingredientIds"));assertTrue("chili" in vm.planIds("omittedIngredientIds"));assertFalse("Omitted chili must not be owned: ${vm.agentPlan}","chili" in vm.planIds("ownedIngredientIds"))
  }
  compose.onNodeWithText("+ Chili flakes").assertDoesNotExist()
  compose.onNodeWithText("+ Wheat noodles").assertExists() // Open section survives the update.
  compose.onNodeWithText("Spicy · include chili").performScrollTo().performClick()
  compose.waitUntil(20000){!vm.aiBusy}
  compose.runOnIdle{assertNull(vm.aiError);assertTrue("chili" in vm.planIds("ingredientIds"));assertEquals("noodles",vm.plannedRecipe!!.id)}
  compose.onNodeWithText("+ Chili flakes").assertExists()
  compose.onNodeWithContentDescription("Include Soy sauce in recipe").performScrollTo().performClick()
  compose.waitUntil(20000){!vm.aiBusy}
  compose.onNodeWithText("+ Soy sauce").assertDoesNotExist()
  compose.runOnIdle{assertNull(vm.aiError);vm.approvePlan();assertFalse("soy" in vm.selected);assertTrue("soy" in vm.omittedIngredientIds);vm.back()}
  compose.onNodeWithText("Left out of recipe: Soy sauce").assertExists()
 }
 @Test fun rejectedRevisionKeepsTheCurrentPlanVisible(){
  lateinit var vm:WalletViewModel
  val fixture=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("agent-plan.json").bufferedReader().use{it.readText()})
  compose.runOnIdle{vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java];vm.newPlan();vm.agentPlan=fixture.getJSONObject("plan");vm.agentReply="Your meal is ready."}
  compose.onNodeWithText("01  To buy").performScrollTo().performClick()
  compose.runOnIdle{vm.remix("omit","not-a-recipe-ingredient")}
  compose.waitUntil(20000){!vm.aiBusy}
  compose.runOnIdle{assertNotNull(vm.aiError);assertEquals("noodles",vm.plannedRecipe!!.id);assertTrue("chili" in vm.planIds("ingredientIds"))}
  compose.onNodeWithText("+ Chili flakes").assertExists()
 }
 @Test fun pantryRemixCanEmptyTheShoppingListWithoutLosingTheMeal(){
  val store=PantryStore(InstrumentationRegistry.getInstrumentation().targetContext)
  val initial=store.read()
  lateinit var vm:WalletViewModel
  var oldOwned=emptySet<String>()
  val fixture=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("agent-plan.json").bufferedReader().use{it.readText()})
  try {
   compose.runOnIdle{
    vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java];oldOwned=vm.studio.owned
    vm.studio.confirmOwnedIds(oldOwned+vm.catalog.recipes.first{it.id=="noodles"}.ingredients.map{it.id})
    vm.newPlan();vm.agentPlan=fixture.getJSONObject("plan");vm.agentReply="Your meal is ready."
   }
   compose.onNodeWithText("03  Ingredient remix").performScrollTo().performClick()
   compose.onNodeWithText("Use my pantry").performScrollTo().performClick()
   compose.waitUntil(20000){!vm.aiBusy}
   compose.runOnIdle{assertNull(vm.aiError);assertEquals("noodles",vm.plannedRecipe!!.id);assertTrue(vm.planIds("ingredientIds").isEmpty());assertEquals(0L,vm.plannedQuote!!.charge)}
   compose.onNodeWithText("Nothing to buy · your pantry covers this meal").assertExists()
   compose.onNodeWithText("Review basket →").performScrollTo().performClick()
   compose.onNodeWithText("Your pantry covers this meal").assertExists()
  }finally{store.write(initial);compose.runOnIdle{vm.studio.replaceOwned(oldOwned)}}
 }

}
