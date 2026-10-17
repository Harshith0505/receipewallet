package com.recipewallet.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal class CloudRecipeCache(context: Context, namespace: String) {
    private val preferences = context.getSharedPreferences("cloud-recipes-$namespace", Context.MODE_PRIVATE)
    fun read(): List<CloudRecipe> {
        val rows = JSONArray(preferences.getString("recipes", "[]"))
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val lines = row.getJSONArray("ingredients")
            CloudRecipe(PersonalRecipe(row.getString("id"), row.getString("name"),
                List(lines.length()) { lines.getString(it) }, row.optInt("prepMinutes", 0).takeIf { it > 0 }),
                row.getInt("version"), row.getLong("modifiedAt"), row.getBoolean("dirty"))
        }
    }
    fun write(values: List<CloudRecipe>) {
        val rows = JSONArray()
        values.forEach { value ->
            val recipe = value.recipe
            validatePersonalRecipe(recipe)
            rows.put(JSONObject().put("id", recipe.id).put("name", recipe.name).put("ingredients", JSONArray(recipe.ingredients))
                .put("prepMinutes", recipe.prepMinutes ?: JSONObject.NULL).put("version", value.version)
                .put("modifiedAt", value.modifiedAt).put("dirty", value.dirty))
        }
        check(preferences.edit().putString("recipes", rows.toString()).commit()) { "Couldn’t save on this device. Please try again." }
    }
}

internal data class RecipeLibraryState(val recipes: List<PersonalRecipe>, val email: String?, val pending: Int, val importable: Int)

internal class RecipeCloudViewModel(app: Application) : AndroidViewModel(app) {
    private val config = SupabaseConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
    private val guest = PersonalRecipeStore(app)
    private val client = SupabaseClient(SecureSessionVault(app, config.namespace), SupabaseHttpTransport(config))
    private val mutex = Mutex()
    val configured get() = config.configured
    var state by mutableStateOf(RecipeLibraryState(guest.read(), null, 0, 0))
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    init { refresh() }
    private fun cache(): CloudRecipeCache = CloudRecipeCache(getApplication(), "${config.namespace}-${checkNotNull(client.session).userId}")
    private fun snapshot(): RecipeLibraryState {
        val session = client.session ?: return RecipeLibraryState(guest.read(), null, 0, 0)
        val cached = cache().read()
        val ids = cached.map { it.recipe.id }.toSet()
        return RecipeLibraryState(cached.sortedByDescending { it.modifiedAt }.map { it.recipe }, session.email,
            cached.count { it.dirty }, guest.read().count { it.id !in ids })
    }
    private suspend fun perform(action: () -> String): String? {
        if (busy) return "Please wait for the current sync to finish."
        busy = true
        val result = runCatching { withContext(Dispatchers.IO) { mutex.withLock { action() to snapshot() } } }
        result.onSuccess { (message, updated) -> state = updated; status = message }
            .onFailure { error ->
                status = if (error is CloudFailure || error is IllegalArgumentException || error is IllegalStateException)
                    error.message ?: "Cloud storage is unavailable." else "Couldn’t connect. Your saved recipes are still on this device."
            }
        busy = false
        return result.exceptionOrNull()?.let { status }
    }
    fun refresh() {
        if (!busy) runCatching { state = snapshot() }
        viewModelScope.launch { perform {
        if (client.session == null) "Recipes are saved on this device." else safeSync()
    } } }
    suspend fun save(recipe: PersonalRecipe): String? = perform {
        validatePersonalRecipe(recipe)
        if (client.session == null) { guest.save(recipe); "Saved on this device." }
        else {
            val values = cache().read().associateBy { it.recipe.id }.toMutableMap()
            val previous = values[recipe.id]
            values[recipe.id] = CloudRecipe(recipe, previous?.version ?: 0, System.currentTimeMillis(), dirty = true)
            cache().write(values.values.toList())
            safeSync()
        }
    }
    suspend fun signIn(email: String, password: String): String? = perform {
        config.validate(); client.signIn(email.trim(), password); safeSync()
    }
    suspend fun signUp(email: String, password: String): String? = perform {
        config.validate()
        if (client.signUp(email.trim(), password)) safeSync() else "Confirm your email, then return here to sign in."
    }
    fun importLocalRecipes() { viewModelScope.launch { perform {
        val values = cache().read().associateBy { it.recipe.id }.toMutableMap()
        guest.read().filter { it.id !in values }.forEach {
            values[it.id] = CloudRecipe(it, 0, System.currentTimeMillis(), dirty = true)
        }
        cache().write(values.values.toList()); safeSync()
    } } }
    suspend fun signOut(): String? = perform {
        try { client.signOut() } catch (error: Exception) { if (client.session != null) throw error }
        "Signed out. Recipes saved on this device are available below."
    }
    private fun safeSync(): String = try { sync() } catch (error: Exception) {
        when {
            error is CloudFailure && error.status == 401 -> "Please sign in again. Unsynced edits are kept on this device."
            else -> "Sync paused. Your edits are kept on this device; tap Sync to retry."
        }
    }
    private fun sync(): String {
        val cache = cache()
        val working = cache.read().associateBy { it.recipe.id }.toMutableMap()
        var copies = 0
        working.values.filter { it.dirty }.toList().forEach { pending ->
            try { working[pending.recipe.id] = client.save(pending) }
            catch (error: CloudFailure) {
                if (error.status != 409) throw error
                // Keep both versions. Never overwrite a newer edit on another device.
                val copy = pending.copy(recipe = pending.recipe.copy(id = UUID.randomUUID().toString(),
                    name = pending.recipe.name.take(73) + " (copy)"), version = 0, dirty = true)
                working.remove(pending.recipe.id); working[copy.recipe.id] = copy
                cache.write(working.values.toList())
                working[copy.recipe.id] = client.save(copy); copies++
            }
            cache.write(working.values.toList())
        }
        val remote = client.recipes().associateBy { it.recipe.id }.toMutableMap()
        working.values.filter { it.dirty }.forEach { remote[it.recipe.id] = it }
        cache.write(remote.values.toList())
        return if (copies > 0) "Synced. An edit from another device was kept separately; your change was saved as a copy."
            else "All recipes are synced to your private cloud library."
    }
}
