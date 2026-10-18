package com.recipewallet.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class WalletViewModel(app: Application): AndroidViewModel(app) {
 val catalog = Catalog(app)
 var preference by mutableStateOf("")
 var aiBusy by mutableStateOf(false)
 var agentPlan by mutableStateOf<JSONObject?>(null)
 var agentReply by mutableStateOf("")
 var agentTrace by mutableStateOf<List<String>>(emptyList())
 var aiError by mutableStateOf<String?>(null)
 private var agentSession: String? = null
 fun suggest() {
  if(aiBusy || preference.isBlank()) return
  val message=preference
  aiBusy=true; aiError=null; agentPlan=null; agentReply=""; agentTrace=emptyList()
  viewModelScope.launch {
   try {
    val payload=JSONObject().put("message",message).put("userId",userId)
    agentSession?.let { payload.put("sessionId",it) }
    val result=request("/plan",payload)
    val plan=result.optJSONObject("plan")
    if(plan!=null) {
     val r=catalog.recipes.first { it.id==plan.getString("recipeId") }
     val ids=plan.getJSONArray("ingredientIds").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
     check(ids.isNotEmpty() && ids.all { id -> r.ingredients.any { it.id==id } }) { "The agent returned an invalid basket. Please retry." }
     val s=catalog.stores.first { it.id==plan.getString("storeId") }
     val c=catalog.cards.first { it.id==plan.getString("cardId") }
     val q=quote(r.ingredients.filter { it.id in ids }.sumOf { it.basePrice },s,c)
     check(q.charge==plan.getLong("amount") && q.rewards==plan.getLong("rewards") && q.net==plan.getLong("net")) { "Prices changed. Please plan your shop again." }
    }
    ownedHints=result.optJSONArray("ownedHints")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> OwnedHint(o.getString("ingredientId"), o.getString("name"), o.getString("text")) } } } ?: emptyList()
    agentPlan=plan; agentReply=result.getString("reply"); agentSession=result.getString("sessionId")
    val trace=result.getJSONArray("trace")
    agentTrace=(0 until trace.length()).map { trace.getJSONObject(it).getString("summary") }
    preference=""
   } catch(e: Exception) { aiError=e.message ?: "AI is unavailable. Choose a recipe below." }
   aiBusy=false
  }
 }
 val plannedRecipe get() = agentPlan?.let { p -> catalog.recipes.first { it.id==p.getString("recipeId") } }
 fun planIds(key: String): Set<String> = agentPlan?.getJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
 val plannedQuote get() = agentPlan?.let { p -> quote(plannedRecipe!!.ingredients.filter { it.id in planIds("ingredientIds") }.sumOf { it.basePrice },catalog.stores.first { it.id==p.getString("storeId") },catalog.cards.first { it.id==p.getString("cardId") }) }
 val planWithinBudget get() = agentPlan?.optBoolean("withinBudget",false)==true
 val planBudget get() = agentPlan?.getJSONObject("constraints")?.getLong("budgetCents") ?: -1L
 val planDiet get() = agentPlan?.getJSONObject("constraints")?.getString("diet") ?: "any"
 val planTime get() = agentPlan?.getJSONObject("constraints")?.getInt("maxPrepMinutes") ?: 0
 fun approvePlan() {
  val p=agentPlan ?: return
  if(!planWithinBudget || aiBusy) return
  recipeId=p.getString("recipeId"); selected=planIds("ingredientIds"); storeId=p.getString("storeId"); cardId=p.getString("cardId")
  attempt=UUID.randomUUID().toString(); error=null; step=3
 }

 // ---- Purchase memory: anonymous per-install id; the backend remembers verified purchases under it ----
 data class OwnedHint(val ingredientId: String, val name: String, val text: String)
 data class RecentItem(val id: String, val name: String, val boughtDaysAgo: Int)
 val userId: String = app.getSharedPreferences("recipe_wallet_pantry", android.content.Context.MODE_PRIVATE).let { p ->
  p.getString("user_id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("user_id", it).apply() }
 }
 var ownedHints by mutableStateOf<List<OwnedHint>>(emptyList())
 var recentItems by mutableStateOf<List<RecentItem>>(emptyList())
 fun loadMemory() {
  viewModelScope.launch {
   try {
    val rows = request("/memory", JSONObject().put("userId", userId)).getJSONArray("likelyOwned")
    recentItems = (0 until rows.length()).map { rows.getJSONObject(it).let { o -> RecentItem(o.getString("id"), o.getString("name"), o.getInt("boughtDaysAgo")) } }
   } catch(e: Exception) { /* memory is optional; the app works without it */ }
  }
 }
 /** "I already have this": tells the planner, which removes it from the basket and reprices. */
 fun confirmOwned(h: OwnedHint) {
  if(aiBusy) return
  preference = "I already have ${h.name.lowercase()}"
  suggest()
 }
 fun notOwned(h: OwnedHint) {
  ownedHints = ownedHints.filter { it.ingredientId != h.ingredientId }
  viewModelScope.launch { try { request("/used-up", JSONObject().put("userId", userId).put("ingredientId", h.ingredientId)) } catch(e: Exception) {} }
 }
 fun addRecentToPantry() {
  pantry = pantry + recentItems.map { it.id }
  prefs.edit().putStringSet("ids", pantry).apply()
  pantrySuggestions = emptyList(); pantryReply = ""
 }

 // ---- Pantry: "what can I cook with what I have" (calls backend /suggest) ----
 data class PantrySuggestion(val recipeId: String, val haveIds: List<String>, val missingIds: List<String>, val canCookNow: Boolean, val summary: String)
 private val prefs = app.getSharedPreferences("recipe_wallet_pantry", android.content.Context.MODE_PRIVATE)
 var pantry by mutableStateOf(prefs.getStringSet("ids", emptySet())?.toSet() ?: emptySet())
 var pantryBusy by mutableStateOf(false)
 var pantryError by mutableStateOf<String?>(null)
 var pantryReply by mutableStateOf("")
 var pantrySuggestions by mutableStateOf<List<PantrySuggestion>>(emptyList())
 val allIngredients get() = catalog.recipes.flatMap { it.ingredients }.distinctBy { it.id }
 fun togglePantry(id: String) {
  pantry = if(id in pantry) pantry - id else pantry + id
  prefs.edit().putStringSet("ids", pantry).apply()
  pantrySuggestions = emptyList(); pantryReply = ""
 }
 fun suggestFromPantry() {
  if(pantryBusy) return
  pantryBusy = true; pantryError = null
  viewModelScope.launch {
   try {
    val result = request("/suggest", JSONObject().put("pantryIds", JSONArray(pantry.toList())))
    val rows = result.getJSONArray("suggestions")
    fun ids(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
    pantrySuggestions = (0 until rows.length()).map { i ->
     val o = rows.getJSONObject(i)
     PantrySuggestion(o.getString("recipeId"), ids(o.getJSONArray("haveIds")), ids(o.getJSONArray("missingIds")), o.getBoolean("canCookNow"), o.getString("summary"))
    }.filter { s -> catalog.recipes.any { it.id == s.recipeId } }
    pantryReply = result.optString("reply")
   } catch(e: Exception) { pantryError = e.message ?: "Could not reach the suggestion service." }
   pantryBusy = false
  }
 }
 /** Opens the normal basket flow with only the missing ingredients selected. */
 fun cookFromPantry(s: PantrySuggestion) {
  if(s.canCookNow || s.missingIds.isEmpty()) return
  newPlan(); recipeId = s.recipeId
  selected = s.missingIds.toSet()
  storeId = ""; cardId = ""; attempt = UUID.randomUUID().toString(); step = 1; error = null
 }
 fun newPlan() { ownedHints=emptyList(); agentSession=null; agentPlan=null; agentReply=""; agentTrace=emptyList(); aiError=null; preference="" }
 init { loadMemory() }
 var step by mutableIntStateOf(0)
 var recipeId by mutableStateOf(catalog.recipes.first().id)
 var selected by mutableStateOf(catalog.recipes.first().ingredients.map { it.id }.toSet())
 var storeId by mutableStateOf("")
 var cardId by mutableStateOf("")
 var busy by mutableStateOf(false)
 var error by mutableStateOf<String?>(null)
 var receipt by mutableStateOf<Quote?>(null)
 var paymentVerified by mutableStateOf(false)
 private var pending: Quote? = null
 private var intentId: String? = null
 private var attempt = UUID.randomUUID().toString()
 val recipe get() = catalog.recipes.first { it.id == recipeId }
 val subtotal get() = recipe.ingredients.filter { it.id in selected }.sumOf { it.basePrice }
 val rankings get() = ranked(subtotal, catalog.stores, catalog.cards)
 val current get(): Quote { val s = catalog.stores.firstOrNull { it.id == storeId } ?: rankings.first().store; val c = catalog.cards.firstOrNull { it.id == cardId } ?: rankings.first { it.store.id == s.id }.card; return quote(subtotal,s,c) }
 fun choose(id: String) { newPlan(); recipeId=id; selected=recipe.ingredients.map { it.id }.toSet(); storeId=""; cardId=""; attempt=UUID.randomUUID().toString(); step=1; error=null }
 fun toggle(id: String) { selected=if(id in selected) selected-id else selected+id; attempt=UUID.randomUUID().toString() }
 fun compare() { storeId=rankings.first().store.id; cardId=rankings.first().card.id; step=2 }
 fun checkout(store: String) { storeId=store; cardId=rankings.first { it.store.id==store }.card.id; step=3; error=null; attempt=UUID.randomUUID().toString() }
 fun switchCard(id: String) { cardId=id; attempt=UUID.randomUUID().toString() }
 private suspend fun request(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
  val connection=URL(BuildConfig.BACKEND_URL.trimEnd('/')+path).openConnection() as HttpURLConnection
  try {
   connection.requestMethod="POST"; connection.connectTimeout=10000; connection.readTimeout=95000; connection.doOutput=true
   connection.setRequestProperty("Content-Type","application/json")
   connection.outputStream.use { it.write(payload.toString().toByteArray()) }
   val status=connection.responseCode
   val text=(if(status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
   val result=JSONObject(text)
   check(status in 200..299) { result.optString("error", "Payment service unavailable") }
   result
  } finally { connection.disconnect() }
 }
 fun pay(onReady: (String,String)->Unit) {
  busy=true; error=null; pending=current
  viewModelScope.launch {
   try {
    val result=request("/payment-intent",JSONObject().put("recipeId",recipeId).put("ingredientIds",JSONArray(selected.toList())).put("storeId",storeId).put("cardId",cardId).put("attempt",attempt).put("userId",userId))
    check(result.getString("publishableKey").startsWith("pk_test_")) { "Only Stripe test keys are allowed" }
    check(result.getLong("amount")==pending!!.charge) { "Price changed. Please review your basket." }
    intentId=result.getString("paymentIntentId")
    onReady(result.getString("publishableKey"),result.getString("clientSecret"))
   } catch(e: Exception) { error=e.message ?: "Unable to connect to the demo payment server."; busy=false }
  }
 }
 fun verify() {
  busy=true
  viewModelScope.launch {
   try {
    val result=request("/verify",JSONObject().put("paymentIntentId",intentId))
    check(result.getBoolean("paid")) { "Payment is not confirmed yet. Tap verify to retry." }
    check(result.getLong("amount")==pending?.charge) { "Payment amount did not match the basket." }
    receipt=pending; paymentVerified=true; step=4; intentId=null
   } catch(e: Exception) { error=e.message ?: "Could not verify payment." }
   busy=false
  }
 }
 val canVerify get() = intentId != null
 fun paymentCanceled() { busy=false; error="Payment canceled. Your basket is ready when you are." }
 fun paymentFailed(message: String) { busy=false; error=message }
 fun walkthrough() { receipt=current; paymentVerified=false; step=4; error=null }
 fun restart() { newPlan(); step=0; receipt=null; error=null; busy=false; intentId=null; pending=null; attempt=UUID.randomUUID().toString() }
}
