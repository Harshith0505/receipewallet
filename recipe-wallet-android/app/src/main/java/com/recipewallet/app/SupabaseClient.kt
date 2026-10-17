package com.recipewallet.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class SupabaseConfiguration(val url: String, val publishableKey: String) {
    val configured get() = url.isNotBlank() && publishableKey.isNotBlank()
    val namespace get() = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    fun validate() {
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && uri.path.orEmpty() in listOf("", "/")) { "Use the Supabase project HTTPS URL." }
        require(publishableKey.startsWith("sb_publishable_")) { "Use a Supabase publishable key, never a secret or service-role key." }
    }
}

internal data class CloudSession(val userId: String, val email: String, val accessToken: String, val refreshToken: String, val expiresAt: Long) {
    override fun toString() = "CloudSession(tokens=redacted)"
}
internal interface SessionVault {
    fun read(): CloudSession?
    fun save(session: CloudSession)
    fun clear()
}

/** Tokens are encrypted with a non-exportable Android Keystore key. Passwords are never persisted. */
internal class SecureSessionVault(context: Context, namespace: String) : SessionVault {
    private val preferences = context.getSharedPreferences("cloud-session-$namespace", Context.MODE_PRIVATE)
    private val alias = "recipe-wallet-$namespace"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun read(): CloudSession? {
        val value = preferences.getString("session", null) ?: return null
        return runCatching {
            val parts = value.split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
            CloudSession(json.getString("userId"), json.getString("email"), json.getString("access"), json.getString("refresh"), json.getLong("expires"))
        }.getOrNull()
    }
    override fun save(session: CloudSession) {
        val json = JSONObject().put("userId", session.userId).put("email", session.email).put("access", session.accessToken)
            .put("refresh", session.refreshToken).put("expires", session.expiresAt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(preferences.edit().putString("session", value).commit()) { "Couldn’t save your sign-in on this device." }
    }
    override fun clear() { check(preferences.edit().remove("session").commit()) }
}

internal class CloudFailure(val status: Int, val code: String, message: String) : Exception(message)
internal fun interface SupabaseTransport {
    fun request(method: String, path: String, body: JSONObject?, token: String?): String
}

internal class SupabaseHttpTransport(private val config: SupabaseConfiguration) : SupabaseTransport {
    override fun request(method: String, path: String, body: JSONObject?, token: String?): String {
        config.validate()
        val connection = URL(config.url.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.connectTimeout = 10000; connection.readTimeout = 20000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("apikey", config.publishableKey)
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val code = runCatching { JSONObject(text).optString("error_code", JSONObject(text).optString("code")) }.getOrDefault("")
                val message = when {
                    status == 409 -> "This recipe changed on another device."
                    code == "invalid_credentials" -> "Email or password is incorrect."
                    code == "email_not_confirmed" -> "Confirm your email, then sign in."
                    code == "weak_password" -> "Choose a stronger password."
                    status == 429 -> "Too many attempts. Please wait and try again."
                    status == 401 || code == "refresh_token_not_found" -> "Please sign in again to sync your recipes."
                    else -> "Cloud storage is unavailable. Please try again."
                }
                throw CloudFailure(status, code, message)
            }
            return text
        } finally { connection.disconnect() }
    }
}

internal data class CloudRecipe(val recipe: PersonalRecipe, val version: Int, val modifiedAt: Long, val dirty: Boolean = false)

internal class SupabaseClient(private val vault: SessionVault, private val transport: SupabaseTransport) {
    var session: CloudSession? = vault.read()
        private set
    private fun store(json: JSONObject, expectedOwner: String? = null) {
        val user = json.getJSONObject("user")
        val id = user.getString("id")
        check(expectedOwner == null || expectedOwner == id) { "The cloud account changed. Sign in again." }
        val value = CloudSession(id, user.optString("email"), json.getString("access_token"), json.getString("refresh_token"),
            json.optLong("expires_at", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)))
        vault.save(value); session = value
    }
    fun signIn(email: String, password: String) {
        store(JSONObject(transport.request("POST", "/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password), null)))
    }
    fun signUp(email: String, password: String): Boolean {
        val json = JSONObject(transport.request("POST", "/auth/v1/signup", JSONObject().put("email", email).put("password", password), null))
        if (json.has("access_token") && !json.isNull("access_token")) { store(json); return true }
        return false
    }
    private fun refresh() {
        val previous = checkNotNull(session) { "Sign in to sync recipes." }
        store(JSONObject(transport.request("POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", previous.refreshToken), null)), previous.userId)
    }
    private fun authorized(method: String, path: String, body: JSONObject? = null): String {
        val current = checkNotNull(session) { "Sign in to sync recipes." }
        if (current.expiresAt <= System.currentTimeMillis() / 1000 + 30) refresh()
        return try { transport.request(method, path, body, session!!.accessToken) }
        catch (error: CloudFailure) {
            if (error.status != 401) throw error
            refresh(); transport.request(method, path, body, session!!.accessToken)
        }
    }
    private fun decode(row: JSONObject): CloudRecipe {
        check(row.getString("user_id") == session?.userId) { "Unexpected recipe owner." }
        val ingredients = row.getJSONArray("ingredients")
        val recipe = PersonalRecipe(row.getString("id"), row.getString("name"),
            List(ingredients.length()) { ingredients.getString(it) }, row.optInt("prep_minutes", 0).takeIf { it > 0 })
        validatePersonalRecipe(recipe)
        return CloudRecipe(recipe, row.getInt("version"), Instant.parse(row.getString("updated_at")).toEpochMilli())
    }
    fun recipes(): List<CloudRecipe> {
        val result = linkedMapOf<String, CloudRecipe>()
        var offset = 0
        while (true) {
            val rows = JSONArray(authorized("GET", "/rest/v1/recipes?select=id,user_id,name,ingredients,prep_minutes,version,updated_at&order=id.asc&limit=200&offset=$offset"))
            repeat(rows.length()) { val value = decode(rows.getJSONObject(it)); result[value.recipe.id] = value }
            if (rows.length() < 200) return result.values.toList()
            offset += 200
        }
    }
    fun save(value: CloudRecipe): CloudRecipe {
        val recipe = value.recipe
        val row = JSONObject(authorized("POST", "/rest/v1/rpc/save_recipe", JSONObject()
            .put("p_recipe_id", recipe.id).put("p_name", recipe.name).put("p_ingredients", JSONArray(recipe.ingredients))
            .put("p_prep_minutes", recipe.prepMinutes ?: JSONObject.NULL).put("p_expected_version", value.version)))
        val saved = decode(row)
        check(saved.recipe.id == recipe.id && saved.version > value.version) { "Unexpected saved recipe response." }
        return saved
    }
    fun signOut() {
        try { if (session != null) authorized("POST", "/auth/v1/logout?scope=local", JSONObject()) }
        finally { vault.clear(); session = null }
    }
}
