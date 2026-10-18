package com.recipewallet.app

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.roundToLong
import java.util.Locale

data class Ingredient(val id: String, val name: String, val qty: String, val basePrice: Long, val quantity:Long=1, val unit:String="each")
data class Recipe(val id: String, val name: String, val emoji: String, val description: String, val prepTime: Int, val ingredients: List<Ingredient>,val baseServings:Int=2)
data class Store(val id: String, val name: String, val rating: Double, val distanceMiles: Double, val deliveryFee: Long, val multiplierBps: Long, val address: String,val pickupFee:Long=0,val packageDeliveryFee:Long=0,val serviceFee:Long=0,val deliveryEnabled:Boolean=true,val taxBps:Long=0,val studentDiscountBps:Long=0)
data class RewardCard(val id: String, val name: String, val rate: Double, val rewardType: String, val pointsValue: Double, val notes: String) {
 val label: String get() = if(rewardType == "points") "${rate.toInt()} points / $1" else "${rate.toInt()}% cash back"
 fun rewards(grocery: Long): Long = (grocery * if(rewardType == "points") rate * pointsValue else rate / 100).roundToLong()
}
data class Quote(val store: Store, val card: RewardCard, val groceries: Long, val rewards: Long,val fees:Long=store.deliveryFee,val tax:Long=0,val items:List<GroceryLine> = emptyList(),val missing:List<String> = emptyList(),val fulfillment:String="delivery",val customItems:List<CustomGroceryLine> = emptyList(),val studentDiscount:Long=0) {
 val complete get()=missing.isEmpty()
 val discountedGroceries get() = groceries - studentDiscount
 val charge get() = discountedGroceries + fees + tax
 val net get() = charge - rewards
}
fun studentDiscount(groceries:Long,store:Store,eligible:Boolean):Long = if(eligible) ((groceries*store.studentDiscountBps+5000)/10000).coerceIn(0,groceries) else 0L

fun quote(subtotal: Long, store: Store, card: RewardCard,studentEligible:Boolean=false): Quote {
 val grocery = (subtotal * store.multiplierBps + 5000) / 10000
 val discount=studentDiscount(grocery,store,studentEligible)
 return Quote(store, card, grocery, card.rewards(grocery-discount),studentDiscount=discount)
}
fun ranked(subtotal: Long, stores: List<Store>, cards: List<RewardCard>): List<Quote> = stores.map { s -> cards.map { quote(subtotal, s, it) }.minWith(compareBy<Quote> { it.net }.thenBy { it.card.id }) }.sortedWith(compareBy<Quote> { it.net }.thenBy { it.store.id })
fun money(cents: Long) = String.format(Locale.US, "$%.2f", cents / 100.0)
data class GroceryProduct(val id: String, val name: String, val pack: String, val price: Long, val aliases: List<String>)

fun suggestProduct(line: String, products: List<GroceryProduct>): GroceryProduct? {
    fun normalized(value: String) = " " + value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim() + " "
    val text = normalized(line)
    return products.mapNotNull { product ->
        val score = product.aliases.filter { text.contains(normalized(it)) }.maxOfOrNull { it.length }
        score?.let { product to it }
    }.maxByOrNull { it.second }?.first
}

data class CustomGroceryLine(val product:GroceryProduct,val packages:Int,val unitPrice:Long) {
 val cost get()=packages*unitPrice
}
fun customQuote(items:List<Pair<GroceryProduct,Int>>,store:Store,card:RewardCard,fulfillment:String,studentEligible:Boolean=false):Quote {
 val lines=items.map { (product,count)->CustomGroceryLine(product,count,(product.price*store.multiplierBps+5000)/10000) }
 val groceries=lines.sumOf { it.cost }
 val fees=if(fulfillment=="pickup") store.pickupFee else store.packageDeliveryFee+store.serviceFee
 val discount=studentDiscount(groceries,store,studentEligible)
 val tax=((groceries-discount)*store.taxBps+5000)/10000
 val missing=if(fulfillment=="delivery"&&!store.deliveryEnabled) listOf("Delivery unavailable in this demo") else emptyList()
 return Quote(store,card,groceries,card.rewards(groceries-discount),fees,tax,fulfillment=fulfillment,missing=missing,customItems=lines,studentDiscount=discount)
}
class Catalog(context: Context):CatalogData(JSONObject(context.assets.open("demo.json").bufferedReader().use { it.readText() })) {
 val products:List<GroceryProduct> = JSONArray(context.assets.open("products.json").bufferedReader().use { it.readText() }).let { rows ->
  List(rows.length()) { index -> val row=rows.getJSONObject(index);val aliases=row.getJSONArray("aliases")
   GroceryProduct(row.getString("id"),row.getString("name"),row.getString("pack"),row.getLong("price"),List(aliases.length()){aliases.getString(it)})
  }
 }
}
open class CatalogData(private val data:JSONObject) {
    val recipes: List<Recipe> = data.getJSONArray("recipes").let { rows ->
        List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val ingredients = row.getJSONArray("ingredients")
            Recipe(
                id = row.getString("id"),
                name = row.getString("name"),
                emoji = row.getString("emoji"),
                description = row.getString("description"),
                prepTime = row.getInt("prepTime"),
                ingredients = List(ingredients.length()) { ingredientIndex ->
                    val ingredient = ingredients.getJSONObject(ingredientIndex)
                    Ingredient(
                        ingredient.getString("id"), ingredient.getString("name"),
                        ingredient.getString("qty"), ingredient.getLong("basePrice"), ingredient.getLong("quantity"),ingredient.getString("unit")
                    )
                },
                baseServings = row.getInt("baseServings")
            )
        }
    }

    val stores: List<Store> = data.getJSONArray("stores").let { rows ->
        List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            Store(
                row.getString("id"), row.getString("name"), row.getDouble("rating"),
                row.getDouble("distanceMiles"), row.getLong("deliveryFee"),
                row.getLong("multiplierBps"), row.getString("address"),row.getLong("pickupFee"),row.getLong("packageDeliveryFee"),row.getLong("serviceFee"),row.getBoolean("deliveryEnabled"),row.getLong("taxBps"),row.optLong("studentDiscountBps",0)
            )
        }
    }

    val productOffers:List<ProductOffer> = data.getJSONArray("productOffers").let { a -> List(a.length()) { n -> val o=a.getJSONObject(n); val t=o.getJSONArray("tags"); ProductOffer(o.getString("id"),o.getString("ingredientId"),o.getString("storeId"),o.getLong("packageQuantity"),o.getString("unit"),o.getLong("priceCents"),o.getLong("stockPackages"),List(t.length()){t.getString(it)}) } }

    val cards: List<RewardCard> = data.getJSONArray("cards").let { rows ->
        List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            RewardCard(
                row.getString("id"), row.getString("name"), row.getDouble("rate"),
                row.getString("rewardType"), row.getDouble("pointsValue"), row.getString("notes")
            )
        }
    }
}
