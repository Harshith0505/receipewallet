package com.recipewallet.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudentDiscountTest {
    private val catalog=CatalogData(JSONObject(javaClass.getResourceAsStream("/demo.json")!!.bufferedReader().readText()))
    private val recipe=catalog.recipes.first { it.id=="pasta" }
    private val card=catalog.cards.first()
    private fun basket(store:String="market",eligible:Boolean=true,delivery:String="pickup")=packageQuote(recipe,setOf("pasta"),catalog.stores.first { it.id==store },card,catalog.productOffers,2,delivery,false,eligible)
    @Test fun discountReducesTodaysChargeBeforeCalculatingFutureRewards() {
        val q=basket()
        assertEquals(349L,q.groceries);assertEquals(35L,q.studentDiscount);assertEquals(314L,q.charge)
        assertEquals(16L,q.rewards);assertEquals(298L,q.net)
        assertEquals(349L,basket(eligible=false).charge)
        val delivery=basket(delivery="delivery")
        assertEquals(498L,delivery.fees);assertEquals(812L,delivery.charge);assertEquals(16L,delivery.rewards)
    }
    @Test fun storeSpecificRatesAndSavedRecipePacksUseTheSameOffer() {
        assertEquals(30L,basket("pantry").studentDiscount);assertEquals(569L,basket("pantry").charge)
        assertEquals(0L,basket("fresh").studentDiscount);assertEquals(209L,basket("fresh").charge)
        val product=GroceryProduct("demo","Sample","1 pack",1000,emptyList())
        val store=catalog.stores.first().copy(multiplierBps=10000,taxBps=1000)
        val custom=customQuote(listOf(product to 1),store,card,"delivery",true)
        assertEquals(100L,custom.studentDiscount);assertEquals(90L,custom.tax)
        assertEquals(45L,custom.rewards);assertEquals(1488L,custom.charge)
    }
}
