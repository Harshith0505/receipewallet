package com.recipewallet.app

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// UIAutomator path also works on the API 37 preview where the bundled Espresso
// dependency still calls a removed InputManager method. No live AI/payment calls.
@RunWith(AndroidJUnit4::class)
class StudioDeviceSmokeTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val device=UiDevice.getInstance(instrumentation)
 private fun top(){repeat(7){device.swipe(device.displayWidth*3/4,device.displayHeight/5,device.displayWidth*3/4,device.displayHeight*85/100,12)}}
 private fun find(text:String):androidx.test.uiautomator.UiObject2{
  var item=device.findObject(By.text(text))
  if(item==null){top();item=device.findObject(By.text(text))}
  var tries=0
  while(item==null&&tries++<12){device.swipe(device.displayWidth*3/4,device.displayHeight*85/100,device.displayWidth*3/4,device.displayHeight/4,15);device.waitForIdle();item=device.findObject(By.text(text))}
  assertTrue("Missing control: $text",item!=null);return item!!
 }
 private fun click(text:String){find(text).click();android.os.SystemClock.sleep(300);device.waitForIdle()}
 private fun shot(name:String){android.os.SystemClock.sleep(if(name=="studio-map.png")3000 else 300);device.waitForIdle();device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null),name))}
 @Test fun pantryWeekAndShoppingPreview(){
  val context=instrumentation.targetContext
  context.getSharedPreferences("meal-studio-v1",android.content.Context.MODE_PRIVATE).edit().clear().commit()
  context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
  assertTrue(device.wait(Until.hasObject(By.text("Build with AI")),15000))
  shot("studio-home.png")
  click("My pantry");assertTrue(device.hasObject(By.text("Meet your ingredients.")));shot("studio-pantry.png")
  click("Garlic")
  click("My week");click("Build a local catalog plan instead");find("MONDAY");shot("studio-week.png")
  click("Build manually");shot("studio-manual.png")
  click("Spicy garlic noodles");click("Compare complete baskets →")
  find("Find your basket");shot("studio-map.png")
  val latch=java.util.concurrent.CountDownLatch(1)
  var mapHeight=0.0
  instrumentation.runOnMainSync {
   fun findWeb(v:android.view.View):android.webkit.WebView? {
    if(v is android.webkit.WebView)return v
    if(v is android.view.ViewGroup)for(i in 0 until v.childCount)findWeb(v.getChildAt(i))?.let{return it}
    return null
   }
   val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull()
   val web=activity?.window?.decorView?.let { findWeb(it) }
   if(web==null){File(context.getExternalFilesDir(null),"map-runtime.txt").writeText("No WebView found");latch.countDown()}
   else web.evaluateJavascript("JSON.stringify({url:location.href,ready:document.readyState,body:document.body&&document.body.innerText,viewport:[innerWidth,innerHeight],rect:document.querySelector('#map')?.getBoundingClientRect().toJSON(),html:document.documentElement.outerHTML.slice(0,100)})") { result->File(context.getExternalFilesDir(null),"map-runtime.txt").writeText(result);mapHeight=org.json.JSONObject(org.json.JSONTokener(result).nextValue().toString()).optJSONObject("rect")?.optDouble("height")?:0.0;latch.countDown() }
  }
  assertTrue(latch.await(5,java.util.concurrent.TimeUnit.SECONDS))
  assertTrue("Embedded street map must have a visible viewport",mapHeight>0.0)
  click("Review checkout →");click("Preview confirmation without payment")
  find("No payment was made.");shot("studio-receipt.png")
 }
}
