package com.recipewallet.app

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ConfirmationRoutingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun androidResolvesOnlyTheRegisteredConfirmationPath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun matches(value: String) = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, Uri.parse(value)).setPackage(context.packageName), PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(matches(SignupCallback + "?code=synthetic").isNotEmpty())
        assertTrue(matches("com.recipewallet.app://auth/other?code=synthetic").isEmpty())
        assertTrue(matches("com.recipewallet.app://foreign/confirmed?code=synthetic").isEmpty())
    }
    @Test fun confirmationReturnsToAccountWithoutAcceptingAnInvalidLink() {
        lateinit var library: RecipeCloudViewModel
        compose.runOnIdle {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            library = ViewModelProvider(compose.activity)[RecipeCloudViewModel::class.java]
        }
        compose.waitUntil(30000) { !library.busy }
        val originalEmail = library.state.email
        val originalIntent = Intent(compose.activity.intent)
        try {
        compose.runOnUiThread {
            compose.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SignupCallback + "?error_code=otp_expired"))
                .setPackage(compose.activity.packageName).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        compose.waitUntil(10000) { library.status?.contains("expired or was already used") == true && !library.busy }
        compose.onNodeWithText("Your recipe library").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(originalEmail, library.state.email)
            assertNull(compose.activity.intent.data)
            assertEquals(0, library.confirmationRequests)
        }
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "confirmation-return.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        } finally {
            // ActivityScenario identifies its activity using the original launcher Intent.
            compose.runOnUiThread { compose.activity.intent = originalIntent; library.refresh() }
        }
    }
}
