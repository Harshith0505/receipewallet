package com.recipewallet.app

import org.junit.Assert.*
import org.junit.Test

class RetailerMatchingTest {
 private fun product(size:String,name:String="Pasta")=RetailerProduct(name,size,200,null,"HIGH","UNIT","p1","i1")
 @Test fun computesEnoughWholePackagesOnlyForCompatibleExplicitSizes(){
  val need=RetailerNeed("pasta","Pasta","600 g",600.0,"g")
  assertEquals(2,suggestedPackageCount(need,product("16 oz")))
  assertEquals(1,suggestedPackageCount(need,product("1 kg")))
  assertNull(suggestedPackageCount(need,product("500 ml")))
  assertNull(suggestedPackageCount(need,product("4 ct / 6 oz")))
  assertNull(suggestedPackageCount(need,product("about 1 lb")))
  assertEquals(2,suggestedPackageCount(RetailerNeed("soy","Soy sauce","400 ml",400.0,"ml"),product("12 fl oz","Soy sauce")))
 }
 @Test fun parsesPersonalQuantitiesWithoutSearchingForTheQuantityText(){
  val need=retailerNeed(Ingredient("line-0","600 g pasta","",0),4,2,true)
  assertEquals("pasta",need.name);assertEquals(600.0,need.quantity!!,0.0)
  assertEquals("g",need.unit)
 }
 @Test fun recommendsLiteralMatchesAndDoesNotTreatADifferentProductAsTheIngredient(){
  val need=RetailerNeed("soy","Soy sauce","30 ml")
  val wrong=product("16 oz","Soy noodles")
  val right=product("12 fl oz","Kroger Soy Sauce")
  assertEquals(right,rankRetailerMatches(need,listOf(wrong,right)).first())
  assertTrue(isConfidentRetailerMatch(need,right));assertFalse(isConfidentRetailerMatch(need,wrong))
 }
}
