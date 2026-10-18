package com.recipewallet.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KrogerReturnRoutingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun onlyTheExactReturnRouteIsAcceptedAndNoTokensAreReadFromLinks() {
        val context = compose.activity
        fun matches(value: String) = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(value))
            .setPackage(context.packageName), PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        assertTrue(matches("com.recipewallet.app://kroger/return"))
        assertFalse(matches("com.recipewallet.app://kroger/other"))
        assertFalse(matches("com.recipewallet.app://auth/return"))
        assertFalse(matches("com.recipewallet.app://kroger/confirmed"))
        assertTrue(isKrogerCartReturn("com.recipewallet.app://kroger/return"))
        assertFalse(isKrogerCartReturn("com.recipewallet.app://kroger/return?access_token=untrusted"))
        assertFalse(isKrogerCartReturn("com.recipewallet.app://kroger/return#access_token=untrusted"))
    }
    @Test fun aRetailerReturnClearsIntentDataWithoutLeavingBusyStripeCheckout() {
        val original = Intent(compose.activity.intent)
        lateinit var wallet: WalletViewModel
        compose.runOnIdle {
            wallet = ViewModelProvider(compose.activity)[WalletViewModel::class.java]
            wallet.choose("noodles"); wallet.compare(); wallet.checkout(wallet.storeId); wallet.busy = true
            compose.activity.intent = Intent(original).setData(Uri.parse("fixture://pending"))
        }
        try {
            compose.runOnUiThread {
                compose.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("com.recipewallet.app://kroger/return"))
                    .setPackage(compose.activity.packageName).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            compose.waitUntil(10000) { compose.activity.intent.data == null }
            compose.runOnIdle { assertEquals(3, wallet.step); assertTrue(wallet.busy) }
        } finally { compose.runOnUiThread { wallet.busy = false; compose.activity.intent = original } }
    }
}
