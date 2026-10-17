package com.recipewallet.app

import android.content.Context
import org.json.JSONObject
import kotlin.math.roundToLong
import java.util.Locale

data class Ingredient(val id: String, val name: String, val qty: String, val basePrice: Long, val quantity:Long=1, val unit:String="each")
data class Recipe(val id: String, val name: String, val emoji: String, val description: String, val prepTime: Int, val ingredients: List<Ingredient>,val baseServings:Int=2)
data class Store(val id: String, val name: String, val rating: Double, val distanceMiles: Double, val deliveryFee: Long, val multiplierBps: Long, val address: String,val pickupFee:Long=0,val packageDeliveryFee:Long=0,val serviceFee:Long=0,val deliveryEnabled:Boolean=true,val taxBps:Long=0)
data class RewardCard(val id: String, val name: String, val rate: Double, val rewardType: String, val pointsValue: Double, val notes: String) {
 val label: String get() = if(rewardType == "points") "${rate.toInt()} points / $1" else "${rate.toInt()}% cash back"
 fun rewards(grocery: Long): Long = (grocery * if(rewardType == "points") rate * pointsValue else rate / 100).roundToLong()
}
data class Quote(val store: Store, val card: RewardCard, val groceries: Long, val rewards: Long,val fees:Long=store.deliveryFee,val tax:Long=0,val items:List<GroceryLine> = emptyList(),val missing:List<String> = emptyList(),val fulfillment:String="delivery") {
 val complete get()=missing.isEmpty()
 val charge get() = groceries + fees + tax
 val net get() = charge - rewards
}
fun quote(subtotal: Long, store: Store, card: RewardCard): Quote {
 val grocery = (subtotal * store.multiplierBps + 5000) / 10000
 return Quote(store, card, grocery, card.rewards(grocery))
}
fun ranked(subtotal: Long, stores: List<Store>, cards: List<RewardCard>): List<Quote> = stores.map { s -> cards.map { quote(subtotal, s, it) }.minWith(compareBy<Quote> { it.net }.thenBy { it.card.id }) }.sortedWith(compareBy<Quote> { it.net }.thenBy { it.store.id })
fun money(cents: Long) = String.format(Locale.US, "$%.2f", cents / 100.0)
class Catalog(context: Context):CatalogData(JSONObject(context.assets.open("demo.json").bufferedReader().use { it.readText() }))
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
                row.getLong("multiplierBps"), row.getString("address"),row.getLong("pickupFee"),row.getLong("packageDeliveryFee"),row.getLong("serviceFee"),row.getBoolean("deliveryEnabled"),row.getLong("taxBps")
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
