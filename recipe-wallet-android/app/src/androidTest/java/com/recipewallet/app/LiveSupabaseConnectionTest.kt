package com.recipewallet.app

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt in with -e runLiveSupabase true. Uses only public endpoints and never creates users or sends email. */
class LiveSupabaseConnectionTest {
    @Test fun tabletReachesAuthAndAnonymousRecipeAccessIsDenied() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLiveSupabase") == "true")
        val config = SupabaseConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        assumeTrue(config.configured)
        val transport = SupabaseHttpTransport(config)
        val settings = JSONObject(transport.request("GET", "/auth/v1/settings", null, null))
        assertTrue("Email authentication must be enabled", settings.getJSONObject("external").getBoolean("email"))
        assertFalse("Sign-up must be available", settings.getBoolean("disable_signup"))
        val denied = assertThrows(CloudFailure::class.java) {
            transport.request("GET", "/rest/v1/recipes?select=id", null, null)
        }
        assertTrue(denied.status in listOf(401, 403))
        assertEquals("42501", denied.code)
    }
}
