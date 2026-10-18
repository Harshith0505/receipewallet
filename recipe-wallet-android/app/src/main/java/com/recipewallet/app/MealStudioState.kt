package com.recipewallet.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

internal data class MealMood(val name:String,val emoji:String,val subtitle:String,val prompt:String,val color:Long=0xFFF0D2BB,val recipeId:String="pasta")
internal val mealMoods=listOf(
 MealMood("Cozy","","A little comfort on a long day","comforting and familiar",0xFFF6D2B7,"pasta"),
 MealMood("Fresh","","Bright flavors. A fresh start.","fresh vegetable-forward food",0xFFDDE5C2,"bowl"),
 MealMood("Adventure","","Try a different favorite tonight","bold, spicy flavors",0xFFF1D8DF,"noodles"),
 MealMood("Low effort","","Less prep. More time for you.","the quickest easy meal",0xFFE6DDF2,"chickpea-salad"),
 MealMood("Spice craving","","Warm spices, big flavor","a warmly spiced dinner",0xFFF0C3AF,"chana"),
 MealMood("Sunshine","","Colorful, bright and zesty","a bright citrusy meal",0xFFF3DFA8,"mexican-rice"),
 MealMood("Cool & crisp","","Something refreshing tonight","a chilled crunchy meal",0xFFD9E2EC,"sesame-noodles"),
 MealMood("Pantry hero","","Make the most of what is here","a meal using as many pantry ingredients as possible",0xFFE6E1C6,"chickpea-salad"))

internal class MealStudioState(private val context:Context,private val catalog:Catalog,private val scope:CoroutineScope,private val request:suspend(String,JSONObject)->JSONObject) {
 private val saved=context.getSharedPreferences("meal-studio-v1",Context.MODE_PRIVATE)
 private fun ids(key:String)=runCatching { JSONArray(saved.getString(key,"[]")).let { a->List(a.length()){a.getString(it)} } }.getOrDefault(emptyList())
 val ingredients=catalog.recipes.flatMap { it.ingredients }.distinctBy { it.id }
 var pinnedIds by mutableStateOf(ids("pins").toSet());private set
 fun togglePin(id:String){pinnedIds=if(id in pinnedIds)pinnedIds-id else pinnedIds+id;saved.edit().putString("pins",JSONArray(pinnedIds.toList()).toString()).apply()}
 var cuisine by mutableStateOf(saved.getString("cuisine","Any")?:"Any");private set
 fun updateCuisine(value:String){cuisine=value;saved.edit().putString("cuisine",value).apply()}
 var avoid by mutableStateOf(saved.getString("avoid","")?:"");private set
 var faith by mutableStateOf(saved.getString("faith","None")?:"None");private set
 var pickedMood by mutableIntStateOf(saved.getInt("pickedMood",0).coerceIn(0,mealMoods.lastIndex));private set
 fun chooseMood(){pickedMood=mood;saved.edit().putInt("pickedMood",mood).apply()}
 fun updateAvoid(value:String){avoid=value.take(180);saved.edit().putString("avoid",avoid).apply()}
 fun updateFaith(value:String){faith=value;saved.edit().putString("faith",value).apply()}
 fun conflicts(recipe:Recipe)=excludedIngredientNames(recipe.ingredients.map{it.name},avoid)
 fun fits(recipe:Recipe)=conflicts(recipe).isEmpty()
 var history by mutableStateOf(ids("history").filter { id->catalog.recipes.any { it.id==id } });private set
 var owned by mutableStateOf((if(saved.contains("owned")) ids("owned") else context.getSharedPreferences("recipe_wallet_pantry",Context.MODE_PRIVATE).getStringSet("ids",emptySet())?.toList().orEmpty()).filter { id->ingredients.any { it.id==id } }.toSet());private set
 var mood by mutableIntStateOf(saved.getInt("mood",0).coerceIn(0,mealMoods.lastIndex));private set
 var minutes by mutableFloatStateOf(saved.getFloat("minutes",35f).coerceIn(15f,45f));private set
 var people by mutableIntStateOf(saved.getInt("people",2).coerceIn(1,12));private set
 var vegetarian by mutableStateOf(saved.getBoolean("vegetarian",false));private set
 var novelty by mutableFloatStateOf(saved.getFloat("novelty",.5f).coerceIn(0f,1f));private set
 var budget by mutableFloatStateOf(saved.getFloat("budget",35f).coerceIn(10f,80f));private set
 var scanDetections by mutableStateOf<List<PantryDetection>>(emptyList());private set
 var scanIds by mutableStateOf<Set<String>>(emptySet());private set
 var scanSelected by mutableStateOf<Set<String>>(emptySet());private set
 var photoPath by mutableStateOf(saved.getString("photo",null));private set
 var scanBusy by mutableStateOf(false);private set
 var scanError by mutableStateOf<String?>(null);private set
 var weeklyIds by mutableStateOf(ids("week").filter { id->catalog.recipes.any { it.id==id } });private set
 var weekSource by mutableStateOf(saved.getString("weekSource","local")?:"local");private set
 var weekStamp by mutableStateOf(saved.getString("weekStamp","")?:"");private set
 var autoWeek by mutableStateOf(saved.getBoolean("autoWeek",false));private set
 fun updateAutoWeek(value:Boolean){autoWeek=value;saved.edit().putBoolean("autoWeek",value).apply()}
 var autoCart by mutableStateOf(saved.getBoolean("autoCart",false));private set
 var cartIds by mutableStateOf(ids("cartIds").filter{id->catalog.recipes.any{it.id==id}});private set
 var cartPeople by mutableIntStateOf(saved.getInt("cartPeople",people));private set
 fun prepareWeeklyCart(){if(weeklyIds.isEmpty())return;if(weeklyIds.any{id->!fits(catalog.recipes.first{it.id==id})}){weekError="Your plan contains excluded ingredients. Rebuild it with your current filters.";return};cartIds=weeklyIds.toList();cartPeople=people;saved.edit().putString("cartIds",JSONArray(cartIds).toString()).putInt("cartPeople",cartPeople).apply()}
 fun updateAutoCart(value:Boolean){autoCart=value;saved.edit().putBoolean("autoCart",value).apply();if(value)prepareWeeklyCart()}
 val groceryCart get()=weeklyRecipe(cartIds,cartPeople)
 var weekBusy by mutableStateOf(false);private set
 var weekError by mutableStateOf<String?>(null);private set
 val currentWeek get()=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
 val weekDue get()=weekStamp!=currentWeek
 val favorite get()=history.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.let { id->catalog.recipes.firstOrNull { it.id==id } }
 fun updateMood(value:Int){mood=(value+mealMoods.size)%mealMoods.size;saved.edit().putInt("mood",mood).apply()}
 fun updateMinutes(value:Float){minutes=value;saved.edit().putFloat("minutes",value).apply()}
 fun updatePeople(value:Int){people=value.coerceIn(1,12);saved.edit().putInt("people",people).apply()}
 fun updateVegetarian(value:Boolean){vegetarian=value;saved.edit().putBoolean("vegetarian",value).apply()}
 fun updateNovelty(value:Float){novelty=value;saved.edit().putFloat("novelty",value).apply()}
 fun updateBudget(value:Float){budget=value;saved.edit().putFloat("budget",value).apply()}
 fun record(id:String){if(catalog.recipes.none { it.id==id }) return; history=(history+id).takeLast(20);saved.edit().putString("history",JSONArray(history).toString()).apply()}
 fun replaceOwned(value:Set<String>){owned=value.filter { id->ingredients.any { it.id==id } }.toSet();saved.edit().putString("owned",JSONArray(owned.toList()).toString()).apply()}
 private fun matchedPantryIds(names:List<String>)=ingredients.filter { ingredient -> names.any { name ->
  name.equals(ingredient.name,true) || suggestProduct(name,catalog.products)?.id?.let { it==suggestProduct(ingredient.name,catalog.products)?.id }==true
 } }.map{it.id}.toSet()
 fun importSavedPantry(){
  val store=PantryStore(context)
  if(!saved.getBoolean("unifiedPantry",false)){
   store.write(mergePantry(store.read(),ingredients.filter{it.id in owned}.map{PantryItem(java.util.UUID.randomUUID().toString(),it.name)}))
   saved.edit().putBoolean("unifiedPantry",true).apply()
  }
  replaceOwned(matchedPantryIds(store.read().map{it.name}))
 }
 fun confirmOwnedIds(value:Set<String>){
  val next=value.filter{id->ingredients.any{it.id==id}}.toSet()
  val removed=owned-next
  val store=PantryStore(context)
  val kept=store.read().filter{item->matchedPantryIds(listOf(item.name)).none{it in removed}}
  val added=ingredients.filter{it.id in next}.map{PantryItem(java.util.UUID.randomUUID().toString(),it.name)}
  store.write(mergePantry(kept,added));replaceOwned(next)
 }
 fun toggleOwned(id:String){if(ingredients.none{it.id==id})return;confirmOwnedIds(if(id in owned)owned-id else owned+id)}
 fun clearHistory(){history=emptyList();saved.edit().remove("history").apply()}
 fun toggleScan(id:String){scanSelected=if(id in scanSelected)scanSelected-id else scanSelected+id}
 fun selectDetection(id:String,value:Boolean){scanDetections=scanDetections.map{if(it.item.id==id)it.copy(selected=value)else it}}
 fun editDetection(item:PantryItem){scanDetections=scanDetections.map{if(it.item.id==item.id)it.copy(item=item)else it}}
 fun confirmScan(){
  try {
   val store=PantryStore(context);store.write(mergePantry(store.read(),scanDetections.filter{it.selected}.map{it.item}))
   importSavedPantry();discardScan()
  }catch(e:Exception){scanError=e.message?:"Could not save your pantry. Review the entries and retry."}
 }
 fun discardScan(){scanDetections=emptyList();scanIds=emptySet();scanSelected=emptySet();scanError=null}
 fun clearPhoto(){photoPath?.let { File(it).delete() };photoPath=null;saved.edit().remove("photo").apply();discardScan()}
 fun scan(bitmap:Bitmap){
  if(scanBusy)return
  val scale=minOf(1f,1000f/maxOf(bitmap.width,bitmap.height))
  val image=Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true)
  val bytes=ByteArrayOutputStream().apply { image.compress(Bitmap.CompressFormat.JPEG,75,this) }.toByteArray()
  val file=File(context.filesDir,"pantry-latest.jpg");file.writeBytes(bytes);photoPath=file.absolutePath;saved.edit().putString("photo",photoPath).apply()
  scanBusy=true;scanError=null;scanIds=emptySet();scanSelected=emptySet();scanDetections=emptyList()
  scope.launch {
   try {val result=request("/pantry-scan",JSONObject().put("mimeType","image/jpeg").put("image",Base64.encodeToString(bytes,Base64.NO_WRAP)))
    val a=result.getJSONArray("ingredientIds");scanIds=List(a.length()){a.getString(it)}.filter { id->ingredients.any { it.id==id } }.toSet();scanSelected=scanIds
    val rows=result.getJSONArray("ingredients")
    scanDetections=List(rows.length()){index->rows.getJSONObject(index).let{PantryDetection(PantryItem(java.util.UUID.randomUUID().toString(),it.getString("name"),it.getString("amount")))}}
    if(scanDetections.isEmpty())scanError="No food was clear enough. Try another photo or add items manually."
   }catch(e:Exception){scanError=e.message?:"Scan unavailable. Add pantry items manually."}
   finally {scanBusy=false}
  }
 }
 fun buildWeek(){
  if(weekBusy)return;weekBusy=true;weekError=null
  scope.launch {try {
   val result=request("/weekly-plan",JSONObject().put("preferences",prompt()).put("history",JSONArray(history)).put("pantryIds",JSONArray(owned.toList())).put("vegetarian",vegetarian).put("cuisine",cuisine).put("avoid",avoid).put("maxMinutes",minutes.toInt()))
   val a=result.getJSONArray("recipeIds");val ids=List(a.length()){a.getString(it)}
   check(ids.size==5&&ids.all { id->catalog.recipes.any { it.id==id && (cuisine=="Any"||recipeCuisine(it.id)==cuisine) && fits(it) && (!vegetarian||it.id!="pasta") } }) { "Unsupported weekly plan. Try again." }
   weeklyIds=ids;weekSource=result.getString("source");weekStamp=currentWeek;saveWeek()
  }catch(e:Exception){weekError=e.message?:"Connect the backend to build your week."}finally{weekBusy=false}}
 }
 fun buildLocalWeek(){
  val recipes=rankedRecipes();if(recipes.isEmpty()){weekError="No catalog recipe matches these exclusions. Revise your filters.";return}
  weeklyIds=List(5){recipes[it%recipes.size].id};weekSource="local";weekStamp=currentWeek;weekError=null;saveWeek()
 }
 fun swapDay(index:Int){val options=rankedRecipes();if(index !in weeklyIds.indices||options.isEmpty())return;val next=options[(options.indexOfFirst { it.id==weeklyIds[index] }+1)%options.size];weeklyIds=weeklyIds.toMutableList().apply { set(index,next.id) };saveWeek()}
 private fun saveWeek(){if(autoCart)prepareWeeklyCart();saved.edit().putString("week",JSONArray(weeklyIds).toString()).putString("weekSource",weekSource).putString("weekStamp",weekStamp).apply()}
 fun rankedRecipes()=catalog.recipes.filter { (cuisine=="Any"||recipeCuisine(it.id)==cuisine)&&fits(it)&&(!vegetarian||it.id!="pasta")&&it.prepTime<=minutes.toInt() }.sortedByDescending { r->r.ingredients.count { it.id in owned }*3+history.count { it==r.id }*(if(novelty>.65f)-1 else 1)+(if(r.id==mealMoods[pickedMood].recipeId)4 else if(pickedMood==3)45-r.prepTime else 0) }
 fun estimate(recipe:Recipe)=catalog.stores.map { store->packageQuote(recipe,recipe.ingredients.map { it.id }.toSet()-owned,store,catalog.cards.first(),catalog.productOffers,people,"pickup",false) }.filter { it.complete }.minByOrNull { it.charge }
 fun prompt():String=buildString {
  append("I want ${mealMoods[pickedMood].prompt}, for $people people, under $${budget.toInt()}, at most ${minutes.toInt()} minutes.")
  append(if(novelty>.65f)" I want something different from my recent choices." else " Familiar flavors are welcome.")
  if(cuisine!="Any")append(" Prefer $cuisine-inspired recipes.")
  if(avoid.isNotBlank())append(" Exclude these allergens or ingredients: $avoid. Check product labels and cross-contact separately.")
  if(faith!="None")append(" $faith preferences: flag ingredients requiring certification review; do not claim certification.")
  if(vegetarian)append(" Vegetarian only.")
  if(owned.isNotEmpty())append(" I already have "+ingredients.filter { it.id in owned }.joinToString { it.name }+".")
  favorite?.let { append(" I often choose ${it.name}; suggest something that fits those tastes.") }
 }
 init {runCatching{importSavedPantry()}.onFailure{scanError="Could not load your saved pantry. Review the detailed pantry editor."}}
 fun weeklyRecipe(planIds:List<String> = weeklyIds,planPeople:Int = people):Recipe? {
  if(planIds.isEmpty())return null
  val entries=planIds.flatMap { id->catalog.recipes.first { it.id==id }.let { r->r.ingredients.map { i->i.copy(quantity=kotlin.math.ceil(i.quantity.toDouble()*planPeople/r.baseServings).toLong()) } } }.groupBy { it.id }.map { (_,items)->items.first().copy(quantity=items.sumOf { it.quantity }) }
  return Recipe("week","Your week","🗓️","Five dinners",0,entries,planPeople)
 }
}
