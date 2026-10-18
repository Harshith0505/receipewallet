package com.recipewallet.app

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject

internal data class MealFood(val name:String,val amount:String,val owned:Boolean=false)
internal data class JournalMeal(val id:String,val name:String,val description:String,val servings:Int,val minutes:Int,val foods:List<MealFood>,val steps:List<String>,val viewedAt:Long,val sampleAt:Long=0,val store:String="",val subtotal:Long=0)
internal class MealJournal(context:Context){
 private val prefs=context.getSharedPreferences("recipe-journal-v1",Context.MODE_PRIVATE)
 var meals by mutableStateOf(read());private set
 private fun read():List<JournalMeal> = runCatching{
  val rows=JSONArray(prefs.getString("meals","[]"));List(rows.length()){i->val r=rows.getJSONObject(i);val f=r.getJSONArray("foods");val steps=r.getJSONArray("steps")
   JournalMeal(r.getString("id"),r.getString("name"),r.optString("description"),r.getInt("servings"),r.getInt("minutes"),List(f.length()){j->f.getJSONObject(j).let{MealFood(it.getString("name"),it.getString("amount"),it.optBoolean("owned"))}},List(steps.length()){steps.getString(it)},r.getLong("viewedAt"),r.optLong("sampleAt"),r.optString("store"),r.optLong("subtotal"))
  }
 }.getOrDefault(emptyList())
 fun remember(meal:JournalMeal){
  val old=meals.firstOrNull{it.id==meal.id}
  val same=old!=null&&old.foods==meal.foods&&old.servings==meal.servings&&old.steps==meal.steps
  val next=if(same)meal.copy(sampleAt=old!!.sampleAt,store=old.store,subtotal=old.subtotal)else meal
  val archive=if(old!=null&&old.sampleAt>0&&!same)listOf(old.copy(id=old.id+":shop:"+old.sampleAt))else emptyList()
  write((listOf(next)+archive+meals.filterNot{it.id==meal.id}).take(40))
 }
 fun completeSample(id:String,store:String,subtotal:Long){
  check(meals.any{it.id==id}){"Review the recipe before sample checkout."}
  write(meals.map{if(it.id==id)it.copy(sampleAt=System.currentTimeMillis(),store=store,subtotal=subtotal)else it})
 }
 private fun write(values:List<JournalMeal>){
  val rows=JSONArray(values.map{m->JSONObject().put("id",m.id).put("name",m.name).put("description",m.description).put("servings",m.servings).put("minutes",m.minutes).put("viewedAt",m.viewedAt).put("sampleAt",m.sampleAt).put("store",m.store).put("subtotal",m.subtotal).put("foods",JSONArray(m.foods.map{JSONObject().put("name",it.name).put("amount",it.amount).put("owned",it.owned)})).put("steps",JSONArray(m.steps))})
  check(prefs.edit().putString("meals",rows.toString()).commit()){"Could not save recipe history on this device. Please retry."}
  meals=values
 }
}
internal fun cookingMethod(id:String):List<String> = when(id){
 "noodles"->listOf("Cook the noodles following the instructions on their packet. Drain, reserving a little cooking water.","Prepare the aromatics and toppings from your reviewed ingredient list. Gently warm the included aromatics in sesame oil.","Toss the noodles with the included sauce ingredients. Loosen with a little reserved cooking water if needed.","Divide between bowls and finish with your included toppings. Leave out any ingredients you removed during remix.")
 "bowl"->listOf("Cook the brown rice according to its packet instructions.","Drain and rinse the chickpeas. Wash the greens and slice the avocado, if included.","Arrange the rice, greens and chickpeas in bowls. Add the remaining ingredients from your reviewed list.","Finish with the included lemon and serve. Adjust portions for your household.")
 "pasta"->listOf("Cook the pasta following its packet instructions; reserve a little cooking water before draining.","Halve the tomatoes and gently cook in the included olive oil until soft.","Toss the pasta with the tomatoes and a splash of reserved water.","Finish with the included basil and Parmesan. Omit any ingredients removed in your remix.")
 "chana"->listOf("Drain and rinse the chickpeas. Chop the included tomatoes and garlic.","Warm the included oil and gently cook the aromatics and spices from your reviewed list.","Add tomatoes and chickpeas. Simmer until the tomatoes soften and the sauce thickens, adding a little water if needed.","Finish with the included lemon. Taste and divide into your planned servings.")
 "mexican-rice"->listOf("Cook the brown rice according to its packet instructions.","Prepare the included tomatoes and avocado. Gently warm the included spices in the oil.","Toss the cooked rice with the prepared ingredients from your reviewed list.","Finish with the included lemon and serve in bowls. Skip any toppings removed in remix.")
 "sesame-noodles"->listOf("Cook the noodles according to their packet instructions. Rinse with cold water and drain well.","Slice the included cucumber and green onions.","Mix the included sauce ingredients and toss through the cooled noodles.","Add the prepared vegetables, divide into bowls and serve. Use only your reviewed ingredients.")
 "chickpea-salad"->listOf("Drain and rinse the chickpeas. Wash and chop the included cucumber and tomatoes.","Mix the included lemon juice, oil and cumin to make a dressing.","Toss the chickpeas and prepared vegetables with the dressing.","Divide into servings and enjoy. Leave out anything removed in your remix.")
 else->emptyList()
}
internal fun recipeSnapshot(recipe:Recipe,servings:Int,owned:Set<String>,omitted:Set<String> = emptySet(),steps:List<String> = cookingMethod(recipe.id),personal:Boolean=false):JournalMeal = JournalMeal(
 recipe.id,recipe.name,recipe.description,servings,recipe.prepTime,recipe.ingredients.filter{it.id !in omitted}.map{MealFood(it.name,if(personal)it.qty else "${kotlin.math.ceil(it.quantity.toDouble()*servings/recipe.baseServings).toLong()} ${it.unit}",it.id in owned)},steps,System.currentTimeMillis())
