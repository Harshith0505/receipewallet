package com.recipewallet.app

import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import android.graphics.Bitmap

/** Native UI test: PaymentSheet owns a separate Activity, outside the Compose test clock. */
class StripeCheckoutTest {
 @get:Rule val activityRule=ActivityScenarioRule(MainActivity::class.java)
 @Test fun liveTestCardPaymentIsServerVerified() { runPayment(false) }
 @Test fun liveSavedRecipePaymentIsServerVerified() { runPayment(true) }
 private fun runPayment(saved:Boolean) {
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveAgent")=="true")
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val fixture=JSONObject(instrumentation.context.assets.open("agent-plan.json").bufferedReader().use {it.readText()})
  lateinit var vm: WalletViewModel
  lateinit var directory: File
  activityRule.scenario.onActivity {activity->
   directory=activity.getExternalFilesDir(null)!!
   vm=ViewModelProvider(activity)[WalletViewModel::class.java]
   if(saved) {
    vm.choosePersonal(PersonalRecipe(java.util.UUID.randomUUID().toString(),"Saved test pasta",listOf("200 g pasta","100 g cheddar")))
    vm.compare();vm.checkout(vm.storeId)
   } else {
    vm.agentPlan=fixture.getJSONObject("plan");vm.agentReply="Review this demo basket.";vm.approvePlan();vm.checkout(vm.storeId)
   }
   activity.startTestCheckout()
  }
  val device=UiDevice.getInstance(instrumentation)
  val ready=device.wait(Until.hasObject(By.text("Card information")),40000)
  val flow=if(saved) "saved" else "catalog"
  File(directory,"stripe-$flow-native.xml").outputStream().use {device.dumpWindowHierarchy(it)}
  File(directory,"stripe-$flow-native.png").outputStream().use {instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
  activityRule.scenario.onActivity {
   assertTrue("Stripe card form did not load: busy=${vm.busy}, error=${vm.error}",ready)
  }
  device.findObject(By.desc("Close sheet")).click()
  val cancelDeadline=android.os.SystemClock.elapsedRealtime()+10000
  var canceled=false
  while(!canceled && android.os.SystemClock.elapsedRealtime()<cancelDeadline) {
   activityRule.scenario.onActivity { canceled=!vm.busy && vm.error?.contains("canceled")==true }
   if(!canceled) android.os.SystemClock.sleep(100)
  }
  activityRule.scenario.onActivity {activity->
   assertTrue("Cancellation callback missing: busy=${vm.busy}, error=${vm.error}",canceled)
   assertEquals(3,vm.step)
   assertEquals(if(saved) 554L else 1580L,vm.current.charge)
   assertFalse("garlic" in vm.selected)
   // An uncertain callback must hold the basket and prevent another charge.
   vm.paymentFailed("Simulated connection interruption")
   assertTrue(vm.awaitingVerification)
   vm.switchCard("black");vm.back();vm.walkthrough()
   vm.changeServings(4);vm.changeFulfillment("delivery");vm.changeOrganicOnly(true)
   assertEquals(2,vm.servings);assertEquals("pickup",vm.fulfillment);assertFalse(vm.organicOnly)
   vm.pay { _,_->fail("A second checkout opened before verification") }
   assertEquals("green",vm.cardId);assertEquals(3,vm.step)
   vm.verify()
  }
  // Checkout is scrolled to the top after closing the sheet; the status text is below it.
  val deadline=android.os.SystemClock.elapsedRealtime()+30000
  var resolved=false
  while(!resolved && android.os.SystemClock.elapsedRealtime()<deadline) {
   activityRule.scenario.onActivity { resolved=!vm.busy && !vm.awaitingVerification }
   if(!resolved) android.os.SystemClock.sleep(100)
  }
  assertTrue("An unpaid intent should unlock checkout after verification",resolved)
  activityRule.scenario.onActivity {activity->
   assertFalse(vm.awaitingVerification)
   assertTrue(vm.error?.contains("No test payment completed.")==true)
   activity.startTestCheckout()
  }
  assertTrue(device.wait(Until.hasObject(By.text("Card information")),40000))
  device.findObject(By.clazz("android.widget.EditText").hasDescendant(By.text("Card number"))).text="4242424242424242"
  device.waitForIdle()
  device.findObject(By.clazz("android.widget.EditText").hasDescendant(By.descStartsWith("Expiration date."))).text="1230"
  device.waitForIdle()
  device.findObject(By.clazz("android.widget.EditText").hasDescendant(By.text("CVC"))).text="123"
  device.waitForIdle()
  device.findObject(By.clazz("android.widget.EditText").hasDescendant(By.text("ZIP Code"))).text="98105"
  device.waitForIdle()
  device.findObject(By.text("Pay ${if(saved) "$5.54" else "$15.80"}")).click()
  assertTrue("Verified test receipt did not appear",device.wait(Until.hasObject(By.text("STRIPE TEST PAYMENT VERIFIED")),45000))
  activityRule.scenario.onActivity {
   assertTrue(vm.paymentVerified);assertEquals(if(saved) 554L else 1580L,vm.receipt!!.charge)
   assertEquals(if(saved) "Saved test pasta" else "Spicy garlic noodles",vm.receiptRecipeName)
   assertEquals(if(saved) 2 else 5,vm.receiptIngredients.size)
   assertEquals(if(saved) 2 else 5,if(saved) vm.receipt!!.customItems.size else vm.receipt!!.items.size);assertEquals("pickup",vm.receipt!!.fulfillment)
   assertFalse(vm.receiptIngredients.any { it.id=="garlic" })
   assertTrue(vm.receiptPaymentId!!.startsWith("pi_"))
  }
  File(directory,if(saved) "stripe-saved-receipt.png" else "stripe-receipt.png").outputStream().use {instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
 }
}
