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
 @Test fun liveTestCardPaymentIsServerVerified() {
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveAgent")=="true")
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val fixture=JSONObject(instrumentation.context.assets.open("agent-plan.json").bufferedReader().use {it.readText()})
  lateinit var vm: WalletViewModel
  lateinit var directory: File
  activityRule.scenario.onActivity {activity->
   directory=activity.getExternalFilesDir(null)!!
   vm=ViewModelProvider(activity)[WalletViewModel::class.java]
   vm.agentPlan=fixture.getJSONObject("plan");vm.agentReply="Review this demo basket.";vm.approvePlan();activity.startTestCheckout()
  }
  val device=UiDevice.getInstance(instrumentation)
  val ready=device.wait(Until.hasObject(By.text("Card information")),40000)
  File(directory,"stripe-native.xml").outputStream().use {device.dumpWindowHierarchy(it)}
  File(directory,"stripe-native.png").outputStream().use {instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
  assertTrue("Stripe card form did not load",ready)
  device.findObject(By.desc("Close sheet")).click()
  device.waitForIdle()
  activityRule.scenario.onActivity {activity->
   assertTrue(vm.error?.contains("canceled")==true)
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
  device.findObject(By.text("Pay $7.79")).click()
  assertTrue("Verified test receipt did not appear",device.wait(Until.hasObject(By.text("STRIPE TEST PAYMENT VERIFIED")),45000))
  activityRule.scenario.onActivity {assertTrue(vm.paymentVerified);assertEquals(779L,vm.receipt!!.charge)}
  File(directory,"stripe-receipt.png").outputStream().use {instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
 }
}
