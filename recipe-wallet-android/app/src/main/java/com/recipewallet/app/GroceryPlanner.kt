package com.recipewallet.app

import kotlin.math.ceil

data class ProductOffer(val id:String,val ingredientId:String,val storeId:String,val packageQuantity:Long,val unit:String,val priceCents:Long,val stockPackages:Long,val tags:List<String>)
data class GroceryLine(val ingredientId:String,val name:String,val quantity:Long,val unit:String,val product:ProductOffer,val packages:Long) {
 val cost get()=packages*product.priceCents
}
fun packageQuote(recipe:Recipe, selected:Set<String>, store:Store, card:RewardCard, offers:List<ProductOffer>, servings:Int, fulfillment:String, organicOnly:Boolean,studentEligible:Boolean=false):Quote {
 require(servings in 1..12)
 val lines=mutableListOf<GroceryLine>(); val missing=mutableListOf<String>()
 for(i in recipe.ingredients.filter { it.id in selected }) {
  val needed=ceil(i.quantity.toDouble()*servings/recipe.baseServings).toLong()
  val candidates=offers.filter { it.ingredientId==i.id && it.storeId==store.id && it.unit==i.unit && (!organicOnly||"organic" in it.tags) }.map { it to ((needed+it.packageQuantity-1)/it.packageQuantity) }.filter { (o,n)->n<=o.stockPackages }.sortedWith(compareBy<Pair<ProductOffer,Long>> { it.first.priceCents*it.second }.thenBy { it.first.packageQuantity*it.second }.thenBy { it.first.id })
  val chosen=candidates.firstOrNull()
  if(chosen==null) missing.add(i.name) else lines.add(GroceryLine(i.id,i.name,needed,i.unit,chosen.first,chosen.second))
 }
 if(fulfillment=="delivery"&&!store.deliveryEnabled) missing.add("Delivery unavailable in this demo")
 val groceries=lines.sumOf { it.cost }
 val fees=if(fulfillment=="pickup") store.pickupFee else store.packageDeliveryFee+store.serviceFee
 val discount=studentDiscount(groceries,store,studentEligible)
 val tax=((groceries-discount)*store.taxBps+5000)/10000
 return Quote(store,card,groceries,card.rewards(groceries-discount),fees,tax,lines,missing,fulfillment,studentDiscount=discount)
}
