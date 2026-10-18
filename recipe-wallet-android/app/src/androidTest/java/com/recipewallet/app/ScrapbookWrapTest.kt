package com.recipewallet.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@org.junit.Ignore("Wrap-up navigation was removed from the product")
class ScrapbookWrapTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Before fun resetJournal(){
  InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("food-journal-v1",android.content.Context.MODE_PRIVATE).edit().clear().commit()
  compose.activityRule.scenario.recreate()
 }
 private fun vm()=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
 private fun shot(name:String){compose.waitForIdle();val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(compose.activity.getExternalFilesDir(null),name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun sampleStoryNeverBecomesIntakeAndPagesAreInteractive(){
  shot("scrapbook-home.png")
  compose.onNodeWithText("My wrap-up").performScrollTo().performClick()
  compose.onNodeWithText("Preview a sample food story ↗").performScrollTo().performClick()
  compose.runOnIdle{assertTrue(vm().wrap.sample);assertEquals(0,vm().wrap.entries.size);assertEquals(6,vm().wrap.report.entries.size)}
  compose.onNodeWithText("Next →").performScrollTo()
  shot("scrapbook-wrap.png")
  compose.onNodeWithText("Next →").performClick()
  shot("scrapbook-macros.png")
  compose.onNodeWithText("Next →").performClick()
  compose.runOnIdle{assertEquals(0,vm().wrap.realReport.entries.size)}
  compose.onNodeWithText("Back to my own journal →").performScrollTo().performClick()
  compose.runOnIdle{assertFalse(vm().wrap.sample);assertEquals(0.0,vm().wrap.report.totals.proteinG,0.0)}
 }
 @Test fun explicitEatenPortionsAreEditableAndPersist(){
  compose.onNodeWithText("My wrap-up").performScrollTo().performClick()
  compose.onNodeWithText("+ Log a meal I ate").performScrollTo().performClick()
  compose.onNode(hasSetTextAction() and hasText("Servings you personally ate")).performScrollTo().performTextReplacement("0.5")
  compose.onNodeWithText("Save my food memory").performScrollTo().performClick()
  compose.runOnIdle{assertEquals(1,vm().wrap.entries.size);assertEquals(.5,vm().wrap.entries.first().servings,0.0);assertTrue(vm().wrap.report.totals.proteinG>0)}
  compose.activityRule.scenario.recreate()
  compose.runOnIdle{assertEquals(1,vm().wrap.entries.size);val e=vm().wrap.entries.first();assertTrue(vm().wrap.saveMeal(e.id,e.recipeId,e.date,1.0,e.ingredientIds));assertEquals(1.0,vm().wrap.report.servings,0.0);vm().wrap.removeMeal(e.id);assertEquals(0,vm().wrap.entries.size)}
 }
 @Test fun configuredLocalBackendReturnsAGroundedReport(){
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("wrapBackend")=="true")
  compose.onNodeWithText("My wrap-up").performScrollTo().performClick()
  compose.runOnIdle{vm().wrap.previewSample(true);vm().wrap.askAgent(vm().studio.prompt(),vm().studio.vegetarian)}
  compose.waitUntil(15000){!vm().wrap.busy}
  compose.runOnIdle{assertNull(vm().wrap.error);val result=vm().wrap.agentResult!!;assertEquals("local",result.getString("source"));assertEquals(vm().wrap.report.entries.size,result.getInt("loggedMeals"));assertTrue(result.getJSONArray("trace").length()>=3)
   val preferences=vm().studio.prompt();val vegetarian=vm().studio.vegetarian
   assertNotNull(vm().wrap.agentFor(preferences,vegetarian));assertNull(vm().wrap.agentFor(preferences,!vegetarian));assertNull(vm().wrap.agentFor("Different preferences",vegetarian))}
  compose.onNodeWithText("Local food agent · rule-based").performScrollTo().assertIsDisplayed()
  shot("scrapbook-agent.png")
 }
 @Test fun recipePinsRemainOnTheBoard(){
  compose.runOnIdle{if("noodles" in vm().studio.pinnedIds)vm().studio.togglePin("noodles")}
  compose.onNodeWithText("Build manually").performClick()
  compose.onNodeWithContentDescription("Pin Spicy garlic noodles").performScrollTo().performClick()
  compose.runOnIdle{assertTrue("noodles" in vm().studio.pinnedIds)}
  compose.onNodeWithText("♡ My pins").performScrollTo().performClick()
  compose.onNodeWithText("Spicy garlic noodles").assertExists()
  shot("scrapbook-board.png")
 }
}
