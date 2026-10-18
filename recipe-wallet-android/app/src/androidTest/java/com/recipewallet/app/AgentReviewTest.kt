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
 @org.junit.Before fun resetLocalProfile() {
  InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("meal-studio-v1",android.content.Context.MODE_PRIVATE).edit().clear().commit()
  compose.activityRule.scenario.recreate()
 }
 @Test fun approvedBasketUsesExactReviewedIngredientsAndAmount() {
  val vm=showPlan()
  compose.onNodeWithText("Pay today").assertDoesNotExist()
  compose.onNodeWithText("Your shopping total is calculated",substring=true).performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("02  Already owned").performScrollTo().performClick()
  compose.onNodeWithText("✓ Garlic · This meal only").assertExists()
  compose.onNodeWithText("Review basket →").performScrollTo().assertIsEnabled()
  val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(compose.activity.getExternalFilesDir(null),"agent-review.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
  compose.onNodeWithText("Review basket →").performClick()
        compose.runOnIdle { ViewModelProvider(compose.activity)[WalletViewModel::class.java].illustrativeComparison=true }
  compose.runOnIdle {
   assertEquals(2,vm.step);assertEquals(1580L,vm.current.charge)
   assertFalse(vm.selected.contains("garlic"));assertEquals("fresh",vm.storeId)
  }
  compose.onAllNodesWithText("Review checkout →").onFirst().performScrollTo().performClick()
  compose.onNodeWithText("Wheat noodles ·",substring=true).performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Garlic",substring=true).assertDoesNotExist()
  compose.onNodeWithText("Total charged today · test").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("Approve & pay $15.80").performScrollTo().assertIsDisplayed()
 }
 @Test fun previewReceiptNeverClaimsPaymentAndRetainsReviewedItems() {
  val vm=showPlan()
  compose.runOnIdle {vm.approvePlan();vm.checkout(vm.storeId);vm.walkthrough()}
  compose.onNodeWithText("No payment was made.").assertExists()
  compose.runOnIdle {
   assertFalse(vm.paymentVerified);assertNull(vm.receiptPaymentId)
   assertEquals(5,vm.receiptIngredients.size)
   assertFalse(vm.receiptIngredients.any { it.id=="garlic" })
  }
 }
 @Test fun illustrativeBudgetDoesNotBlockReviewOfCurrentRetailerProducts() {
  val vm=showPlan(100)
  compose.onNodeWithText("Review basket →").performScrollTo().assertIsEnabled()
  compose.runOnIdle {vm.approvePlan();assertEquals(2,vm.step)}
 }
 @Test fun deliveryRevisionCannotBypassTheApprovedBudget() {
  val vm=showPlan(1600)
  compose.runOnIdle {
   assertTrue(vm.planWithinBudget)
   vm.approvePlan();vm.changeFulfillment("delivery");vm.checkout("market")
   assertTrue(vm.current.complete);assertTrue(vm.current.charge>1600)
   vm.pay { _,_->fail("An over-budget revised basket reached payment") }
   assertFalse(vm.busy);assertFalse(vm.checkoutWithinBudget)
  }
  compose.onNodeWithText("Approve & pay ${money(vm.current.charge)}").performScrollTo().assertIsNotEnabled()
 }
 @Test fun liveAgentRevisesAndReviewsCheckout() {
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveAgent")=="true")
  lateinit var vm: WalletViewModel
  compose.runOnIdle {vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]}
  compose.onNode(hasSetTextAction()).performTextInput("Find me a vegetarian noodle dinner under $30. I already have garlic.")
  compose.onNodeWithText("Build my meal").performScrollTo().performClick()
  compose.waitUntil(95000) { !vm.aiBusy }
  compose.runOnIdle {assertNull(vm.aiError);assertNotNull(vm.agentPlan);assertTrue(vm.planWithinBudget);assertTrue("garlic" in vm.planIds("ownedIngredientIds"))}
  val original=vm.plannedQuote!!.charge
  compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("I already have soy sauce too.")
  compose.onNodeWithText("Update my meal").performScrollTo().performClick()
  compose.waitUntil(95000) { !vm.aiBusy }
  compose.runOnIdle {assertNull(vm.aiError);assertTrue(vm.plannedQuote!!.charge<original);assertTrue("soy" in vm.planIds("ownedIngredientIds"))}
  compose.onNodeWithText("Review basket →").performScrollTo()
  val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(compose.activity.getExternalFilesDir(null),"agent-live-review.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
  compose.onNodeWithText("Review basket →").performClick()
        compose.runOnIdle { ViewModelProvider(compose.activity)[WalletViewModel::class.java].illustrativeComparison=true }
  compose.onAllNodesWithText("Review checkout →").onFirst().performScrollTo().performClick()
  // Native PaymentSheet is exercised by StripeCheckoutTest without a Compose test clock.
  compose.onNodeWithText("Approve & pay ${money(vm.current.charge)}").performScrollTo().assertIsEnabled()
  compose.runOnIdle { assertNull(vm.error); assertTrue(vm.checkoutWithinBudget) }
 }
}
