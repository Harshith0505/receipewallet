package com.recipewallet.app

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.floor

/** A meal is journaled only after the person explicitly says how much they ate. */
internal data class EatenMeal(val id:String,val recipeId:String,val date:String,val servings:Double,val ingredientIds:List<String>) {
 fun json()=JSONObject().put("id",id).put("recipeId",recipeId).put("date",date).put("servings",servings).put("ingredientIds",JSONArray(ingredientIds))
}
internal data class MealNutrition(val proteinG:Double=0.0,val carbsG:Double=0.0,val sugarG:Double=0.0,val fatG:Double=0.0,val fiberG:Double=0.0,val kcal:Double=0.0) {
 operator fun plus(other:MealNutrition)=MealNutrition(proteinG+other.proteinG,carbsG+other.carbsG,sugarG+other.sugarG,fatG+other.fatG,fiberG+other.fiberG,kcal+other.kcal)
 operator fun times(scale:Double)=MealNutrition(proteinG*scale,carbsG*scale,sugarG*scale,fatG*scale,fiberG*scale,kcal*scale)
 fun rounded()=MealNutrition(oneDecimal(proteinG),oneDecimal(carbsG),oneDecimal(sugarG),oneDecimal(fatG),oneDecimal(fiberG),oneDecimal(kcal))
 fun json()=JSONObject().put("proteinG",proteinG).put("carbsG",carbsG).put("sugarG",sugarG).put("fatG",fatG).put("fiberG",fiberG).put("kcal",kcal)
}
internal fun oneDecimal(n:Double)=floor(n*10.0+.5)/10.0
internal data class IngredientNutrition(val id:String,val basis:Double,val unit:String,val macros:MealNutrition,val assumption:String)
internal data class FoodWeek(val weekStart:LocalDate,val entries:List<EatenMeal>,val totals:MealNutrition,val daysLogged:Int,val servings:Double,val ingredientCount:Int,val favoriteRecipeId:String?)
internal fun mondayOf(date:LocalDate)=date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
internal fun nutritionTable(data:JSONObject):Map<String,IngredientNutrition> {
 val rows=data.getJSONArray("ingredients")
 return List(rows.length()){n->val row=rows.getJSONObject(n)
  IngredientNutrition(row.getString("id"),row.getDouble("basisQuantity"),row.getString("unit"),MealNutrition(row.getDouble("proteinG"),row.getDouble("carbsG"),row.getDouble("sugarG"),row.getDouble("fatG"),row.getDouble("fiberG"),row.getDouble("kcal")),row.getString("assumption"))
 }.associateBy { it.id }
}
internal fun validateEatenMeal(entry:EatenMeal,recipes:List<Recipe>,nutrition:Map<String,IngredientNutrition>) {
 require(entry.id.matches(Regex("^[a-zA-Z0-9_-]{1,80}$"))) { "Give this meal a valid identifier." }
 val recipe=recipes.firstOrNull{it.id==entry.recipeId}?:error("Choose a supported recipe for nutrition estimates.")
 LocalDate.parse(entry.date)
 require(entry.servings.isFinite()&&entry.servings in .25..12.0){"Enter between 0.25 and 12 servings actually eaten."}
 require(entry.ingredientIds.isNotEmpty()&&entry.ingredientIds.distinct().size==entry.ingredientIds.size){"Include at least one ingredient; remove duplicates."}
 require(entry.ingredientIds.all { id->recipe.ingredients.any{it.id==id} }){"Only this recipe's ingredients can be logged."}
 entry.ingredientIds.forEach{id->val ingredient=recipe.ingredients.first{it.id==id};val basis=nutrition[id]?:error("Nutrition estimate unavailable for this ingredient.")
  require(basis.basis>0&&basis.basis.isFinite()&&ingredient.unit==basis.unit){"Nutrition units do not match this recipe."}
 }
}
internal fun foodWeek(weekStart:LocalDate,entries:List<EatenMeal>,recipes:List<Recipe>,nutrition:Map<String,IngredientNutrition>):FoodWeek {
 require(weekStart.dayOfWeek==DayOfWeek.MONDAY){"A food week starts on Monday."}
 val selected=entries.filter{val date=LocalDate.parse(it.date);!date.isBefore(weekStart)&&!date.isAfter(weekStart.plusDays(6))}
 require(selected.map{it.id}.distinct().size==selected.size){"Duplicate meal identifier."}
 var totals=MealNutrition()
 selected.forEach{entry->validateEatenMeal(entry,recipes,nutrition);val recipe=recipes.first{it.id==entry.recipeId};var mealTotals=MealNutrition()
  recipe.ingredients.filter{it.id in entry.ingredientIds}.map{it.id}.forEach{id->val ingredient=recipe.ingredients.first{it.id==id};val basis=nutrition.getValue(id)
   mealTotals+=basis.macros*(ingredient.quantity.toDouble()*entry.servings/recipe.baseServings/basis.basis)
  };totals+=mealTotals
 }
 return FoodWeek(weekStart,selected.sortedWith(compareByDescending<EatenMeal>{it.date}.thenBy{it.id}),totals.rounded(),selected.map{it.date}.distinct().size,floor(selected.sumOf{it.servings}*100+.5)/100.0,selected.flatMap{it.ingredientIds}.distinct().size,selected.groupingBy{it.recipeId}.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String,Int>>{it.value}.thenBy{it.key}).firstOrNull()?.key)
}
internal fun readEatenMeals(raw:String,recipes:List<Recipe>,nutrition:Map<String,IngredientNutrition>):List<EatenMeal> {
 val rows=JSONArray(raw)
 return List(rows.length()){index->val r=rows.getJSONObject(index);val ids=r.getJSONArray("ingredientIds")
  EatenMeal(r.getString("id"),r.getString("recipeId"),r.getString("date"),r.getDouble("servings"),List(ids.length()){ids.getString(it)}).also{validateEatenMeal(it,recipes,nutrition)}
 }.also{require(it.map{meal->meal.id}.distinct().size==it.size)}
}

internal class NutritionWrapState(context:Context,private val catalog:Catalog,private val scope:CoroutineScope,private val request:suspend(String,JSONObject)->JSONObject) {
 private val saved=context.getSharedPreferences("food-journal-v1",Context.MODE_PRIVATE)
 val reference=JSONObject(context.assets.open("nutrition.json").bufferedReader().use{it.readText()})
 private val nutrition=nutritionTable(reference)
 var journalWarning by mutableStateOf<String?>(null);private set
 var entries by mutableStateOf(runCatching{readEatenMeals(saved.getString("entries","[]")?:"[]",catalog.recipes,nutrition)}.getOrElse{journalWarning="Your saved journal couldn't be read. It hasn't been overwritten; add a new meal only if you want to start a fresh journal.";emptyList()});private set
 var previousWeek by mutableStateOf(false);private set
 var sample by mutableStateOf(false);private set
 var busy by mutableStateOf(false);private set
 var error by mutableStateOf<String?>(null);private set
 var agentResult by mutableStateOf<JSONObject?>(null);private set
 private var agentPreferences:Pair<String,Boolean>?=null
 private var revision=0
 val weekStart get()=mondayOf(LocalDate.now()).minusWeeks(if(previousWeek)1 else 0)
 val selectedEntries get()=if(sample)sampleEntries()else entries
 val report get()=foodWeek(weekStart,selectedEntries,catalog.recipes,nutrition)
 val realReport get()=foodWeek(weekStart,entries,catalog.recipes,nutrition)
 fun selectWeek(previous:Boolean){previousWeek=previous;invalidate()}
 fun previewSample(value:Boolean){sample=value;invalidate()}
 private fun invalidate(){revision++;agentResult=null;error=null}
 // A completed or in-flight story must never become actionable for different preferences.
 fun agentFor(preferences:String,vegetarian:Boolean)=agentResult?.takeIf{agentPreferences==(preferences.take(600) to vegetarian)}
 fun saveMeal(id:String?,recipeId:String,date:String,servings:Double,ids:List<String>):Boolean=try {
  check(!sample){"Leave sample mode before editing your journal."}
  val entry=EatenMeal(id?:UUID.randomUUID().toString(),recipeId,date,servings,ids)
  validateEatenMeal(entry,catalog.recipes,nutrition)
  val day=LocalDate.parse(date)
  require(!day.isBefore(weekStart)&&!day.isAfter(weekStart.plusDays(6))){"Choose a date inside the selected week."}
  require(!day.isAfter(LocalDate.now())){"Log a meal after you eat it. Future plans belong in My week."}
  val next=entries.filter{it.id!=entry.id}+entry
  require(next.count{mondayOf(LocalDate.parse(it.date))==weekStart}<=100){"This week already has 100 logged meals."}
  entries=next;persist();invalidate();true
 }catch(e:Exception){error=e.message?:"Check the date and serving amount.";false}
 fun removeMeal(id:String){if(sample)return;entries=entries.filter{it.id!=id};persist();invalidate()}
 private fun persist(){saved.edit().putString("entries",JSONArray(entries.map{it.json()}).toString()).apply();journalWarning=null}
 private fun sampleEntries():List<EatenMeal> {
  val recipes=catalog.recipes
  return List(6){n->val recipe=recipes[n%recipes.size];EatenMeal("sample-$n",recipe.id,weekStart.plusDays((n%4).toLong()).toString(),if(n==4)1.5 else 1.0,recipe.ingredients.map{it.id})}
 }
 fun askAgent(preferences:String,vegetarian:Boolean=false) {
  if(busy||report.entries.isEmpty())return
  val snapshot=report;val token=revision;val payload=JSONObject().put("weekStart",snapshot.weekStart.toString()).put("entries",JSONArray(snapshot.entries.map{it.json()})).put("preferences",preferences.take(600)).put("diet",if(vegetarian)"vegetarian"else "any")
  busy=true;error=null
  agentResult=null;agentPreferences=preferences.take(600) to vegetarian
  scope.launch{try{
   val response=request("/weekly-wrap",payload)
   val total=response.getJSONObject("totals");val local=snapshot.totals.json()
   listOf("proteinG","carbsG","sugarG","fatG","fiberG","kcal").forEach{key->check(kotlin.math.abs(total.getDouble(key)-local.getDouble(key))<=.11){"The agent's totals didn't match your journal. Local estimates remain available."}}
   check(response.getString("weekStart")==snapshot.weekStart.toString()){"The food agent returned another week. Try again."}
   response.optString("nextRecipeId").takeIf{it.isNotBlank()&&it!="null"}?.let{id->check(catalog.recipes.any{it.id==id}&&(!vegetarian||id!="pasta")){"The suggested recipe doesn't match your dietary preferences."}}
   if(token==revision)agentResult=response
  }catch(e:Exception){if(token==revision)error=e.message?:"Food agent unavailable. Your local story still works."}finally{busy=false}}
 }
 fun assumption(id:String)=nutrition[id]?.assumption?:"Estimate unavailable"
}
