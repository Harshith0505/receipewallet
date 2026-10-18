package com.recipewallet.app

import kotlin.math.ceil

// Only convert an explicit, single package size in a compatible dimension.
internal fun suggestedPackageCount(need: RetailerNeed, product: RetailerProduct): Int? {
 val required=need.quantity ?: return null
 val unit=need.unit ?: return null
 val size=product.size ?: return null
 val match=Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*(fl\\s*oz|kg|g|ml|l|oz|lb|lbs|ct|count|each)\\s*$",RegexOption.IGNORE_CASE).matchEntire(size) ?: return null
 fun normalized(value:String,amount:Double):Pair<String,Double>? = when(value.lowercase().replace(" ","")) {
  "g"->"mass" to amount;"kg"->"mass" to amount*1000;"oz"->"mass" to amount*28.349523125;"lb","lbs"->"mass" to amount*453.59237
  "ml"->"volume" to amount;"l"->"volume" to amount*1000;"floz"->"volume" to amount*29.5735295625
  "each","ct","count"->"count" to amount;else->null
 }
 val wanted=normalized(unit,required) ?: return null
 val pack=normalized(match.groupValues[2],match.groupValues[1].toDouble()) ?: return null
 if(wanted.first!=pack.first||pack.second<=0||wanted.second<=0)return null
 return ceil(wanted.second/pack.second).toInt().takeIf{it in 1..20}
}
internal fun retailerNeed(ingredient:Ingredient,servings:Int,baseServings:Int,personal:Boolean):RetailerNeed {
 if(!personal) {
  val quantity=ceil(ingredient.quantity.toDouble()*servings/baseServings)
  return RetailerNeed(ingredient.id,ingredient.name,"${quantity.toLong()} ${ingredient.unit} needed for $servings servings",quantity,ingredient.unit)
 }
 val match=Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|l|oz|lb|each|ct)\\s+(.+)$",RegexOption.IGNORE_CASE).matchEntire(ingredient.name)
 return if(match!=null)RetailerNeed(ingredient.id,match.groupValues[3],ingredient.name,match.groupValues[1].toDouble(),match.groupValues[2])
 else RetailerNeed(ingredient.id,ingredient.name.replace(Regex("^\\s*\\d+(?:[./]\\d+)?\\s*(cups?|tbsp|tsp|tablespoons?|teaspoons?|cloves?|pieces?)?\\s+",RegexOption.IGNORE_CASE),""),ingredient.name+" · review package quantity")
}
internal fun rankRetailerMatches(need:RetailerNeed,products:List<RetailerProduct>):List<RetailerProduct> {
 val words=Regex("[a-z]+").findAll(need.name.lowercase()).map{it.value}.filter{it !in setOf("fresh","of","a","the","chopped","optional")}.toList()
 fun score(product:RetailerProduct):Int {
  val name=product.name.lowercase()
  return words.count{Regex("\\b"+Regex.escape(it)+"s?\\b").containsMatchIn(name)}
 }
 return products.filter{it.selectable}.sortedWith(compareByDescending<RetailerProduct>{score(it)}.thenBy{it.price})
}
internal fun isConfidentRetailerMatch(need:RetailerNeed,product:RetailerProduct):Boolean {
 val words=Regex("[a-z]+").findAll(need.name.lowercase()).map{it.value}.filter{it !in setOf("fresh","of","a","the","chopped","optional")}.toList()
 return words.isNotEmpty()&&words.all{Regex("\\b"+Regex.escape(it)+"s?\\b").containsMatchIn(product.name.lowercase())}
}
