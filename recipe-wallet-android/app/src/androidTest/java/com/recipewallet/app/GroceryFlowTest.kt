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
 @Test fun manualPackageShoppingAndPreviewNeedNoBackend() {
  compose.onNodeWithText("Tomato basil pasta").performScrollTo().performClick()
  compose.onNodeWithText("2 servings").assertExists()
  compose.onNodeWithText("Compare complete baskets →").performScrollTo().performClick()
  compose.onNodeWithText("Your meal.\nYour best store.").assertExists()
  lateinit var vm:WalletViewModel
  compose.runOnIdle {
   vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
   assertTrue(vm.rankings.all { it.complete })
   vm.organicOnly=true;vm.settingsChanged()
   assertFalse(vm.rankings.first { it.store.id=="market" }.complete)
  }
  compose.onAllNodesWithText("Review checkout →").onFirst().performScrollTo().performClick()
  compose.onNodeWithText("Test payment amount").performScrollTo().assertExists()
  compose.onNodeWithText("Preview confirmation without payment").performScrollTo().performClick()
  compose.onNodeWithText("WALKTHROUGH · NO PAYMENT MADE").assertExists()
  compose.runOnIdle { assertFalse(vm.paymentVerified);assertNotNull(vm.receipt) }
  val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(compose.activity.getExternalFilesDir(null),"task3-receipt.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
 }
}
