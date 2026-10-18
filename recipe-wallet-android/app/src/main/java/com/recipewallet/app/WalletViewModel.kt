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
 internal val wrap = NutritionWrapState(app,catalog,viewModelScope) { path,payload -> request(path,payload) }
 internal val studio = MealStudioState(app,catalog,viewModelScope) { path,payload -> request(path,payload) }
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
 val canCompare get() = studio.fits(recipe) && selected.isNotEmpty() && (!isPersonal || (selected.all { productFor(it)!=null } && selected.groupBy { productMatches[it] }.values.all { ids -> ids.sumOf { packCounts[it] ?: 1 }<=20 }))
 fun customBasketJson(): JSONObject {
  check(isPersonal && canCompare) { "Match every included ingredient to a product first." }
  val quantities=selected.groupBy { productMatches.getValue(it) }.mapValues { (_, ids) -> ids.sumOf { packCounts[it] ?: 1 } }
  check(quantities.values.all { it in 1..20 }) { "Choose no more than 20 packs of the same product." }
  return JSONObject().put("items",JSONArray(quantities.map { (id,count) -> JSONObject().put("productId",id).put("quantity",count) }))
 }
 var preference by mutableStateOf("")
 var aiBusy by mutableStateOf(false)
 var generatedRecipe by mutableStateOf<JSONObject?>(null)
 private var personalBudget by mutableLongStateOf(-1L)
 var agentPlan by mutableStateOf<JSONObject?>(null)
 var agentReply by mutableStateOf("")
 var agentChanges by mutableStateOf<List<String>>(emptyList())
 var agentTrace by mutableStateOf<List<String>>(emptyList())
 var aiError by mutableStateOf<String?>(null)
 private var agentSession: String? = null
 fun suggest() {
  if(aiBusy || preference.isBlank()) return
  val profileContext=if(studio.avoid.isNotBlank()||studio.faith!="None"||studio.cuisine!="Any") "\nCuisine preference: ${studio.cuisine}. Dietary constraints: avoid ${studio.avoid}. ${studio.faith} preferences require product certification review." else ""
  val pantryNames=(studio.ingredients.filter{it.id in studio.owned}.map{it.name}+PantryStore(getApplication()).read().map{it.name}).distinct().take(40)
  val pantryContext=if(pantryNames.isNotEmpty()) "\nConfirmed pantry: "+pantryNames.joinToString().take(500)+". Review sufficient quantity; only exclude ingredients used by this meal." else ""
  val message=preference+profileContext+pantryContext
  aiBusy=true; aiError=null; agentPlan=null; generatedRecipe=null; agentReply=""; agentChanges=emptyList(); agentTrace=emptyList()
  viewModelScope.launch {
   try {
    val payload=JSONObject().put("message",message).put("studentEligible",studentEligible).put("userId",userId)
    agentSession?.let { payload.put("sessionId",it) }
    val result=request("/plan",payload)
    val plan=result.optJSONObject("plan")
    if(plan!=null) {
     val r=catalog.recipes.first { it.id==plan.getString("recipeId") }
     check(studio.fits(r)&&(studio.cuisine=="Any"||recipeCuisine(r.id)==studio.cuisine)) { "The suggested recipe does not match your cuisine or ingredient filters. Try another recipe." }
     val ids=plan.getJSONArray("ingredientIds").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
     check(ids.isNotEmpty() && ids.all { id -> r.ingredients.any { it.id==id } }) { "The agent returned an invalid basket. Please retry." }
     val s=catalog.stores.first { it.id==plan.getString("storeId") }
     val c=catalog.cards.first { it.id==plan.getString("cardId") }
     val q=packageQuote(r,ids,s,c,catalog.productOffers,plan.optInt("servings",2),plan.optString("fulfillment","pickup"),plan.optBoolean("organicOnly",false),plan.optBoolean("studentEligible",false))
     check(q.charge==plan.getLong("amount") && q.rewards==plan.getLong("rewards") && q.net==plan.getLong("net")) { "Prices changed. Please plan your shop again." }
    }
    val draft=result.optJSONObject("generatedRecipe")
    if(draft!=null) {
     val rows=draft.getJSONArray("ingredients")
     val personal=PersonalRecipe(draft.getString("id"),draft.getString("name"),List(rows.length()) { index -> rows.getJSONObject(index).let { "${it.getString("amount")} ${it.getString("name")}" } },draft.getInt("prepMinutes"))
     validatePersonalRecipe(personal)
     check(excludedIngredientNames(personal.ingredients,studio.avoid).isEmpty()) { "The draft contains an excluded ingredient. Revise your request before shopping." }
     check(draft.getInt("servings") in 1..12) { "Review the generated serving count." }
    }
    generatedRecipe=draft
    ownedHints=result.optJSONArray("ownedHints")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> OwnedHint(o.getString("ingredientId"), o.getString("name"), o.getString("text")) } } } ?: emptyList()
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
 val planBudget get() = agentPlan?.getJSONObject("constraints")?.getLong("budgetCents") ?: personalBudget
 val planDiet get() = agentPlan?.getJSONObject("constraints")?.getString("diet") ?: "any"
 val planTime get() = agentPlan?.getJSONObject("constraints")?.getInt("maxPrepMinutes") ?: 0
 fun approvePlan() {
  val p=agentPlan ?: return
  if(!planWithinBudget || aiBusy || !canEditCheckout) return
  studio.record(p.getString("recipeId"))
  clearPayment()
  servings=p.optInt("servings",2); fulfillment=p.optString("fulfillment","pickup"); organicOnly=p.optBoolean("organicOnly",false)
  studentEligible=p.optBoolean("studentEligible",false)
  personalRecipe=null; recipeId=p.getString("recipeId"); selected=planIds("ingredientIds"); storeId=p.getString("storeId"); cardId=p.getString("cardId")
  attempt=UUID.randomUUID().toString(); error=null; step=2
 }
 fun approveGeneratedRecipe() {
  val draft=generatedRecipe ?: return
  if(aiBusy || !canEditCheckout) return
  val rows=draft.getJSONArray("ingredients")
  val value=PersonalRecipe(draft.getString("id"),draft.getString("name"),List(rows.length()) { index -> rows.getJSONObject(index).let { "${it.getString("amount")} ${it.getString("name")}" } },draft.getInt("prepMinutes"))
  val included=(0 until rows.length()).filter { !rows.getJSONObject(it).getBoolean("owned") }.map { "line-$it" }.toSet()
  val budget=draft.getJSONObject("constraints").getLong("budgetCents")
  choosePersonal(value)
  selected=included
  personalBudget=budget
 }
 fun newPlan() { ownedHints=emptyList(); servings=2; fulfillment="pickup"; organicOnly=false; agentSession=null; agentPlan=null; generatedRecipe=null; personalBudget=-1L; agentReply=""; agentChanges=emptyList(); agentTrace=emptyList(); aiError=null; preference="" }

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
 var pantry:Set<String>
  get()=studio.owned
  private set(value){studio.confirmOwnedIds(value)}
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
  if(!canEditCheckout || s.canCookNow || s.missingIds.isEmpty()) return
  choose(s.recipeId)
  selected = s.missingIds.toSet()
  storeId = ""; cardId = ""; attempt = UUID.randomUUID().toString(); step = 1; error = null
 }
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
 var studentEligible by mutableStateOf(false)
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
 val checkoutWithinBudget get() = planBudget<0 || current.charge<=planBudget
 fun settingsChanged() { if(!canEditCheckout) return; clearPayment(); storeId=""; cardId=""; receipt=null }
 fun changeServings(value:Int) { if(!canEditCheckout || value !in 1..12) return; servings=value; settingsChanged() }
 fun changeFulfillment(value:String) { if(!canEditCheckout || value !in listOf("pickup","delivery")) return; fulfillment=value; settingsChanged() }
 fun changeOrganicOnly(value:Boolean) { if(!canEditCheckout) return; organicOnly=value; settingsChanged() }
 val canEditCheckout get() = !busy && !awaitingVerification
 private fun clearPayment() { pending=null; pendingPayload=null; intentId=null; error=null; attempt=UUID.randomUUID().toString() }
 fun choose(id: String) { if(!canEditCheckout) return; if(!studio.fits(catalog.recipes.first{it.id==id})){error="This recipe contains an ingredient in your exclusions.";return}; clearPayment(); newPlan(); personalRecipe=null; recipeId=id; selected=recipe.ingredients.map { it.id }.toSet()-studio.owned; servings=studio.people; studio.record(id); storeId=""; cardId=""; step=1 }
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
  payload.put("userId",userId)
  pendingPayload=payload
  viewModelScope.launch {
   try {
    val result=request("/payment-intent",payload)
    check(result.getString("publishableKey").startsWith("pk_test_")) { "Only Stripe test keys are allowed" }
    check(result.getLong("amount")==pending!!.charge) { "Price changed. Please review your basket." }
    intentId=result.getString("paymentIntentId")
    onReady(result.getString("publishableKey"),result.getString("clientSecret"))
   } catch(e: Exception) { error=e.message ?: "Checkout is unavailable. Please try again."; busy=false }
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
