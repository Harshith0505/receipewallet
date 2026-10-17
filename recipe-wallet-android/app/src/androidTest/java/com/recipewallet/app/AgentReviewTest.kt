package com.recipewallet.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import android.graphics.Bitmap
import androidx.test.uiautomator.UiDevice
import org.junit.Assume.assumeTrue

class AgentReviewTest {
 @get:Rule val compose = createAndroidComposeRule<MainActivity>()
 private fun showPlan(budget: Long=3000): WalletViewModel {
  val fixture=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("agent-plan.json").bufferedReader().use { it.readText() })
  val plan=fixture.getJSONObject("plan")
  plan.getJSONObject("constraints").put("budgetCents",budget)
  plan.put("withinBudget",budget>=plan.getLong("amount"))
  lateinit var vm: WalletViewModel
  compose.runOnIdle {
   vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
   vm.agentPlan=plan; vm.agentReply=plan.getString("explanation")
   val trace=fixture.getJSONArray("trace")
   vm.agentTrace=(0 until trace.length()).map { trace.getJSONObject(it).getString("summary") }
  }
  return vm
 }
 @Test fun approvedBasketUsesExactReviewedIngredientsAndAmount() {
  val vm=showPlan()
  val expected=vm.plannedQuote!!.charge
  compose.onNodeWithText("Amount charged before rewards").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText(money(vm.plannedQuote!!.charge)).assertExists()
  compose.onNodeWithText("Already owned: Garlic").assertExists()
  compose.onNodeWithText("Review package-priced basket  →").performScrollTo().assertIsEnabled()
  val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(compose.activity.getExternalFilesDir(null),"agent-review.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
  compose.onNodeWithText("Review package-priced basket  →").performClick()
  compose.runOnIdle {
   assertEquals(2,vm.step);assertEquals(expected,vm.current.charge)
   assertFalse(vm.selected.contains("garlic"));assertEquals("market",vm.storeId)
  }
  compose.onNodeWithText("Your meal.\nYour best store.").assertExists()
 }
 @Test fun overBudgetPlanCannotBeApproved() {
  val vm=showPlan(100)
  compose.onNodeWithText("This is above your budget. Revise your request or budget before approving.").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Review package-priced basket  →").performScrollTo().assertIsNotEnabled()
  compose.runOnIdle {vm.approvePlan();assertEquals(0,vm.step)}
 }
 @Test fun liveAgentRevisesAndOpensStripeTestCheckout() {
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveAgent")=="true")
  lateinit var vm: WalletViewModel
  compose.runOnIdle {vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]}
  compose.onNode(hasSetTextAction()).performTextInput("Find me a vegetarian noodle dinner under $30. I already have garlic.")
  compose.onNodeWithText("Plan my shop · AI").performScrollTo().performClick()
  compose.waitUntil(95000) { !vm.aiBusy }
  compose.runOnIdle {assertNull(vm.aiError);assertNotNull(vm.agentPlan);assertTrue(vm.planWithinBudget);assertTrue("garlic" in vm.planIds("ownedIngredientIds"))}
  val original=vm.plannedQuote!!.charge
  compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("I already have soy sauce too.")
  compose.onNodeWithText("Update my plan · AI").performScrollTo().performClick()
  compose.waitUntil(95000) { !vm.aiBusy }
  compose.runOnIdle {assertNull(vm.aiError);assertTrue(vm.plannedQuote!!.charge<original);assertTrue("soy" in vm.planIds("ownedIngredientIds"))}
  compose.onNodeWithText("Review package-priced basket  →").performScrollTo()
  val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(compose.activity.getExternalFilesDir(null),"agent-live-review.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
  compose.onNodeWithText("Review package-priced basket  →").performClick()
  compose.onAllNodesWithText("Review checkout →").onFirst().performScrollTo().performClick()
  compose.onNodeWithText("Pay ${money(vm.current.charge)} · Stripe test").performScrollTo().performClick()
  compose.waitUntil(35000) { vm.canVerify || vm.error!=null }
  compose.runOnIdle {assertNull(vm.error);assertTrue(vm.canVerify)}
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  device.waitForIdle()
  File(compose.activity.getExternalFilesDir(null),"stripe-checkout.xml").outputStream().use { device.dumpWindowHierarchy(it) }
  val sheetImage=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(compose.activity.getExternalFilesDir(null),"stripe-checkout.png").outputStream().use { sheetImage.compress(Bitmap.CompressFormat.PNG,100,it) }
 }
}
