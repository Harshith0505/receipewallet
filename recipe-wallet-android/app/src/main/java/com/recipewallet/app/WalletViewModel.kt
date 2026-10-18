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
  if(!canEditCheckout) return
  clearPayment(); newPlan(); personalRecipe=value; recipeId="saved:${value.id}"
  productMatches=value.ingredients.mapIndexedNotNull { index,line -> suggestProduct(line,catalog.products)?.let { "line-$index" to it.id } }.toMap()
  packCounts=value.ingredients.indices.associate { "line-$it" to 1 }
  selected=value.ingredients.indices.map { "line-$it" }.toSet()
  storeId=""; cardId=""; error=null; attempt=UUID.randomUUID().toString(); step=1
 }
 fun matchProduct(lineId: String, productId: String) {
  if(!canEditCheckout || lineId !in recipe.ingredients.map { it.id }) return
  clearPayment()
  require(catalog.products.any { it.id==productId })
  productMatches=productMatches+(lineId to productId); attempt=UUID.randomUUID().toString()
 }
 fun setPacks(lineId: String, count: Int) {
  if(!canEditCheckout || lineId !in recipe.ingredients.map { it.id }) return
  clearPayment()
  packCounts=packCounts+(lineId to count.coerceIn(1,20)); attempt=UUID.randomUUID().toString()
 }
 val canCompare get() = selected.isNotEmpty() && (!isPersonal || (selected.all { productFor(it)!=null } && selected.groupBy { productMatches[it] }.values.all { ids -> ids.sumOf { packCounts[it] ?: 1 }<=20 }))
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
 var agentChanges by mutableStateOf<List<String>>(emptyList())
 var agentTrace by mutableStateOf<List<String>>(emptyList())
 var aiError by mutableStateOf<String?>(null)
 private var agentSession: String? = null
 fun suggest() {
  if(aiBusy || preference.isBlank()) return
  val message=preference
  aiBusy=true; aiError=null; agentPlan=null; agentReply=""; agentChanges=emptyList(); agentTrace=emptyList()
  viewModelScope.launch {
   try {
    val payload=JSONObject().put("message",message).put("studentEligible",studentEligible)
    agentSession?.let { payload.put("sessionId",it) }
    val result=request("/plan",payload)
    val plan=result.optJSONObject("plan")
    if(plan!=null) {
     val r=catalog.recipes.first { it.id==plan.getString("recipeId") }
     val ids=plan.getJSONArray("ingredientIds").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
     check(ids.isNotEmpty() && ids.all { id -> r.ingredients.any { it.id==id } }) { "The agent returned an invalid basket. Please retry." }
     val s=catalog.stores.first { it.id==plan.getString("storeId") }
     val c=catalog.cards.first { it.id==plan.getString("cardId") }
     val q=packageQuote(r,ids,s,c,catalog.productOffers,plan.optInt("servings",2),plan.optString("fulfillment","pickup"),plan.optBoolean("organicOnly",false),plan.optBoolean("studentEligible",false))
     check(q.charge==plan.getLong("amount") && q.rewards==plan.getLong("rewards") && q.net==plan.getLong("net")) { "Prices changed. Please plan your shop again." }
    }
    agentPlan=plan; agentReply=result.getString("reply"); agentSession=result.getString("sessionId")
    val changes=result.optJSONArray("changes")
    agentChanges=if(changes!=null) (0 until changes.length()).map { changes.getString(it) } else emptyList()
    val trace=result.getJSONArray("trace")
    agentTrace=(0 until trace.length()).map { trace.getJSONObject(it).getString("summary") }
    preference=""
   } catch(e: Exception) { aiError=e.message ?: "AI is unavailable. Choose a recipe below." }
   aiBusy=false
  }
 }
 val plannedRecipe get() = agentPlan?.let { p -> catalog.recipes.first { it.id==p.getString("recipeId") } }
 fun planIds(key: String): Set<String> = agentPlan?.getJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
 val plannedQuote get() = agentPlan?.let { p -> packageQuote(plannedRecipe!!,planIds("ingredientIds"),catalog.stores.first { it.id==p.getString("storeId") },catalog.cards.first { it.id==p.getString("cardId") },catalog.productOffers,p.optInt("servings",2),p.optString("fulfillment","pickup"),p.optBoolean("organicOnly",false),p.optBoolean("studentEligible",false)) }
 val planWithinBudget get() = plannedQuote?.let { it.complete && (planBudget<0 || it.charge<=planBudget) } == true
 val planBudget get() = agentPlan?.getJSONObject("constraints")?.getLong("budgetCents") ?: -1L
 val planDiet get() = agentPlan?.getJSONObject("constraints")?.getString("diet") ?: "any"
 val planTime get() = agentPlan?.getJSONObject("constraints")?.getInt("maxPrepMinutes") ?: 0
 fun approvePlan() {
  val p=agentPlan ?: return
  if(!planWithinBudget || aiBusy || !canEditCheckout) return
  clearPayment()
  servings=p.optInt("servings",2); fulfillment=p.optString("fulfillment","pickup"); organicOnly=p.optBoolean("organicOnly",false)
  studentEligible=p.optBoolean("studentEligible",false)
  personalRecipe=null; recipeId=p.getString("recipeId"); selected=planIds("ingredientIds"); storeId=p.getString("storeId"); cardId=p.getString("cardId")
  attempt=UUID.randomUUID().toString(); error=null; step=2
 }
 fun newPlan() { servings=2; fulfillment="pickup"; organicOnly=false; agentSession=null; agentPlan=null; agentReply=""; agentChanges=emptyList(); agentTrace=emptyList(); aiError=null; preference="" }
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
 val recipe get() = personalRecipe?.let { saved ->
  Recipe("saved:${saved.id}",saved.name,"","Your saved recipe",saved.prepMinutes ?: 0,
   saved.ingredients.mapIndexed { index,line ->
    val id="line-$index"; val product=productFor(id); val packs=packCounts[id] ?: 1
    Ingredient(id,line,product?.let { "$packs × ${it.pack}" } ?: "Choose a product",(product?.price ?: 0L)*packs)
   })
 } ?: catalog.recipes.first { it.id == recipeId }
 var servings by mutableIntStateOf(2)
  private set
 var fulfillment by mutableStateOf("pickup")
  private set
 var organicOnly by mutableStateOf(false)
  private set
 var studentEligible by mutableStateOf(true)
  private set
 fun changeStudentEligibility(value:Boolean) {
  if(!canEditCheckout || value==studentEligible) return
  clearPayment(); studentEligible=value; receipt=null
 }
 val subtotal get() = if(isPersonal) recipe.ingredients.filter { it.id in selected }.sumOf { it.basePrice } else rankings.firstOrNull { it.complete }?.groceries ?: 0L
 fun groceryQuote(store:Store,card:RewardCard):Quote {
  if(!isPersonal) return packageQuote(recipe,selected,store,card,catalog.productOffers,servings,fulfillment,organicOnly,studentEligible)
  if(!canCompare) return Quote(store,card,0,0,missing=listOf("Match every included ingredient to a product and choose no more than 20 packs."))
  val grouped=selected.groupBy { productMatches.getValue(it) }.map { (id,ids)->catalog.products.first { it.id==id } to ids.sumOf { packCounts[it] ?: 1 } }
  return customQuote(grouped,store,card,fulfillment,studentEligible)
 }
 val rankings get() = catalog.stores.map { s -> catalog.cards.map { groceryQuote(s,it) }.minWith(compareBy<Quote> { it.net }.thenBy { it.card.id }) }.sortedWith(compareBy<Quote> { !it.complete }.thenBy { it.net }.thenBy { it.store.id })
 val current get(): Quote { val s=catalog.stores.firstOrNull { it.id==storeId } ?: rankings.first().store;val c=catalog.cards.firstOrNull { it.id==cardId } ?: catalog.cards.maxBy { it.rewards(groceryQuote(s,it).discountedGroceries) };return groceryQuote(s,c) }
 val checkoutWithinBudget get() = agentPlan==null || planBudget<0 || current.charge<=planBudget
 fun settingsChanged() { if(!canEditCheckout) return; clearPayment(); storeId=""; cardId=""; receipt=null }
 fun changeServings(value:Int) { if(!canEditCheckout || value !in 1..12) return; servings=value; settingsChanged() }
 fun changeFulfillment(value:String) { if(!canEditCheckout || value !in listOf("pickup","delivery")) return; fulfillment=value; settingsChanged() }
 fun changeOrganicOnly(value:Boolean) { if(!canEditCheckout) return; organicOnly=value; settingsChanged() }
 val canEditCheckout get() = !busy && !awaitingVerification
 private fun clearPayment() { pending=null; pendingPayload=null; intentId=null; error=null; attempt=UUID.randomUUID().toString() }
 fun choose(id: String) { if(!canEditCheckout) return; clearPayment(); newPlan(); personalRecipe=null; recipeId=id; selected=recipe.ingredients.map { it.id }.toSet(); storeId=""; cardId=""; step=1 }
 fun toggle(id: String) { if(!canEditCheckout) return; clearPayment(); selected=if(id in selected) selected-id else selected+id }
 fun compare() { if(!canEditCheckout || !canCompare) return; val best=rankings.firstOrNull { it.complete } ?: return; storeId=best.store.id; cardId=best.card.id; step=2 }
 fun checkout(store: String) { if(!canEditCheckout || selected.isEmpty() || rankings.none { it.store.id==store && it.complete }) return; clearPayment(); storeId=store; cardId=rankings.first { it.store.id==store }.card.id; step=3 }
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
  if(!canEditCheckout || step!=3 || !canCompare) return
  if(!current.complete || !checkoutWithinBudget) { error="Review the basket: it must be complete and within your approved budget."; return }
  busy=true; error=null; pending=current
  pendingIngredients=recipe.ingredients.filter { it.id in selected }.toList()
  pendingRecipeName=recipe.name
  val payload=JSONObject().put("recipeId",recipeId).put("ingredientIds",JSONArray(selected.toList())).put("storeId",current.store.id).put("cardId",current.card.id).put("attempt",attempt).put("pricingVersion","packages-v1").put("servings",servings).put("fulfillment",fulfillment).put("organicOnly",organicOnly).put("studentEligible",studentEligible)
  if(isPersonal) payload.put("pricingVersion","custom-packages-v1").put("customBasket",customBasketJson())
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
 fun walkthrough() { if(!canEditCheckout || !current.complete || selected.isEmpty()) return; receipt=current; receiptIngredients=recipe.ingredients.filter { it.id in selected }; receiptRecipeName=recipe.name; receiptPaymentId=null; paymentVerified=false; step=4; error=null }
 fun restart() { if(!canEditCheckout) return; newPlan(); personalRecipe=null; step=0; receipt=null; receiptIngredients=emptyList(); receiptRecipeName=""; receiptPaymentId=null; paymentVerified=false; clearPayment() }
}
