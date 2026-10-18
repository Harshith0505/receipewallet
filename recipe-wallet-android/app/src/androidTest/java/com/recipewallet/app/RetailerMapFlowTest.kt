package com.recipewallet.app

import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.util.concurrent.atomic.AtomicInteger

class RetailerMapFlowTest {
 @get:Rule val compose=createComposeRule()
 private fun shopping()=RetailerViewModel{path,payload->
  if(path.endsWith("stores"))JSONObject("""{"stores":[{"id":"s1","name":"First QFC","address":"Seattle","latitude":47.6622899,"longitude":-122.2964227},{"id":"s2","name":"Second QFC","address":"Wallingford","latitude":47.6618635,"longitude":-122.3359107}]}""")
  else JSONObject("""{"storeId":"${payload.getString("storeId")}","currency":"usd","fetchedAt":"now","products":[{"productId":"p1","itemId":"i1","name":"Pasta","packageSize":"500 g","regularPriceCents":${if(payload.getString("storeId")=="s1")100 else 300},"stockLevel":"HIGH","soldBy":"UNIT"}]}""")
 }
 private fun show(vm:RetailerViewModel){
  compose.setContent{MaterialTheme{LiveShoppingScreen("map-meal","Pasta",listOf(RetailerNeed("pasta","Pasta","300 g",300.0,"g")),emptyList(),2000,{}, {},vm)}}
  compose.waitUntil(10000){!vm.busy&&vm.selections.isNotEmpty()}
 }
 @Test fun storeListUnderTheMapRefreshesPricesAndClearsPackageApproval(){
  val vm=shopping();show(vm)
  compose.runOnIdle{assertTrue(vm.selectedStore!!.hasCoordinates);vm.confirmPackages(true)}
  compose.onNodeWithText("2. Second QFC").performScrollTo().performClick()
  compose.waitUntil(10000){!vm.busy&&vm.selectedStore?.id=="s2"}
  compose.runOnIdle{assertEquals(300L,vm.selections.getValue("pasta").product.price);assertFalse(vm.packagesReviewed);assertNull(vm.basket)}
  compose.onNodeWithText("Directions to selected store").assertExists()
 }
 @Test fun renderedMapPinsSelectTheCorrespondingLiveStore(){
  assumeTrue(InstrumentationRegistry.getArguments().getString("liveMap")=="true")
  val vm=shopping();show(vm)
  compose.onNodeWithContentDescription("Map of nearby grocery stores").performScrollTo().assertIsDisplayed()
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  var web:WebView?=null
  instrumentation.runOnMainSync{
   fun find(view:android.view.View):WebView?{
    if(view is WebView)return view
    if(view is android.view.ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let{return it}
    return null
   }
   val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
   web=find(activity.window.decorView)
  }
  assertNotNull(web)
  val count=AtomicInteger(0)
  compose.waitUntil(40000){
   instrumentation.runOnMainSync{web!!.evaluateJavascript("document.getElementById('map').getBoundingClientRect().height >= 290 ? document.querySelectorAll('.leaflet-marker-icon').length : 0"){count.set(it.toIntOrNull()?:0)}}
   count.get()==2
  }
  compose.runOnIdle{vm.confirmPackages(true)}
  instrumentation.runOnMainSync{web!!.evaluateJavascript("document.querySelectorAll('.leaflet-marker-icon')[1].click()",null)}
  compose.waitUntil(10000){!vm.busy&&vm.selectedStore?.id=="s2"}
  compose.runOnIdle{assertEquals(300L,vm.selections.getValue("pasta").product.price);assertFalse(vm.packagesReviewed)}
  compose.onNodeWithContentDescription("Map of nearby grocery stores").performScrollTo()
  val tiles=AtomicInteger(0)
  compose.waitUntil(40000){
   instrumentation.runOnMainSync{web!!.evaluateJavascript("Array.from(document.querySelectorAll('.leaflet-tile')).filter(t=>t.complete && t.naturalWidth>0).length"){tiles.set(it.toIntOrNull()?:0)}}
   tiles.get()>0
  }
  instrumentation.waitForIdleSync()
  java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"live-store-map.png").outputStream().use{instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
 }
}
