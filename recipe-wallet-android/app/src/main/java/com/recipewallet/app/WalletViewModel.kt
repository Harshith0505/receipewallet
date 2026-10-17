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
  if(!planWithinBudget || aiBusy || !canEditCheckout) return
  clearPayment()
  recipeId=p.getString("recipeId"); selected=planIds("ingredientIds"); storeId=p.getString("storeId"); cardId=p.getString("cardId")
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
 var awaitingVerification by mutableStateOf(false)
  private set
 var receiptIngredients by mutableStateOf<List<Ingredient>>(emptyList())
  private set
 var receiptRecipeName by mutableStateOf("")
  private set
 var receiptPaymentId by mutableStateOf<String?>(null)
  private set
 private var pending: Quote? = null
 private var pendingPayload: JSONObject? = null
 private var pendingIngredients: List<Ingredient> = emptyList()
 private var pendingRecipeName = ""
 private var verifying = false
 private var intentId: String? = null
 private var attempt = UUID.randomUUID().toString()
 val recipe get() = catalog.recipes.first { it.id == recipeId }
 val subtotal get() = recipe.ingredients.filter { it.id in selected }.sumOf { it.basePrice }
 val rankings get() = ranked(subtotal, catalog.stores, catalog.cards)
 val current get(): Quote { val s = catalog.stores.firstOrNull { it.id == storeId } ?: rankings.first().store; val c = catalog.cards.firstOrNull { it.id == cardId } ?: rankings.first { it.store.id == s.id }.card; return quote(subtotal,s,c) }
 val canEditCheckout get() = !busy && !awaitingVerification
 private fun clearPayment() { pending=null; pendingPayload=null; intentId=null; error=null; attempt=UUID.randomUUID().toString() }
 fun choose(id: String) { if(!canEditCheckout) return; clearPayment(); newPlan(); recipeId=id; selected=recipe.ingredients.map { it.id }.toSet(); storeId=""; cardId=""; step=1 }
 fun toggle(id: String) { if(!canEditCheckout) return; clearPayment(); selected=if(id in selected) selected-id else selected+id }
 fun compare() { storeId=rankings.first().store.id; cardId=rankings.first().card.id; step=2 }
 fun checkout(store: String) { if(!canEditCheckout) return; clearPayment(); storeId=store; cardId=rankings.first { it.store.id==store }.card.id; step=3 }
 fun switchCard(id: String) { if(!canEditCheckout || id==current.card.id) return; clearPayment(); cardId=id }
 fun back() { if(canEditCheckout && step in 1..3) { step--; error=null } }
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
  if(!canEditCheckout || step!=3 || selected.isEmpty()) return
  busy=true; error=null; pending=current
  pendingIngredients=recipe.ingredients.filter { it.id in selected }.toList()
  pendingRecipeName=recipe.name
  val payload=JSONObject().put("recipeId",recipeId).put("ingredientIds",JSONArray(selected.toList())).put("storeId",current.store.id).put("cardId",current.card.id).put("attempt",attempt)
  pendingPayload=payload
  viewModelScope.launch {
   try {
    val result=request("/payment-intent",payload)
    check(result.getString("publishableKey").startsWith("pk_test_")) { "Only Stripe test keys are allowed" }
    check(result.getLong("amount")==pending!!.charge) { "Price changed. Please review your basket." }
    intentId=result.getString("paymentIntentId")
    onReady(result.getString("publishableKey"),result.getString("clientSecret"))
   } catch(e: Exception) { error=e.message ?: "Unable to connect to the demo payment server."; busy=false }
  }
 }
 fun verify() {
  val id=intentId ?: return
  val payload=pendingPayload ?: return
  if(verifying) return
  verifying=true; busy=true; awaitingVerification=true; error=null
  viewModelScope.launch {
   try {
    val result=request("/verify",JSONObject(payload.toString()).put("paymentIntentId",id))
    if(!result.getBoolean("paid")) {
     val status=result.getString("status")
     awaitingVerification=status !in listOf("requires_payment_method","requires_confirmation","requires_action","canceled")
     if(status=="canceled") clearPayment()
     error=if(awaitingVerification) "Your test payment is still processing. Verify its status before trying another payment." else "No test payment completed. Your basket is saved; you can retry checkout."
     return@launch
    }
    check(result.getLong("amount")==pending?.charge) { "Payment amount did not match the basket." }
    check(result.getString("currency")=="usd" && result.getString("paymentIntentId")==id) { "Payment receipt did not match this checkout." }
    receipt=pending; receiptIngredients=pendingIngredients; receiptRecipeName=pendingRecipeName; receiptPaymentId=id
    paymentVerified=true; step=4; intentId=null; awaitingVerification=false
   } catch(e: Exception) { error="We couldn't verify the test payment. Don't pay again yet. Retry verification. ${e.message ?: "Check the backend connection."}" }
   finally { busy=false; verifying=false }
  }
 }
 val canVerify get() = intentId != null
 fun paymentCanceled() { busy=false; error="Payment canceled. Your basket is ready when you are." }
 fun paymentFailed(message: String) { busy=false; awaitingVerification=intentId!=null; error="$message Verify the payment status before retrying." }
 fun walkthrough() { if(!canEditCheckout) return; receipt=current; receiptIngredients=recipe.ingredients.filter { it.id in selected }; receiptRecipeName=recipe.name; receiptPaymentId=null; paymentVerified=false; step=4; error=null }
 fun restart() { if(!canEditCheckout) return; newPlan(); step=0; receipt=null; receiptIngredients=emptyList(); receiptRecipeName=""; receiptPaymentId=null; paymentVerified=false; clearPayment() }
}
