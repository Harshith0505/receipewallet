package com.recipewallet.app

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CloudStorageTest {
    private val owner = "11111111-1111-4111-8111-111111111111"
    private class MemoryVault(var value: CloudSession? = null) : SessionVault {
        override fun read() = value
        override fun save(session: CloudSession) { value = session }
        override fun clear() { value = null }
    }
    private fun session(expiry: Long = System.currentTimeMillis() / 1000 + 3600) =
        CloudSession(owner, "fixture@example.invalid", "access-fixture", "refresh-fixture", expiry)
    private fun response(id: String = owner) = JSONObject().put("user", JSONObject().put("id", id).put("email", "fixture@example.invalid"))
        .put("access_token", "rotated-access").put("refresh_token", "rotated-refresh").put("expires_in", 3600).toString()
    private fun row(id: String = UUID.randomUUID().toString(), user: String = owner, version: Int = 1) =
        JSONObject().put("id", id).put("user_id", user).put("name", "Test recipe")
            .put("ingredients", JSONArray(listOf("2 eggs"))).put("prep_minutes", 10).put("version", version)
            .put("updated_at", "2026-10-17T12:00:00.123456+00:00")

    @Test fun tokensAreEncryptedAndPersistAcrossVaultInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "test-${UUID.randomUUID()}"
        val vault = SecureSessionVault(context, namespace)
        try {
            val expected = session()
            vault.save(expected)
            val raw = context.getSharedPreferences("cloud-session-$namespace", 0).getString("session", "")!!
            assertFalse(raw.contains(expected.accessToken)); assertFalse(raw.contains(expected.refreshToken))
            assertFalse(raw.contains(expected.email))
            assertEquals(expected, SecureSessionVault(context, namespace).read())
            vault.clear(); assertNull(SecureSessionVault(context, namespace).read())
        } finally { vault.clear() }
    }
    @Test fun recipesAndPendingEditsAreScopedToAccountAndProject() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "test-${UUID.randomUUID()}"
        val a = CloudRecipeCache(context, "$namespace-a")
        val b = CloudRecipeCache(context, "$namespace-b")
        val otherProject = CloudRecipeCache(context, "other-$namespace-a")
        try {
            val pending = CloudRecipe(PersonalRecipe(UUID.randomUUID().toString(), "Offline recipe", listOf("2 eggs")), 3, 1000, true)
            a.write(listOf(pending))
            assertEquals(listOf(pending), CloudRecipeCache(context, "$namespace-a").read())
            assertTrue(b.read().isEmpty()); assertTrue(otherProject.read().isEmpty())
        } finally { a.write(emptyList()); b.write(emptyList()); otherProject.write(emptyList()) }
    }
    @Test fun expiredSessionRotatesBeforeARecipeRequest() {
        val vault = MemoryVault(session(0))
        var calls = 0
        val client = SupabaseClient(vault, SupabaseTransport { method, path, body, token ->
            calls++
            if (path.contains("refresh_token")) {
                assertEquals("POST", method); assertEquals("refresh-fixture", body!!.getString("refresh_token")); assertNull(token)
                response()
            } else {
                assertEquals("rotated-refresh", vault.value!!.refreshToken)
                assertEquals("rotated-access", token)
                JSONArray().put(row()).toString()
            }
        })
        assertEquals(1, client.recipes().size); assertEquals(2, calls)
    }
    @Test fun aRefreshCannotSwitchAccountsAndUnexpectedOwnersAreRejected() {
        val original = session(0)
        val vault = MemoryVault(original)
        val client = SupabaseClient(vault, SupabaseTransport { _, _, _, _ -> response(UUID.randomUUID().toString()) })
        assertThrows(IllegalStateException::class.java) { client.recipes() }
        assertEquals(original, vault.value)
        val other = SupabaseClient(MemoryVault(session()), SupabaseTransport { _, _, _, _ ->
            JSONArray().put(row(user = UUID.randomUUID().toString())).toString()
        })
        assertThrows(IllegalStateException::class.java) { other.recipes() }
    }
    @Test fun savesCarryVersionAndNeverSupplyAnOwner() {
        val recipe = PersonalRecipe(UUID.randomUUID().toString(), "Test recipe", listOf("2 eggs"), 10)
        val client = SupabaseClient(MemoryVault(session()), SupabaseTransport { method, path, body, token ->
            assertEquals("POST", method); assertEquals("/rest/v1/rpc/save_recipe", path)
            assertEquals("access-fixture", token); assertEquals(3, body!!.getInt("p_expected_version"))
            assertFalse(body.has("user_id")); assertEquals(recipe.id, body.getString("p_recipe_id"))
            row(id = recipe.id, version = 4).toString()
        })
        val saved = client.save(CloudRecipe(recipe, 3, 0, true))
        assertEquals(4, saved.version); assertFalse(saved.dirty)
    }
    @Test fun emailConfirmationDoesNotCreateASessionAndOfflineLogoutClearsTokens() {
        val vault = MemoryVault()
        val signup = SupabaseClient(vault, SupabaseTransport { _, _, _, _ -> "{\"id\":\"pending\"}" })
        assertFalse(signup.signUp("fixture@example.invalid", "synthetic-test-password")); assertNull(vault.value)
        vault.value = session()
        val offline = SupabaseClient(vault, SupabaseTransport { _, _, _, _ -> throw java.io.IOException("Offline") })
        assertThrows(java.io.IOException::class.java) { offline.signOut() }; assertNull(vault.value); assertNull(offline.session)
    }
}
