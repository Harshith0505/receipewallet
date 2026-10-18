package com.recipewallet.app

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Cached values must track every input affecting a real checkout total. */
@RunWith(AndroidJUnit4::class)
class PricingCacheTest {
 @Test fun checkoutPricesInvalidateWhenInputsChange(){
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  instrumentation.runOnMainSync {
   val vm=WalletViewModel(instrumentation.targetContext.applicationContext as Application)
   vm.recipeId="pasta";vm.selected=vm.recipe.ingredients.map{it.id}.toSet()
   fun checkQuotes(){
    val expected=vm.catalog.stores.map{s->vm.catalog.cards.map{c->packageQuote(vm.recipe,vm.selected,s,c,vm.catalog.productOffers,vm.servings,vm.fulfillment,vm.organicOnly)}.minWith(compareBy<Quote>{it.net}.thenBy{it.card.id})}.sortedWith(compareBy<Quote>{!it.complete}.thenBy{it.net}.thenBy{it.store.id})
    assertEquals(expected,vm.rankings)
    assertEquals(vm.groceryQuote(vm.current.store,vm.current.card),vm.current)
   }
   checkQuotes()
   val initial=vm.rankings
   assertSame(initial,vm.rankings)
   vm.error="Unrelated UI state"
   assertSame(initial,vm.rankings)
   vm.changeServings(6);checkQuotes();assertNotSame(initial,vm.rankings)
   vm.toggle("cheese");checkQuotes()
   vm.changeFulfillment("delivery");checkQuotes()
   vm.changeOrganicOnly(true);checkQuotes()
   vm.storeId=vm.catalog.stores.last().id;vm.cardId=vm.catalog.cards.last().id;checkQuotes()
  }
 }
 @Test fun recipeEstimatesTrackServingsAndPantry(){
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  instrumentation.runOnMainSync {
   val vm=WalletViewModel(instrumentation.targetContext.applicationContext as Application)
   val state=vm.studio;val oldPeople=state.people;val oldOwned=state.owned
   try {
    val recipe=vm.catalog.recipes.first{it.id=="pasta"}
    fun checkEstimate(){
     val expected=vm.catalog.stores.map{s->packageQuote(recipe,recipe.ingredients.map{it.id}.toSet()-state.owned,s,vm.catalog.cards.first(),vm.catalog.productOffers,state.people,"pickup",false)}.filter{it.complete}.minByOrNull{it.charge}
     assertEquals(expected,state.estimate(recipe))
    }
    checkEstimate();assertSame(state.estimate(recipe),state.estimate(recipe))
    state.updatePeople(if(oldPeople==12)2 else 12);checkEstimate()
    state.toggleOwned("pasta");checkEstimate()
   }finally{
    state.updatePeople(oldPeople)
    (state.owned union oldOwned).filter{(it in state.owned)!=(it in oldOwned)}.forEach{state.toggleOwned(it)}
   }
  }
 }
}
