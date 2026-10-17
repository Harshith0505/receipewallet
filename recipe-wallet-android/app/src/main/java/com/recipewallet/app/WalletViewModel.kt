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
 var personalRecipe by mutableStateOf<PersonalRecipe?>(null)
  private set
 var productMatches by mutableStateOf<Map<String, String>>(emptyMap())
  private set
 var packCounts by mutableStateOf<Map<String, Int>>(emptyMap())
  private set
 val isPersonal get() = personalRecipe != null
 fun productFor(lineId: String) = catalog.products.firstOrNull { it.id == productMatches[lineId] }
 fun choosePersonal(value: PersonalRecipe) {
  newPlan(); personalRecipe=value; recipeId="saved:${value.id}"
  productMatches=value.ingredients.mapIndexedNotNull { index,line -> suggestProduct(line,catalog.products)?.let { "line-$index" to it.id } }.toMap()
  packCounts=value.ingredients.indices.associate { "line-$it" to 1 }
  selected=value.ingredients.indices.map { "line-$it" }.toSet()
  storeId=""; cardId=""; error=null; attempt=UUID.randomUUID().toString(); step=1
 }
 fun matchProduct(lineId: String, productId: String) {
  require(catalog.products.any { it.id==productId })
  productMatches=productMatches+(lineId to productId); attempt=UUID.randomUUID().toString()
 }
 fun setPacks(lineId: String, count: Int) {
  packCounts=packCounts+(lineId to count.coerceIn(1,20)); attempt=UUID.randomUUID().toString()
 }
 val canCompare get() = selected.isNotEmpty() && (!isPersonal || selected.all { productFor(it)!=null })
 fun customBasketJson(): JSONObject {
  check(isPersonal && canCompare) { "Match every included ingredient to a product first." }
  val quantities=selected.groupBy { productMatches.getValue(it) }.mapValues { (_, ids) -> ids.sumOf { packCounts[it] ?: 1 } }
  check(quantities.values.all { it in 1..20 }) { "Choose no more than 20 packs of the same product." }
  return JSONObject().put("items",JSONArray(quantities.map { (id,count) -> JSONObject().put("productId",id).put("quantity",count) }))
 }
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
    val payload=JSONObject().put("message",message)
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
  personalRecipe=null; recipeId=p.getString("recipeId"); selected=planIds("ingredientIds"); storeId=p.getString("storeId"); cardId=p.getString("cardId")
  attempt=UUID.randomUUID().toString(); error=null; step=3
 }
 fun newPlan() { agentSession=null; agentPlan=null; agentReply=""; agentTrace=emptyList(); aiError=null; preference="" }
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
 val recipe get() = personalRecipe?.let { saved ->
  Recipe("saved:${saved.id}",saved.name,"","Your saved recipe",saved.prepMinutes ?: 0,
   saved.ingredients.mapIndexed { index,line ->
    val id="line-$index"; val product=productFor(id); val packs=packCounts[id] ?: 1
    Ingredient(id,line,product?.let { "$packs × ${it.pack}" } ?: "Choose a product",(product?.price ?: 0L)*packs)
   })
 } ?: catalog.recipes.first { it.id == recipeId }
 val subtotal get() = recipe.ingredients.filter { it.id in selected }.sumOf { it.basePrice }
 val rankings get() = ranked(subtotal, catalog.stores, catalog.cards)
 val current get(): Quote { val s = catalog.stores.firstOrNull { it.id == storeId } ?: rankings.first().store; val c = catalog.cards.firstOrNull { it.id == cardId } ?: rankings.first { it.store.id == s.id }.card; return quote(subtotal,s,c) }
 fun choose(id: String) { newPlan(); personalRecipe=null; recipeId=id; selected=recipe.ingredients.map { it.id }.toSet(); storeId=""; cardId=""; attempt=UUID.randomUUID().toString(); step=1; error=null }
 fun toggle(id: String) { selected=if(id in selected) selected-id else selected+id; attempt=UUID.randomUUID().toString() }
 fun compare() { if(!canCompare) return; if(isPersonal) { try { customBasketJson() } catch(e: Exception) { error=e.message; return } }; error=null; storeId=rankings.first().store.id; cardId=rankings.first().card.id; step=2 }
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
  if(busy || !canCompare) return
  busy=true; error=null; pending=current
  viewModelScope.launch {
   try {
    val payload=JSONObject().put("recipeId",recipeId).put("ingredientIds",JSONArray(selected.toList())).put("storeId",storeId).put("cardId",cardId).put("attempt",attempt)
    if(isPersonal) payload.put("customBasket",customBasketJson())
    val result=request("/payment-intent",payload)
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
 fun restart() { newPlan(); personalRecipe=null; step=0; receipt=null; error=null; busy=false; intentId=null; pending=null; attempt=UUID.randomUUID().toString() }
}
