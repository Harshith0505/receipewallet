package com.recipewallet.app
import org.junit.Assert.*
import org.junit.Test
class PricingTest {
 private val market=Store("market","Market",4.7,0.8,199,9500,"Demo")
 private val cash=RewardCard("cash","Visa",5.0,"cashback",0.0,"")
 @Test fun paymentAndRewardTotalsAreSeparate() {
  val q=quote(685,market,cash)
  assertEquals(651L,q.groceries); assertEquals(33L,q.rewards)
  assertEquals(850L,q.charge); assertEquals(817L,q.net)
 }
 @Test fun pointsAreEarnedPerDollar() {
  val points=RewardCard("points","Points",3.0,"points",0.01,"")
  assertEquals(20L,quote(685,market,points).rewards)
 }
 @Test fun roundingUsesNearestCent() {
  assertEquals(1L,quote(1,market,cash).groceries)
  assertEquals(5L,cash.rewards(100))
 }
 @Test fun rankingPairsEachStoreWithBestCard() {
  val expensive=market.copy(id="expensive",rating=5.0,distanceMiles=0.1,deliveryFee=500)
  val results=ranked(685,listOf(expensive,market),listOf(cash.copy(id="low",rate=1.0),cash))
  assertEquals("market",results.first().store.id)
  assertEquals("cash",results.first().card.id)
 }
 @Test fun ingredientRemovalReducesCost() { assertTrue(quote(645,market,cash).net < quote(685,market,cash).net) }
}
