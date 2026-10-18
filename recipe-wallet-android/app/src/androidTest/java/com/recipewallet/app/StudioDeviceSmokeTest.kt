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
 private fun click(text:String){find(text).click();device.waitForIdle()}
 private fun shot(name:String){device.waitForIdle();device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null),name))}
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
  assertTrue(device.hasObject(By.text("Find your basket")));shot("studio-map.png")
  click("Review checkout →");click("Preview confirmation without payment")
  assertTrue(device.hasObject(By.text("No payment was made.")));shot("studio-receipt.png")
 }
}
