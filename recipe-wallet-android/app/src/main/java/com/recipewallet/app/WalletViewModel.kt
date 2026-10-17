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
 var aiReason by mutableStateOf<String?>(null)
 var aiRecipeId by mutableStateOf<String?>(null)
 var aiError by mutableStateOf<String?>(null)
 fun suggest() {
  aiBusy=true; aiError=null; aiReason=null; aiRecipeId=null
  viewModelScope.launch {
   try {
    val result=request("/recommend",JSONObject().put("preference",preference))
    val id=result.getString("recipeId")
    check(catalog.recipes.any { it.id==id }) { "Unable to match a recipe." }
    aiRecipeId=id; aiReason=result.getString("reason")
   } catch(e: Exception) { aiError=e.message ?: "AI is unavailable. Choose a recipe below." }
   aiBusy=false
  }
 }
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
 fun choose(id: String) { recipeId=id; selected=recipe.ingredients.map { it.id }.toSet(); storeId=""; cardId=""; attempt=UUID.randomUUID().toString(); step=1; error=null }
 fun toggle(id: String) { selected=if(id in selected) selected-id else selected+id; attempt=UUID.randomUUID().toString() }
 fun compare() { storeId=rankings.first().store.id; cardId=rankings.first().card.id; step=2 }
 fun checkout(store: String) { storeId=store; cardId=rankings.first { it.store.id==store }.card.id; step=3; error=null; attempt=UUID.randomUUID().toString() }
 fun switchCard(id: String) { cardId=id; attempt=UUID.randomUUID().toString() }
 private suspend fun request(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
  val connection=URL(BuildConfig.BACKEND_URL.trimEnd('/')+path).openConnection() as HttpURLConnection
  try {
   connection.requestMethod="POST"; connection.connectTimeout=10000; connection.readTimeout=20000; connection.doOutput=true
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
    val result=request("/payment-intent",JSONObject().put("recipeId",recipeId).put("ingredientIds",JSONArray(selected.toList())).put("storeId",storeId).put("cardId",cardId).put("attempt",attempt))
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
 fun restart() { step=0; receipt=null; error=null; busy=false; intentId=null; pending=null; attempt=UUID.randomUUID().toString() }
}
