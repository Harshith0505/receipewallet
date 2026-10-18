package com.recipewallet.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class RecipeJournalFlowTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private var previous:String?=null
 @Before fun preserveHistory(){previous=context.getSharedPreferences("recipe-journal-v1",0).getString("meals",null)}
 @After fun restoreHistory(){context.getSharedPreferences("recipe-journal-v1",0).edit().apply{if(previous==null)remove("meals")else putString("meals",previous)}.commit()}
 @Test fun samplePaymentOpensRecipeHistoryAndPersistsCookingInstructions(){
  lateinit var vm:WalletViewModel
  compose.runOnIdle{
   vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
   vm.studio.updateAvoid("");vm.choose("noodles");vm.choose("chana");vm.step=2
   vm.startSampleCheckout("Sample store",1926)
  }
  compose.onNodeWithText("Sample checkout").assertExists()
  compose.onNodeWithText("Simulate payment & view recipe").performScrollTo().performClick()
  compose.onNodeWithText("My recipes").assertExists()
  compose.onNodeWithText("Sample checkout complete · no charge or order").assertExists()
  compose.runOnIdle{
   assertEquals(6,vm.step);assertFalse(vm.paymentVerified);assertNull(vm.receiptPaymentId)
   val saved=MealJournal(context).meals
   assertTrue(saved.any{it.id=="noodles"})
   val featured=saved.maxBy{it.sampleAt};assertEquals("chana",featured.id)
   assertEquals(1926,featured.subtotal);assertTrue(featured.steps.isNotEmpty())
  }
  compose.onNodeWithText("My recipes").performScrollTo()
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  fun shot(name:String){instrumentation.waitForIdleSync();java.io.File(context.getExternalFilesDir(null),name).outputStream().use{instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
  shot("recipe-history.png")
  compose.onNodeWithText("Let's cook").performScrollTo().assertIsDisplayed()
  shot("recipe-cooking-method.png")
 }
 @Test fun liveReviewedBasketLeadsThroughSamplePaymentToTheSameRecipe(){
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveRetailer")=="true")
  lateinit var wallet:WalletViewModel
  lateinit var retailer:RetailerViewModel
  compose.runOnIdle{
   wallet=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
   retailer=ViewModelProvider(compose.activity)[RetailerViewModel::class.java]
   wallet.studio.updateAvoid("")
   wallet.choosePersonal(PersonalRecipe("live-sample-pasta","Pasta night",listOf("200 g Pasta"),15));wallet.compare()
  }
  compose.waitUntil(95000){!retailer.busy&&retailer.selections.isNotEmpty()}
  compose.runOnIdle{assertTrue(retailer.canReview);assertNull(retailer.error)}
  compose.onNodeWithContentDescription("Review item sizes and quantities").performScrollTo().performClick()
  compose.onNodeWithText("Review live basket →").performScrollTo().performClick()
  compose.waitUntil(95000){!retailer.busy&&retailer.reviewing}
  var subtotal=0L
  compose.runOnIdle{subtotal=retailer.basket!!.subtotal}
  compose.onNodeWithText("Continue to sample checkout").performScrollTo().performClick()
  compose.onNodeWithText("Simulate payment & view recipe").performScrollTo().performClick()
  compose.onNodeWithText("My recipes").assertExists()
  compose.runOnIdle{
   assertEquals(6,wallet.step);assertFalse(wallet.paymentVerified)
   val featured=wallet.journal.meals.maxBy{it.sampleAt}
   assertEquals("Pasta night",featured.name);assertEquals(subtotal,featured.subtotal)
   assertEquals("disconnected",retailer.cartConnection);assertEquals("",retailer.cartTransfer)
  }
 }
 @Test fun generatedMethodAndServingsSurviveApprovalAndHistoryReload(){
  compose.runOnIdle{
   val vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
   vm.generatedRecipe=JSONObject("""{"id":"journal-pizza","name":"Garden pizza","servings":4,"prepMinutes":25,"explanation":"A vegetable pizza","constraints":{"budgetCents":3000},"ingredients":[{"name":"Flour","amount":"500 g","owned":true},{"name":"Tomatoes","amount":"3 whole","owned":false}],"steps":["Prepare the dough.","Add tomatoes and bake until cooked."]}""")
   vm.approveGeneratedRecipe()
   val entry=MealJournal(context).meals.first{it.id=="saved:journal-pizza"}
   assertEquals(4,entry.servings);assertEquals(2,entry.steps.size)
   assertTrue(entry.foods.first().owned);assertEquals("500 g",entry.foods.first().amount)
   assertEquals(entry.steps,vm.currentMealSnapshot().steps)
  }
 }
 @Test fun revisedMealDoesNotRewriteAnEarlierCompletedSampleShop(){
  compose.runOnIdle{
   val journal=MealJournal(context)
   val first=JournalMeal("test-meal","First meal","",2,20,listOf(MealFood("Rice","1 cup")),listOf("Cook rice."),1)
   journal.remember(first);journal.completeSample(first.id,"Store",500)
   journal.remember(first.copy(servings=4,viewedAt=2,foods=listOf(MealFood("Rice","2 cups"))))
   assertEquals(0,journal.meals.first{it.id==first.id}.sampleAt)
   val old=journal.meals.first{it.id.startsWith("test-meal:shop:")}
   assertEquals(2,old.servings);assertEquals("1 cup",old.foods.first().amount);assertEquals(500,old.subtotal)
  }
 }
}
