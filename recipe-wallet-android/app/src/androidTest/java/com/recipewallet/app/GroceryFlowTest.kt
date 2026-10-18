package com.recipewallet.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class GroceryFlowTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @org.junit.Before fun resetLocalProfile() {
  InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("meal-studio-v1",android.content.Context.MODE_PRIVATE).edit().clear().commit()
  compose.activityRule.scenario.recreate()
 }
 @Test fun manualPackageShoppingAndPreviewNeedNoBackend() {
  compose.onNodeWithText("Build manually").performClick()
  compose.onNodeWithText("Tomato basil pasta").performScrollTo().performClick()
  compose.onNodeWithText("2 servings").assertExists()
  compose.onNodeWithText("Compare complete baskets →").performScrollTo().performClick()
  compose.onNodeWithText("Find your basket").assertExists()
  lateinit var vm:WalletViewModel
  compose.runOnIdle {
   vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
   assertTrue(vm.rankings.all { it.complete })
   vm.changeOrganicOnly(true)
   assertFalse(vm.rankings.first { it.store.id=="market" }.complete)
  }
  compose.onAllNodesWithText("Review checkout →").onFirst().performScrollTo().performClick()
  compose.onNodeWithText("Total charged today · test").performScrollTo().assertExists()
  compose.onNodeWithText("Preview confirmation without payment").performScrollTo().performClick()
  compose.onNodeWithText("No payment was made.").assertExists()
  compose.runOnIdle { assertFalse(vm.paymentVerified);assertNotNull(vm.receipt) }
  val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(compose.activity.getExternalFilesDir(null),"task3-receipt.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
 }
}
