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

internal data class MealMood(val name:String,val emoji:String,val subtitle:String,val prompt:String)
internal val mealMoods=listOf(
 MealMood("Cozy","🍲","A little comfort on a long day","comforting and familiar"),
 MealMood("Fresh","🥑","Bright flavors. A fresh start.","fresh vegetable-forward food"),
 MealMood("Adventure","🌶️","Try a different favorite tonight","bold, spicy flavors"),
 MealMood("Low effort","⚡","Less prep. More time for you.","the quickest easy meal"))

internal class MealStudioState(private val context:Context,private val catalog:Catalog,private val scope:CoroutineScope,private val request:suspend(String,JSONObject)->JSONObject) {
 private val saved=context.getSharedPreferences("meal-studio-v1",Context.MODE_PRIVATE)
 private fun ids(key:String)=runCatching { JSONArray(saved.getString(key,"[]")).let { a->List(a.length()){a.getString(it)} } }.getOrDefault(emptyList())
 val ingredients=catalog.recipes.flatMap { it.ingredients }.distinctBy { it.id }
 var history by mutableStateOf(ids("history").filter { id->catalog.recipes.any { it.id==id } });private set
 var owned by mutableStateOf(ids("owned").filter { id->ingredients.any { it.id==id } }.toSet());private set
 var mood by mutableIntStateOf(saved.getInt("mood",0).coerceIn(0,3));private set
 var minutes by mutableFloatStateOf(saved.getFloat("minutes",35f).coerceIn(15f,45f));private set
 var people by mutableIntStateOf(saved.getInt("people",2).coerceIn(1,12));private set
 var vegetarian by mutableStateOf(saved.getBoolean("vegetarian",false));private set
 var novelty by mutableFloatStateOf(saved.getFloat("novelty",.5f).coerceIn(0f,1f));private set
 var budget by mutableFloatStateOf(saved.getFloat("budget",35f).coerceIn(10f,80f));private set
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
 fun toggleOwned(id:String){if(ingredients.none { it.id==id })return;owned=if(id in owned)owned-id else owned+id;saved.edit().putString("owned",JSONArray(owned.toList()).toString()).apply()}
 fun clearHistory(){history=emptyList();saved.edit().remove("history").apply()}
 fun toggleScan(id:String){scanSelected=if(id in scanSelected)scanSelected-id else scanSelected+id}
 fun confirmScan(){owned=owned+scanSelected;saved.edit().putString("owned",JSONArray(owned.toList()).toString()).apply();scanIds=emptySet();scanSelected=emptySet()}
 fun discardScan(){scanIds=emptySet();scanSelected=emptySet();scanError=null}
 fun clearPhoto(){photoPath?.let { File(it).delete() };photoPath=null;saved.edit().remove("photo").apply();discardScan()}
 fun scan(bitmap:Bitmap){
  if(scanBusy)return
  val scale=minOf(1f,1000f/maxOf(bitmap.width,bitmap.height))
  val image=Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true)
  val bytes=ByteArrayOutputStream().apply { image.compress(Bitmap.CompressFormat.JPEG,75,this) }.toByteArray()
  val file=File(context.filesDir,"pantry-latest.jpg");file.writeBytes(bytes);photoPath=file.absolutePath;saved.edit().putString("photo",photoPath).apply()
  scanBusy=true;scanError=null;scanIds=emptySet();scanSelected=emptySet()
  scope.launch {
   try {val result=request("/pantry-scan",JSONObject().put("mimeType","image/jpeg").put("image",Base64.encodeToString(bytes,Base64.NO_WRAP)))
    val a=result.getJSONArray("ingredientIds");scanIds=List(a.length()){a.getString(it)}.filter { id->ingredients.any { it.id==id } }.toSet();scanSelected=scanIds
    if(scanIds.isEmpty())scanError="No supported ingredients were clear enough. Add items below."
   }catch(e:Exception){scanError=e.message?:"Scan unavailable. Add pantry items manually."}
   finally {scanBusy=false}
  }
 }
 fun buildWeek(){
  if(weekBusy)return;weekBusy=true;weekError=null
  scope.launch {try {
   val result=request("/weekly-plan",JSONObject().put("preferences",prompt()).put("history",JSONArray(history)).put("pantryIds",JSONArray(owned.toList())).put("vegetarian",vegetarian).put("maxMinutes",minutes.toInt()))
   val a=result.getJSONArray("recipeIds");val ids=List(a.length()){a.getString(it)}
   check(ids.size==5&&ids.all { id->catalog.recipes.any { it.id==id } }) { "Unsupported weekly plan. Try again." }
   weeklyIds=ids;weekSource=result.getString("source");weekStamp=currentWeek;saveWeek()
  }catch(e:Exception){weekError=e.message?:"Connect the backend to build your week."}finally{weekBusy=false}}
 }
 fun buildLocalWeek(){
  val recipes=rankedRecipes();if(recipes.isEmpty())return
  weeklyIds=List(5){recipes[it%recipes.size].id};weekSource="local";weekStamp=currentWeek;weekError=null;saveWeek()
 }
 fun swapDay(index:Int){val options=rankedRecipes();if(index !in weeklyIds.indices||options.isEmpty())return;val next=options[(options.indexOfFirst { it.id==weeklyIds[index] }+1)%options.size];weeklyIds=weeklyIds.toMutableList().apply { set(index,next.id) };saveWeek()}
 private fun saveWeek(){saved.edit().putString("week",JSONArray(weeklyIds).toString()).putString("weekSource",weekSource).putString("weekStamp",weekStamp).apply()}
 fun rankedRecipes()=catalog.recipes.filter { (!vegetarian||it.id!="pasta")&&it.prepTime<=minutes.toInt() }.sortedByDescending { r->r.ingredients.count { it.id in owned }*3+history.count { it==r.id }*(if(novelty>.65f)-1 else 1)+(when(mood){0->if(r.id=="pasta")4 else 0;1->if(r.id=="bowl")4 else 0;2->if(r.id=="noodles")4 else 0;else->45-r.prepTime}) }
 fun prompt():String=buildString {
  append("I want ${mealMoods[mood].prompt}, for $people people, under $${budget.toInt()}, at most ${minutes.toInt()} minutes.")
  append(if(novelty>.65f)" I want something different from my recent choices." else " Familiar flavors are welcome.")
  if(vegetarian)append(" Vegetarian only.")
  if(owned.isNotEmpty())append(" I already have "+ingredients.filter { it.id in owned }.joinToString { it.name }+".")
  favorite?.let { append(" I often choose ${it.name}; suggest something that fits those tastes.") }
 }
 fun weeklyRecipe():Recipe? {
  if(weeklyIds.isEmpty())return null
  val entries=weeklyIds.flatMap { id->catalog.recipes.first { it.id==id }.let { r->r.ingredients.map { i->i.copy(quantity=kotlin.math.ceil(i.quantity.toDouble()*people/r.baseServings).toLong()) } } }.groupBy { it.id }.map { (_,items)->items.first().copy(quantity=items.sumOf { it.quantity }) }
  return Recipe("week","Your week","🗓️","Five dinners",0,entries,people)
 }
}
