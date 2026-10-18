package com.recipewallet.app

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.util.UUID

internal data class RetailerStore(val id: String, val name: String, val address: String)
internal data class RetailerNeed(val id: String, val name: String, val amount: String)
internal data class RetailerProduct(val name: String, val size: String?, val price: Long?,
    val promo: Long?, val stock: String?, val soldBy: String?, val productId: String, val itemId: String) {
    val key get() = "$productId:$itemId"
    val selectable get() = productId.isNotBlank() && itemId.isNotBlank() && price != null && size != null &&
        soldBy == "UNIT" && !Regex("OUT.?OF.?STOCK|UNAVAILABLE", RegexOption.IGNORE_CASE).containsMatchIn(stock.orEmpty())
}
internal data class RetailerSelection(val label: String, val product: RetailerProduct, val quantity: Int)
internal data class RetailerBasketLine(val name: String, val size: String, val quantity: Int, val price: Long, val subtotal: Long)
internal data class RetailerBasket(val storeId: String, val fetchedAt: String, val subtotal: Long, val lines: List<RetailerBasketLine>, val quoteToken: String? = null)
internal class RetailerApiFailure(val status: Int, message: String, val transferStatus: String?) : IllegalStateException(message)
internal fun isKrogerCartReturn(value: String): Boolean = try {
    URI(value).let { it.scheme == "com.recipewallet.app" && it.host == "kroger" && it.path == "/return" && it.rawQuery == null && it.rawFragment == null && it.rawUserInfo == null && it.port == -1 }
} catch (_: Exception) { false }

private fun JSONObject.textOrNull(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
private fun JSONObject.priceOrNull(key: String) = if (isNull(key)) null else getLong(key).takeIf { it > 0 }
private fun parseRetailerProduct(row: JSONObject) = RetailerProduct(row.getString("name"), row.textOrNull("packageSize"),
    row.priceOrNull("regularPriceCents"), row.priceOrNull("promotionalPriceCents"), row.textOrNull("stockLevel"),
    row.textOrNull("soldBy"), row.optString("productId"), row.optString("itemId"))

internal suspend fun retailerRequest(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
    val connection = URL(BuildConfig.BACKEND_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 95000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val result = JSONObject((if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: "{}")
        if (status !in 200..299) throw RetailerApiFailure(status, result.optString("error", "Retailer search is unavailable. Please try again."), result.textOrNull("transferStatus"))
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
    var stores by mutableStateOf<List<RetailerStore>>(emptyList()); private set
    var selectedStore by mutableStateOf<RetailerStore?>(null); private set
    var products by mutableStateOf<List<RetailerProduct>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var searchedZip by mutableStateOf(""); private set
    var searchedQuery by mutableStateOf(""); private set
    var fetchedAt by mutableStateOf(""); private set
    var needs by mutableStateOf<List<RetailerNeed>>(emptyList()); private set
    var selections by mutableStateOf<Map<String, RetailerSelection>>(emptyMap()); private set
    var activeNeed by mutableStateOf<String?>(null); private set
    var basket by mutableStateOf<RetailerBasket?>(null); private set
    var reviewing by mutableStateOf(false)
    var cartConnection by mutableStateOf("disconnected"); private set
    var cartTransfer by mutableStateOf(""); private set
    var cartFulfillment by mutableStateOf("PICKUP"); private set
    var cartItemCount by mutableStateOf(0); private set
    private var cartSessionToken: String? = null
    private var cartAttempt: String? = null
    private var cartAttemptQuote: String? = null
    private var context by mutableStateOf("browse")
    private var requestId = 0
    val cartUncertain get() = cartTransfer in listOf("sending", "unknown")
    val editingBlocked get() = busy || cartUncertain
    val canTransfer get() = !editingBlocked && cartConnection == "connected" && cartTransfer != "added" && basket?.quoteToken != null
    val recipeMode get() = context != "browse"
    val activeLabel get() = needs.firstOrNull { it.id == activeNeed }?.name
    private fun combined() = selections.values.groupBy { it.product.key }
    val canReview get() = selections.isNotEmpty() && needs.all { it.id in selections } && combined().let { groups ->
        groups.size <= 12 && groups.values.all { rows -> rows.sumOf { it.quantity } in 1..20 }
    }
    fun configure(id: String, ingredients: List<RetailerNeed>) {
        if (cartUncertain) return
        if (context == id && needs == ingredients) return
        context = id; needs = ingredients
        requestId++; busy = false; products = emptyList(); searchedQuery = ""; activeNeed = null
        selections = emptyMap(); basket = null; reviewing = false; error = null
    }
    private fun perform(path: String, payload: JSONObject, onFailure: (Exception) -> Unit = {}, apply: (JSONObject) -> Unit) {
        val id = ++requestId
        busy = true; error = null
        viewModelScope.launch {
            try {
                val result = request(path, payload)
                if (id == requestId) apply(result)
            } catch (e: Exception) {
                if (id == requestId) { error = e.message ?: "Retailer request failed. Please retry."; onFailure(e) }
            } finally { if (id == requestId) busy = false }
        }
    }
    fun findStores() {
        if (editingBlocked) return
        val zip = postalCode.trim()
        if (!zip.matches(Regex("[0-9]{5}"))) { error = "Enter a five-digit US ZIP code."; return }
        stores = emptyList(); selectedStore = null; products = emptyList(); searchedQuery = ""; searchedZip = ""
        selections = emptyMap(); basket = null; reviewing = false; activeNeed = null
        perform("/retailers/stores", JSONObject().put("postalCode", zip)) { result ->
            val rows = result.getJSONArray("stores")
            stores = List(rows.length()) { i -> rows.getJSONObject(i).let {
                RetailerStore(it.getString("id"), it.getString("name"), it.getString("address"))
            } }
            searchedZip = zip
        }
    }
    fun chooseStore(store: RetailerStore) {
        if (editingBlocked || store !in stores) return
        if (selectedStore?.id != store.id) { selections = emptyMap(); basket = null }
        selectedStore = store; products = emptyList(); searchedQuery = ""; activeNeed = null; error = null
    }
    fun changeStore() {
        if (editingBlocked) return
        selectedStore = null; selections = emptyMap(); basket = null; products = emptyList(); searchedQuery = ""; activeNeed = null
    }
    fun searchProducts(term: String = query, needId: String? = activeNeed) {
        if (editingBlocked) return
        val store = selectedStore ?: return
        val search = term.trim()
        if (search.length !in 2..80) { error = "Enter a product search of 2–80 characters."; return }
        if (needId != null && needs.none { it.id == needId }) return
        activeNeed = needId; query = search; products = emptyList(); searchedQuery = ""
        perform("/retailers/products", JSONObject().put("storeId", store.id).put("query", search)) { result ->
            check(result.getString("storeId") == store.id && result.getString("currency") == "usd") {
                "The retailer returned prices for another store or currency. Please retry."
            }
            val rows = result.getJSONArray("products")
            products = List(rows.length()) { parseRetailerProduct(rows.getJSONObject(it)) }
            searchedQuery = search; fetchedAt = result.getString("fetchedAt")
        }
    }
    fun selectProduct(product: RetailerProduct) {
        if (editingBlocked || product !in products || !product.selectable || selectedStore == null) return
        val need = needs.firstOrNull { it.id == activeNeed }
        if (recipeMode && need == null) { error = "Choose the ingredient you are matching first."; return }
        val id = need?.id ?: "product:${product.key}"
        val count = selections[id]?.takeIf { it.product.key == product.key }?.quantity ?: 1
        selections = selections + (id to RetailerSelection(need?.name ?: product.name, product, count))
        basket = null; error = null
    }
    fun setQuantity(id: String, count: Int) {
        if (editingBlocked) return
        val row = selections[id] ?: return
        selections = selections + (id to row.copy(quantity = count.coerceIn(1, 20)))
        basket = null
    }
    fun remove(id: String) {
        if (editingBlocked) return
        selections = selections - id; basket = null
    }
    fun refreshBasket() {
        if (editingBlocked || !canReview) return
        val store = selectedStore ?: return
        val expected = combined().mapValues { (_, rows) -> rows.sumOf { it.quantity } }
        val items = combined().values.map { rows -> JSONObject().put("productId", rows[0].product.productId)
            .put("itemId", rows[0].product.itemId).put("quantity", rows.sumOf { it.quantity }) }
        basket = null
        perform("/retailers/basket", JSONObject().put("storeId", store.id).put("items", JSONArray(items))) { result ->
            check(result.getString("storeId") == store.id && result.getString("currency") == "usd" &&
                result.getString("pricingScope") == "item-subtotal-only") { "The retailer basket did not match this store. Please retry." }
            val rows = result.getJSONArray("items")
            val actual = mutableMapOf<String, Int>()
            val lines = List(rows.length()) { i ->
                val row = rows.getJSONObject(i)
                val product = parseRetailerProduct(row)
                val count = row.getInt("quantity")
                check(product.selectable && count in 1..20 && product.key !in actual) { "A retailer basket item is invalid. Please search again." }
                actual[product.key] = count
                val subtotal = Math.multiplyExact(product.price!!, count.toLong())
                check(subtotal == row.getLong("lineSubtotalCents")) { "The item totals did not match. Please refresh." }
                RetailerBasketLine(product.name, product.size!!, count, product.price, subtotal)
            }
            check(actual == expected) { "The retailer returned a different selection. Please refresh." }
            val total = lines.fold(0L) { amount, line -> Math.addExact(amount, line.subtotal) }
            check(total == result.getLong("subtotalCents")) { "The basket subtotal did not match. Please refresh." }
            basket = RetailerBasket(store.id, result.getString("fetchedAt"), total, lines, result.textOrNull("quoteToken"))
            reviewing = true
        }
    }

    fun changeCartFulfillment(value: String) {
        if (!editingBlocked && cartTransfer != "added" && value in listOf("PICKUP", "DELIVERY")) cartFulfillment = value
    }
    fun connectCart(openBrowser: (String) -> Unit) {
        if (editingBlocked || basket == null) return
        perform("/retailers/cart/connect", JSONObject()) { result ->
            val proof = result.getString("sessionToken")
            val url = result.getString("authorizationUrl")
            val uri = URI(url)
            check(proof.matches(Regex("[A-Za-z0-9_-]{43}")) && uri.scheme == "https" && uri.host == "api.kroger.com" &&
                uri.path == "/v1/connect/oauth2/authorize" && uri.rawUserInfo == null && uri.port == -1) { "The service returned an invalid Kroger sign-in link." }
            cartSessionToken = proof; cartConnection = "pending"; cartTransfer = ""; cartAttempt = null; cartAttemptQuote = null
            openBrowser(url)
        }
    }
    fun checkCartConnection() {
        if (busy) return
        val proof = cartSessionToken ?: return
        perform("/retailers/cart/status", JSONObject().put("sessionToken", proof), onFailure = { e ->
            if (e is RetailerApiFailure && e.status == 401) cartConnection = "expired"
        }) { result ->
            cartConnection = result.getString("status")
            result.optJSONObject("transfer")?.let {
                if (it.optString("attemptId") == cartAttempt) {
                    cartTransfer = it.getString("status"); cartItemCount = it.optInt("itemCount")
                }
            }
        }
    }
    fun transferCart() {
        if (!canTransfer) return
        val proof = cartSessionToken ?: return
        val quote = basket?.quoteToken ?: return
        if (cartAttemptQuote != quote) { cartAttempt = UUID.randomUUID().toString(); cartAttemptQuote = quote }
        cartTransfer = "sending"
        val payload = JSONObject().put("sessionToken", proof).put("quoteToken", quote).put("attemptId", cartAttempt).put("fulfillment", cartFulfillment)
        perform("/retailers/cart/add", payload, onFailure = { e ->
            cartTransfer = if (e !is RetailerApiFailure || e.status >= 500 || e.transferStatus in listOf("unknown", "sending")) "unknown" else "failed"
            if (e is RetailerApiFailure && e.status == 401) cartConnection = "expired"
        }) { result ->
            check(result.getString("status") == "added" && result.has("orderPlaced") && !result.getBoolean("orderPlaced") &&
                result.has("paymentMade") && !result.getBoolean("paymentMade")) { "Check your Kroger cart to confirm the transfer result." }
            cartTransfer = "added"; cartItemCount = result.getInt("itemCount")
        }
    }
    fun disconnectCart(checkedUncertainCart: Boolean = false) {
        if (busy || (cartUncertain && !checkedUncertainCart)) return
        val proof = cartSessionToken
        val clear: () -> Unit = {
            cartSessionToken = null; cartConnection = "disconnected"; cartTransfer = ""; cartAttempt = null; cartAttemptQuote = null; cartItemCount = 0
        }
        if (proof == null) { clear(); return }
        perform("/retailers/cart/disconnect", JSONObject().put("sessionToken", proof), onFailure = { clear() }) { clear() }
    }
}
