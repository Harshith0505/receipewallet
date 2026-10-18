package com.recipewallet.app

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StudentDiscountFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun studentOffersAppearInShopsAndReduceCheckoutAndReceiptTotals() {
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.onNodeWithText("Build manually").performScrollTo().performClick()
        compose.onNodeWithText("Tomato basil pasta").performScrollTo().performClick()
        compose.onNodeWithText("Compare complete baskets →").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Apply illustrative student offers").performScrollTo().performClick()
        compose.onNodeWithText("10% illustrative student offer · applied").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("5% illustrative student offer · applied").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No illustrative student offer").performScrollTo().assertIsDisplayed()
        lateinit var vm:WalletViewModel
        compose.runOnIdle {
            vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            assertTrue(vm.studentEligible)
            val q=vm.rankings.first { it.store.id=="market" }
            assertTrue(q.studentDiscount>0)
            assertEquals(q.groceries-q.studentDiscount+q.fees+q.tax,q.charge)
            vm.checkout("market")
        }
        compose.onNodeWithText("Illustrative student offer (10%)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Total charged today · test").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        File(compose.activity.getExternalFilesDir(null),"student-discount-checkout.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
        var discounted=0L
        var savings=0L
        compose.runOnIdle {discounted=vm.current.charge;savings=vm.current.studentDiscount}
        compose.onNodeWithContentDescription("Apply illustrative student offers").performScrollTo().performClick()
        compose.runOnIdle {assertFalse(vm.studentEligible);assertEquals(discounted+savings,vm.current.charge);assertEquals(0L,vm.current.studentDiscount)}
        compose.onNodeWithContentDescription("Apply illustrative student offers").performClick()
        compose.runOnIdle {assertTrue(vm.studentEligible);assertEquals(discounted,vm.current.charge)}
        compose.onNodeWithText("Preview confirmation without payment").performScrollTo().performClick()
        compose.onNodeWithText("Illustrative student offer (10%)").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {assertEquals(discounted,vm.receipt!!.charge);assertEquals(savings,vm.receipt!!.studentDiscount);assertFalse(vm.paymentVerified)}
    }
}
