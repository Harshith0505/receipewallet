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
import java.util.UUID

/** Opt in with -e liveScan true. Uses a public-domain sample, never the user's photos. */
class PantryScanLiveTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun actualPhotoScansThroughBackendAndOnlyReviewedIngredientsAreSaved() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveScan") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = PantryStore(context)
        val initial = store.read()
        val testName = "Scan test " + UUID.randomUUID().toString().take(8)
        lateinit var model: PantryViewModel
        var modelReady = false
        try {
            compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            compose.onNodeWithText("My pantry").performScrollTo().performClick()
            compose.onNodeWithText("Edit pantry names & amounts").performScrollTo().performClick()
            compose.runOnIdle {
                model = ViewModelProvider(compose.activity)[PantryViewModel::class.java]
                modelReady = true
                model.loadPhoto {
                    instrumentation.context.assets.open("sample-fridge.jpg").use { input ->
                        val original = PantryPhoto.cameraFile(context)
                        try {
                            original.outputStream().use { input.copyTo(it) }
                            PantryPhoto.read(context, PantryPhoto.cameraUri(context, original))
                        } finally { PantryPhoto.removeCapture(context, original.absolutePath) }
                    }
                }
            }
            compose.waitUntil(15000) { !model.loading }
            compose.onNodeWithText("Scan ingredients").performScrollTo().performClick()
            compose.waitUntil(90000) { !model.scanning }
            assertNull("Live scan error: ${model.error}", model.error)
            assertTrue("Expected visible ingredients from the sample photo", !model.detections.isNullOrEmpty())
            assertEquals("Scanning must not save anything", initial, store.read())
            val first = model.detections!!.first().item
            compose.onNodeWithText("Review your ingredients").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Save ${model.detections!!.size} to pantry").performScrollTo()
            compose.waitForIdle()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            File(compose.activity.getExternalFilesDir(null), "pantry-live-review.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
            model.detections!!.drop(1).forEach { detection ->
                compose.onNodeWithContentDescription("Include ${detection.item.name}").performScrollTo().performClick()
            }
            compose.onNodeWithContentDescription("Edit detected ${first.name}").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("Ingredient")).performClick()
            compose.waitForIdle()
            compose.onNode(hasSetTextAction() and hasText("Ingredient")).performTextReplacement(testName)
            compose.waitForIdle()
            compose.onNode(hasSetTextAction() and hasText("Amount (optional)")).performClick()
            compose.waitForIdle()
            compose.onNode(hasSetTextAction() and hasText("Amount (optional)")).performTextReplacement("2 bottles")
            compose.waitForIdle()
            compose.onNodeWithText("Save ingredient").performClick()
            assertEquals("Review edits must not save before approval", initial, store.read())
            compose.onNodeWithText("Save 1 to pantry").performScrollTo().performClick()
            assertEquals(1,store.read().count { it.name==testName && it.amount=="2 bottles" })
            assertEquals(initial.size+1,store.read().size)
            assertEquals(initial,store.read().filterNot { it.name==testName })
            compose.onNodeWithText("Review your ingredients").assertDoesNotExist()
        } finally {
            store.write(store.read().filterNot { it.name==testName })
            if (modelReady) compose.runOnIdle { model.removePhoto() }
        }
    }
}
