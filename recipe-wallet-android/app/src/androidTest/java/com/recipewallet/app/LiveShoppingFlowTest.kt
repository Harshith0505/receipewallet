package com.recipewallet.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveShoppingFlowTest {
 @get:Rule val compose=createComposeRule()
 @Test fun automaticallyMatchesProductsLetsUsersChooseAndRefreshesTheSameBasket(){
  val rows=listOf(
   JSONObject("""{"productId":"p1","itemId":"i1","name":"Pasta regular","packageSize":"16 oz","regularPriceCents":199,"stockLevel":"HIGH","soldBy":"UNIT"}"""),
   JSONObject("""{"productId":"p2","itemId":"i2","name":"Pasta large pack","packageSize":"1 kg","regularPriceCents":399,"stockLevel":"HIGH","soldBy":"UNIT"}"""),
   JSONObject("""{"productId":"p3","itemId":"i3","name":"Tomato sauce","packageSize":"250 ml","regularPriceCents":150,"stockLevel":"HIGH","soldBy":"UNIT"}"""))
  val calls=mutableListOf<String>()
  lateinit var submitted:JSONObject
  val vm=RetailerViewModel{path,payload->
   calls.add(path)
   when(path){
    "/retailers/stores"->JSONObject("""{"stores":[{"id":"s1","name":"Nearby QFC","address":"Seattle"}]}""")
    "/retailers/products"->JSONObject().put("storeId","s1").put("currency","usd").put("fetchedAt","now").put("products",JSONArray(rows.filter{if(payload.getString("query").contains("Pasta",true))it.getString("name").contains("Pasta")else it.getString("name").contains("sauce")}))
    "/retailers/basket"->{
     submitted=payload
     val items=payload.getJSONArray("items")
     val result=List(items.length()){index->val item=items.getJSONObject(index);JSONObject(rows.first{it.getString("productId")==item.getString("productId")}.toString()).put("quantity",item.getInt("quantity")).apply{put("lineSubtotalCents",getLong("regularPriceCents")*getInt("quantity"))}}
     JSONObject().put("storeId","s1").put("currency","usd").put("pricingScope","item-subtotal-only").put("fetchedAt","now").put("quoteToken","signed-test-quote").put("items",JSONArray(result)).put("subtotalCents",result.sumOf{it.getLong("lineSubtotalCents")})
    }
    else->error("Unexpected request: $path")
   }
  }
  compose.setContent{MaterialTheme{LiveShoppingScreen("meal","Dinner",listOf(RetailerNeed("pasta","Pasta","600 g",600.0,"g"),RetailerNeed("sauce","Tomato sauce","300 ml",300.0,"ml")),emptyList(),1000,{}, {},vm)}}
  compose.waitUntil(10000){!vm.busy&&vm.selections.size==2}
  assertEquals(2,vm.selections.getValue("pasta").quantity)
  assertEquals(2,vm.selections.getValue("sauce").quantity)
  assertEquals(1,calls.count{it=="/retailers/stores"});assertEquals(2,calls.count{it=="/retailers/products"})
  compose.onNodeWithText("Check live retailer prices").assertDoesNotExist()
  compose.onAllNodesWithText("Change product").onFirst().performScrollTo().performClick()
  compose.onNodeWithContentDescription("Select Pasta large pack for Pasta").performScrollTo().performClick()
  compose.onNodeWithText("Review live basket →").performScrollTo().assertIsNotEnabled()
  compose.onNodeWithContentDescription("Review item sizes and quantities").performScrollTo().performClick()
  compose.onNodeWithText("Review live basket →").performScrollTo().performClick()
  compose.waitUntil(10000){vm.reviewing&&!vm.busy}
  compose.onNodeWithText("Review your grocery basket").assertExists()
  compose.runOnIdle{
   assertEquals(699L,vm.basket!!.subtotal)
   val items=submitted.getJSONArray("items")
   assertEquals(1,(0 until items.length()).map{items.getJSONObject(it)}.first{it.getString("productId")=="p2"}.getInt("quantity"))
   assertFalse(items.getJSONObject(0).has("priceCents"))
  }
  compose.onNodeWithText("Edit products & quantities").performScrollTo().performClick()
  compose.onNodeWithContentDescription("More packs for Pasta").performScrollTo().performClick()
  compose.runOnIdle{assertNull(vm.basket);assertFalse(vm.packagesReviewed)}
 }
 @Test fun missingProductExplainsTheDisabledReviewAndTheWholeLabelCanConfirm(){
  val requests=mutableListOf<String>()
  val vm=RetailerViewModel{path,payload->
   requests.add(path)
   if(path.endsWith("stores"))JSONObject("""{"stores":[{"id":"s1","name":"QFC","address":"Seattle"}]}""")
   else {
    val chickpeas=payload.getString("query").contains("Chickpeas")
    JSONObject("""{"storeId":"s1","currency":"usd","fetchedAt":"now","products":[{"productId":"${if(chickpeas)"beans"else "pasta"}","itemId":"${if(chickpeas)"beans"else "pasta"}","name":"${if(chickpeas)"Garbanzo Beans"else "Pasta"}","packageSize":"500 g","regularPriceCents":100,"stockLevel":"HIGH","soldBy":"UNIT"}]}""")
   }
  }
  compose.setContent{MaterialTheme{LiveShoppingScreen("missing-product","Dinner",listOf(RetailerNeed("pasta","Pasta","200 g",200.0,"g"),RetailerNeed("chickpeas","Chickpeas","200 g",200.0,"g")),emptyList(),2000,{}, {},vm)}}
  compose.waitUntil(10000){!vm.busy&&vm.selectedStore!=null}
  compose.runOnIdle{assertEquals(listOf("chickpeas"),vm.unmatchedNeeds.map{it.id});vm.confirmPackages(true);assertFalse(vm.packagesReviewed)}
  compose.onNodeWithContentDescription("Review item sizes and quantities").performScrollTo().assertIsNotEnabled()
  compose.onNodeWithText("Choose a store product for: Chickpeas.").assertExists()
  compose.onNodeWithText("Choose product for Chickpeas").performScrollTo().performClick()
  compose.onNodeWithText("Hide alternatives").assertIsDisplayed()
  compose.onNodeWithContentDescription("Select Garbanzo Beans for Chickpeas").performScrollTo().performClick()
  compose.onNodeWithText("I reviewed the products, sizes, and quantities").performScrollTo().performClick()
  compose.onNodeWithContentDescription("Review item sizes and quantities").assertIsOn()
  compose.onNodeWithText("Review live basket →").assertIsEnabled()
  compose.runOnIdle{assertTrue(vm.canReview);assertTrue(vm.packagesReviewed);assertFalse(requests.contains("/retailers/basket"))}
 }
 @Test fun combinedQuantityLimitIsExplainedAndCannotBeAcknowledgedAway(){
  val vm=RetailerViewModel{path,_->if(path.endsWith("stores"))JSONObject("""{"stores":[{"id":"s1","name":"QFC","address":"Seattle"}]}""")else JSONObject("""{"storeId":"s1","currency":"usd","fetchedAt":"now","products":[{"productId":"pasta","itemId":"pasta","name":"Pasta","packageSize":"500 g","regularPriceCents":100,"stockLevel":"HIGH","soldBy":"UNIT"}]}""")}
  compose.setContent{MaterialTheme{LiveShoppingScreen("same-product","Dinner",listOf(RetailerNeed("a","Pasta","200 g"),RetailerNeed("b","Pasta","200 g")),emptyList(),2000,{}, {},vm)}}
  compose.waitUntil(10000){!vm.busy&&vm.selections.size==2}
  compose.runOnIdle{vm.setQuantity("a",20);assertFalse(vm.canReview);vm.confirmPackages(true);assertFalse(vm.packagesReviewed);assertTrue(vm.unmatchedNeeds.isEmpty())}
  compose.onNodeWithText("Review your shopping quantities").performScrollTo().assertIsDisplayed()
  compose.onNodeWithContentDescription("Review item sizes and quantities").assertIsNotEnabled()
  compose.runOnIdle{vm.setQuantity("a",1)}
  compose.onNodeWithText("I reviewed the products, sizes, and quantities").performScrollTo().performClick()
  compose.runOnIdle{assertTrue(vm.canReview);assertTrue(vm.packagesReviewed)}
 }
 @Test fun liveKrogerProductsReachTheReviewedBasketWithoutALookupButton(){
  org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("liveRetailer")=="true")
  val vm=RetailerViewModel()
  compose.setContent{MaterialTheme{LiveShoppingScreen("live-pasta","Pasta dinner",listOf(RetailerNeed("pasta","Pasta","600 g",600.0,"g")),emptyList(),2000,{}, {},vm)}}
  compose.waitUntil(95000){!vm.busy&&vm.selectedStore!=null}
  compose.runOnIdle{
   assertNull(vm.error);assertTrue(vm.candidatesByNeed["pasta"].orEmpty().isNotEmpty())
   if(vm.selections["pasta"]==null)vm.selectForNeed("pasta",vm.candidatesByNeed.getValue("pasta").first())
  }
  compose.onNodeWithContentDescription("Review item sizes and quantities").performScrollTo().performClick()
  compose.onNodeWithText("Review live basket →").performScrollTo().performClick()
  compose.waitUntil(95000){!vm.busy&&vm.reviewing}
  compose.runOnIdle{assertNull(vm.error);assertTrue(vm.basket!!.subtotal>0);assertEquals(vm.selectedStore!!.id,vm.basket!!.storeId);assertNotNull(vm.basket!!.quoteToken)}
  compose.onNodeWithText("Review your grocery basket").performScrollTo().assertIsDisplayed()
  val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"integrated-live-basket.png").outputStream().use{instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
 }

}
