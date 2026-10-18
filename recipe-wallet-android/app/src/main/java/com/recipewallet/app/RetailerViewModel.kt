package com.recipewallet.app

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class RetailerStore(val id: String, val name: String, val address: String)
internal data class RetailerProduct(val name: String, val size: String?, val price: Long?,
    val promo: Long?, val stock: String?, val soldBy: String?)

internal suspend fun retailerRequest(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
    val connection = URL(BuildConfig.BACKEND_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 60000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val result = JSONObject((if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: "{}")
        check(status in 200..299) { result.optString("error", "Retailer search is unavailable. Please try again.") }
        check(result.optString("provider") == "kroger" && result.has("demo") && !result.getBoolean("demo")) {
            "The service did not return retailer data. Please try again."
        }
        result
    } finally { connection.disconnect() }
}

internal class RetailerViewModel(
    private val request: suspend (String, JSONObject) -> JSONObject = ::retailerRequest
) : ViewModel() {
    var postalCode by mutableStateOf("")
    var query by mutableStateOf("")
    var stores by mutableStateOf<List<RetailerStore>>(emptyList())
        private set
    var selectedStore by mutableStateOf<RetailerStore?>(null)
        private set
    var products by mutableStateOf<List<RetailerProduct>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var searchedZip by mutableStateOf("")
        private set
    var searchedQuery by mutableStateOf("")
        private set
    var fetchedAt by mutableStateOf("")
        private set

    fun findStores() {
        if (busy) return
        val zip = postalCode.trim()
        if (!zip.matches(Regex("[0-9]{5}"))) { error = "Enter a five-digit US ZIP code."; return }
        busy = true; error = null; stores = emptyList(); selectedStore = null
        products = emptyList(); searchedQuery = ""; searchedZip = ""
        viewModelScope.launch {
            try {
                val result = request("/retailers/stores", JSONObject().put("postalCode", zip))
                val rows = result.getJSONArray("stores")
                stores = List(rows.length()) { i -> rows.getJSONObject(i).let {
                    RetailerStore(it.getString("id"), it.getString("name"), it.getString("address"))
                } }
                searchedZip = zip
            } catch (e: Exception) { error = e.message ?: "Could not find stores. Please retry." }
            finally { busy = false }
        }
    }

    fun chooseStore(store: RetailerStore) {
        if (busy || store !in stores) return
        selectedStore = store; products = emptyList(); searchedQuery = ""; error = null
    }

    fun searchProducts(term: String = query) {
        if (busy) return
        val store = selectedStore ?: return
        val search = term.trim()
        if (search.length !in 2..80) { error = "Enter a product search of 2–80 characters."; return }
        query = search; busy = true; error = null; products = emptyList(); searchedQuery = ""
        viewModelScope.launch {
            try {
                val result = request("/retailers/products", JSONObject().put("storeId", store.id).put("query", search))
                check(result.getString("storeId") == store.id && result.getString("currency") == "usd") {
                    "The retailer returned prices for another store or currency. Please retry."
                }
                val rows = result.getJSONArray("products")
                fun JSONObject.textOrNull(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
                fun JSONObject.priceOrNull(key: String) = if (isNull(key)) null else getLong(key).takeIf { it > 0 }
                products = List(rows.length()) { i -> rows.getJSONObject(i).let {
                    RetailerProduct(it.getString("name"), it.textOrNull("packageSize"), it.priceOrNull("regularPriceCents"),
                        it.priceOrNull("promotionalPriceCents"), it.textOrNull("stockLevel"), it.textOrNull("soldBy"))
                } }
                searchedQuery = search; fetchedAt = result.getString("fetchedAt")
            } catch (e: Exception) { error = e.message ?: "Could not load prices. Please retry." }
            finally { busy = false }
        }
    }
}
