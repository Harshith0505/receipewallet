package com.recipewallet.app

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in live planning check; never starts a payment or changes cloud recipes. */
class GeminiPlanningLiveTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun geminiBuildsAndRevisesADiscountedPlanOnTablet() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveGemini")=="true")
        lateinit var vm:WalletViewModel
        compose.runOnIdle {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            vm=ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            vm.changeStudentEligibility(true)
        }
        compose.onNode(hasSetTextAction()).performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextReplacement("Find me a vegetarian noodle dinner under $30. I already have garlic.")
        compose.onNodeWithText("Build my meal").performScrollTo().performClick()
        compose.waitUntil(95000) { !vm.aiBusy }
        compose.runOnIdle {
            assertNull(vm.aiError);assertNotNull(vm.agentPlan);assertTrue(vm.planWithinBudget)
            assertTrue("garlic" in vm.planIds("ownedIngredientIds"))
            assertTrue(vm.agentPlan!!.getBoolean("studentEligible"))
            assertTrue(vm.plannedQuote!!.studentDiscount>0)
        }
        val original=vm.plannedQuote!!.charge
        compose.onNode(hasSetTextAction()).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextReplacement("I already have soy sauce too. Keep the same meal.")
        compose.onNodeWithText("Update my meal").performScrollTo().performClick()
        compose.waitUntil(95000) { !vm.aiBusy }
        compose.runOnIdle {
            assertNull(vm.aiError);assertTrue(vm.planWithinBudget)
            assertTrue("garlic" in vm.planIds("ownedIngredientIds"));assertTrue("soy" in vm.planIds("ownedIngredientIds"))
            assertTrue(vm.plannedQuote!!.charge<original)
        }
        compose.onNodeWithText("Review package-priced basket  →").performScrollTo().assertIsEnabled()
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        File(compose.activity.getExternalFilesDir(null),"gemini-merged-plan.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)
        }
        compose.onNodeWithText("Review package-priced basket  →").performClick()
        compose.onNodeWithText("Choose your shop").assertExists()
        compose.runOnIdle {assertEquals(2,vm.step);assertTrue(vm.studentEligible);assertTrue(vm.current.studentDiscount>0)}
    }
}
