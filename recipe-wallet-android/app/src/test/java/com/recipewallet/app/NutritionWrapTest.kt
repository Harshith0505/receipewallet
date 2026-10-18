package com.recipewallet.app

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NutritionWrapTest {
 private val monday=LocalDate.parse("2026-10-12")
 private val recipe=Recipe("meal","A meal","","",15,listOf(Ingredient("rice","Rice","200 g",100,200,"g"),Ingredient("oil","Oil","20 ml",100,20,"ml")),2)
 private val table=mapOf("rice" to IngredientNutrition("rice",100.0,"g",MealNutrition(10.0,70.0,2.0,3.0,4.0,350.0),"Dry rice"),"oil" to IngredientNutrition("oil",100.0,"ml",MealNutrition(0.0,0.0,0.0,92.0,0.0,813.0),"Oil density included"))
 private fun meal(id:String="1",servings:Double=1.0,ids:List<String> = listOf("rice","oil"),date:String="2026-10-12")=EatenMeal(id,"meal",date,servings,ids)
 @Test fun portionsNotPackagePurchasesDriveNutrition(){
  val one=foodWeek(monday,listOf(meal()),listOf(recipe),table)
  assertEquals(10.0,one.totals.proteinG,0.0)
  assertEquals(70.0,one.totals.carbsG,0.0)
  assertEquals(2.0,one.totals.sugarG,0.0)
  assertEquals(12.2,one.totals.fatG,0.0001)
  assertEquals(431.3,one.totals.kcal,0.0001)
  val quarter=foodWeek(monday,listOf(meal(servings=.25)),listOf(recipe),table)
  assertEquals(2.5,quarter.totals.proteinG,0.0)
 }
 @Test fun removedIngredientsAndWeekBoundariesAreRespected(){
  val log=listOf(meal(ids=listOf("rice")),meal("sunday",date="2026-10-18"),meal("before",date="2026-10-11"),meal("after",date="2026-10-19"))
  val report=foodWeek(monday,log,listOf(recipe),table)
  assertEquals(2,report.entries.size)
  assertEquals(2,report.daysLogged)
  assertEquals(20.0,report.totals.proteinG,0.0)
  assertEquals(15.2,report.totals.fatG,0.0)
  assertEquals(2,report.ingredientCount)
 }
 @Test fun emptyJournalDoesNotBecomeZeroIntakeClaim(){
  val report=foodWeek(monday,emptyList(),listOf(recipe),table)
  assertEquals(0,report.daysLogged)
  assertTrue(report.entries.isEmpty())
  assertNull(report.favoriteRecipeId)
 }
 @Test fun persistedLogRoundTripsAndRejectsInvalidEntries(){
  val raw="[${meal().json()}]"
  assertEquals(listOf(meal()),readEatenMeals(raw,listOf(recipe),table))
  listOf(meal(servings=Double.NaN),meal(servings=0.0),meal(servings=12.25),meal(ids=emptyList()),meal(ids=listOf("rice","rice")),meal(ids=listOf("invented")),meal(date="tomorrow")).forEach{bad->
   assertTrue(runCatching{validateEatenMeal(bad,listOf(recipe),table)}.isFailure)
  }
  assertTrue(runCatching{readEatenMeals("[${meal().json()},${meal().json()}]",listOf(recipe),table)}.isFailure)
 }
 @Test fun unitMismatchFailsRatherThanDisplayingPlausibleWrongTotals(){
  val wrong=table+ ("rice" to table.getValue("rice").copy(unit="ml"))
  assertTrue(runCatching{foodWeek(monday,listOf(meal()),listOf(recipe),wrong)}.isFailure)
 }
 @Test fun realReferenceCoversEverySupportedRecipeAndKeepsSampleMathLocal(){
  fun asset(name:String)=javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use{it.readText()}
  val catalog=CatalogData(JSONObject(asset("demo.json")))
  val nutrition=nutritionTable(JSONObject(asset("nutrition.json")))
  catalog.recipes.forEach{r->
   val log=EatenMeal("known",r.id,monday.toString(),1.0,r.ingredients.map{it.id})
   val one=foodWeek(monday,listOf(log),catalog.recipes,nutrition)
   val two=foodWeek(monday,listOf(log.copy(servings=2.0)),catalog.recipes,nutrition)
   assertTrue(one.totals.kcal>0)
   assertEquals(one.totals.proteinG*2,two.totals.proteinG,.11)
   assertEquals(one.totals.carbsG*2,two.totals.carbsG,.11)
  }
 }
 @Test fun fiftyFourBackendPortionAndOmissionFixturesMatchOfflineReport(){
  fun asset(name:String)=javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use{it.readText()}
  val catalog=CatalogData(JSONObject(asset("demo.json")))
  val nutrition=nutritionTable(JSONObject(asset("nutrition.json")))
  val cases=JSONArray(asset("nutrition-parity.json"))
  assertEquals(54,cases.length())
  repeat(cases.length()){index->
   val fixture=cases.getJSONObject(index);val entry=readEatenMeals(JSONArray().put(fixture.getJSONObject("entry")).toString(),catalog.recipes,nutrition).single()
   val actual=foodWeek(monday,listOf(entry),catalog.recipes,nutrition).totals.json();val expected=fixture.getJSONObject("totals")
   listOf("proteinG","carbsG","sugarG","fatG","fiberG","kcal").forEach{key->assertEquals("case $index $key",expected.getDouble(key),actual.getDouble(key),.00001)}
  }
 }
 @Test fun mondayIsStableAcrossYearAndSundayBoundaries(){
  assertEquals(LocalDate.parse("2025-12-29"),mondayOf(LocalDate.parse("2026-01-01")))
  assertEquals(monday,mondayOf(LocalDate.parse("2026-10-18")))
 }
}
