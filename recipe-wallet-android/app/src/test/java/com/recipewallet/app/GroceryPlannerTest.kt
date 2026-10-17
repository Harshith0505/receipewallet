package com.recipewallet.app
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GroceryPlannerTest {
 private val catalog=CatalogData(JSONObject(javaClass.getResourceAsStream("/demo.json")!!.bufferedReader().readText()))
 @Test fun androidMatchesServerForEveryShoppingScenario() {
  val cases=javaClass.getResourceAsStream("/grocery-parity.tsv")!!.bufferedReader().readLines().drop(1)
  for(row in cases) {
   val f=row.split('\t');val r=catalog.recipes.first { it.id==f[0] };val s=catalog.stores.first { it.id==f[1] }
   val selected=r.ingredients.map { it.id }.filter { it!=f[5] }.toSet()
   val q=packageQuote(r,selected,s,catalog.cards.first(),catalog.productOffers,f[2].toInt(),f[3],f[4].toBoolean())
   assertEquals(row,f[6].toBoolean(),q.complete)
   if(q.complete) {
    assertEquals(row,f[7].toLong(),q.groceries);assertEquals(row,f[8].toLong(),q.fees);assertEquals(row,f[9].toLong(),q.charge)
   }
  }
 }
 @Test fun servingsIncreasePackagesAndPantryRemovesWholePurchases() {
  val r=catalog.recipes.first { it.id=="pasta" };val s=catalog.stores.first();val c=catalog.cards.first()
  val two=packageQuote(r,setOf("pasta"),s,c,catalog.productOffers,2,"pickup",false)
  val twelve=packageQuote(r,setOf("pasta"),s,c,catalog.productOffers,12,"pickup",false)
  assertEquals(1L,two.items.single().packages);assertEquals(3L,twelve.items.single().packages)
  assertTrue(twelve.charge>two.charge)
  assertEquals(0L,packageQuote(r,emptySet(),s,c,catalog.productOffers,2,"pickup",false).groceries)
 }
}
