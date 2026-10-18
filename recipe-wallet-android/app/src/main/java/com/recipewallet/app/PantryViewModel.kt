package com.recipewallet.app

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PantryViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PantryStore(app)
    private val initial = runCatching { store.read() }
    val writable = initial.isSuccess
    var items by mutableStateOf(initial.getOrDefault(emptyList())); private set
    var error by mutableStateOf(if (writable) null else "Couldn’t open your pantry. Reopen the app to try again."); private set
    var photo by mutableStateOf<Bitmap?>(null); private set
    var photoPath: String? = null; private set
    var loading by mutableStateOf(false); private set
    var scanning by mutableStateOf(false); private set
    var detections by mutableStateOf<List<PantryDetection>?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    val busy get() = loading || scanning

    fun showError(message: String) { error = message }
    fun loadPhoto(load: () -> Bitmap) {
        if (busy) return
        loading = true; error = null; notice = null
        viewModelScope.launch {
            try {
                val (bitmap, path) = withContext(Dispatchers.IO) {
                    val value = load(); value to PantryPhoto.save(getApplication(), value)
                }
                PantryPhoto.remove(getApplication(), photoPath)
                photo = bitmap; photoPath = path; detections = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn’t open this photo. Please try another." }
            finally { loading = false }
        }
    }
    fun removePhoto() {
        if (busy) return
        PantryPhoto.remove(getApplication(), photoPath)
        photo = null; photoPath = null; detections = null; notice = null
    }
    fun scan() {
        val path = photoPath ?: return
        if (busy) return
        scanning = true; error = null; notice = null; detections = null
        viewModelScope.launch {
            try { detections = PantryScanner.scan(path) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is java.io.IOException) "Can’t reach the scan service. Check the backend connection and retry." else e.message ?: "Couldn’t scan this photo. Please retry." }
            finally { scanning = false }
        }
    }
    fun select(id: String, value: Boolean) { detections = detections?.map { if (it.item.id == id) it.copy(selected = value) else it } }
    fun editDetection(item: PantryItem) { detections = detections?.map { if (it.item.id == item.id) it.copy(item = item) else it } }
    fun dismissReview() { detections = null }
    fun save(values: List<PantryItem>): Boolean {
        if (!writable) return false
        return runCatching { store.write(values) }.onSuccess { items = values; error = null }
            .onFailure { error = it.message ?: "Couldn’t save your pantry. Please try again." }.isSuccess
    }
    fun saveReviewed() {
        val chosen = detections?.filter { it.selected }?.map { it.item } ?: return
        if (chosen.isEmpty()) return
        val previousCount = items.size
        val merged = mergePantry(items, chosen)
        if (save(merged)) {
            val added = merged.size - previousCount
            val skipped = chosen.size - added
            notice = if (added == 0) "These ingredients are already in your pantry. Your amounts haven’t changed."
                else "Added $added ${if (added == 1) "ingredient" else "ingredients"}." + if (skipped > 0) " Already saved items were kept as they are." else ""
            detections = null
        }
    }
}

/** Re-scanning cannot overwrite confirmed amounts or create duplicate names. */
internal fun mergePantry(existing: List<PantryItem>, incoming: List<PantryItem>): List<PantryItem> {
    val names = existing.map { it.name.trim().lowercase().replace(Regex("\\s+"), " ") }.toMutableSet()
    return existing + incoming.filter { names.add(it.name.trim().lowercase().replace(Regex("\\s+"), " ")) }
}
