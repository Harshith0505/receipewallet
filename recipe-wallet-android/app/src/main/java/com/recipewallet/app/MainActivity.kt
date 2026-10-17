package com.recipewallet.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

private val Ink=Color(0xFF213E30)
private val Cream=Color(0xFFF7F6EF)
private val Muted=Color(0xFF677167)
private val Lime=Color(0xFFD8E9A1)
class MainActivity: ComponentActivity() {
 private val vm: WalletViewModel by viewModels()
 private lateinit var sheet: PaymentSheet
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  sheet=PaymentSheet(this) { result -> when(result) {
   is PaymentSheetResult.Completed -> vm.verify()
   is PaymentSheetResult.Canceled -> vm.paymentCanceled()
   is PaymentSheetResult.Failed -> vm.paymentFailed(result.error.localizedMessage ?: "Payment failed. Try again.")
  } }
  setContent {
   MaterialTheme(colorScheme=lightColorScheme(primary=Ink, background=Cream, surface=Color.White, secondary=Lime)) {
    Wallet(vm) { startTestCheckout() }
   }
  }
 }
 fun startTestCheckout() {
  vm.pay { key, secret ->
   PaymentConfiguration.init(this,key)
   sheet.presentWithPaymentIntent(secret,PaymentSheet.Configuration(merchantDisplayName="Recipe Wallet · TEST", allowsDelayedPaymentMethods=false))
  }
 }
}
@Composable private fun Wallet(vm: WalletViewModel, pay: ()->Unit) {
 BackHandler(enabled=vm.step in 1..3 && !vm.busy) { vm.step--; vm.error=null }
 val scroll = key(vm.step) { rememberScrollState() }
 Scaffold(containerColor=Cream) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().verticalScroll(scroll).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
    Text("◒  recipe wallet",color=Ink,fontSize=23.sp,fontWeight=FontWeight.Bold)
    Tag("DEMO",Lime)
   }
   Text("A good meal. A smarter spend.",color=Muted,fontSize=13.sp)
   if(vm.step<4) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
     listOf("Plan","Basket","Store","Pay").forEachIndexed { i,s -> Text("${i+1} $s",modifier=Modifier.weight(1f).background(if(i==vm.step) Ink else Color(0xFFE9EAE1),RoundedCornerShape(9.dp)).padding(vertical=10.dp,horizontal=7.dp),color=if(i==vm.step) Color.White else Muted,fontSize=12.sp) }
    }
   }
   when(vm.step) {
    0 -> {
     Eyebrow("YOUR DINNER, PLANNED TOGETHER")
     Title("Tell us the craving.\nWe'll plan the shop.")
     Text("A meal, a budget, and what you already own. Your agent checks recipes, stores and sample Visa rewards.",color=Muted)
     Panel {
      Text(if(vm.agentReply.isBlank()) "Plan my shop" else "Revise your plan",color=Ink,fontWeight=FontWeight.Bold,fontSize=18.sp)
      OutlinedTextField(value=vm.preference,onValueChange={if(it.length<=600) vm.preference=it},label={Text(if(vm.agentReply.isBlank()) "e.g. Vegetarian dinner under $12" else "e.g. I already have garlic too")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=4,enabled=!vm.aiBusy)
      if(vm.agentReply.isBlank() && !vm.aiBusy) TextButton(onClick={vm.preference="Find me a vegetarian noodle dinner under $30. I already have garlic."}) { Text("Try an example request") }
      Primary(if(vm.aiBusy) "Agent is planning your shop…" else if(vm.agentReply.isBlank()) "Plan my shop · AI" else "Update my plan · AI",enabled=!vm.aiBusy&&vm.preference.isNotBlank(),onClick={vm.suggest()})
      if(vm.aiBusy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
      Text("Your request and recent meal preferences are sent to Gemini. No payment details are shared with the AI. Local planning sessions expire after 20 minutes.",color=Muted,fontSize=11.sp)
      vm.aiError?.let { Text(it,color=Color(0xFF9A3A28),fontSize=13.sp) }
     }
     if(vm.agentReply.isNotBlank()) {
      Panel {
       Tag("AGENT PLAN · REVIEW BEFORE CHECKOUT",Lime)
       vm.agentTrace.forEach { Text("✓ $it",color=Muted,fontSize=12.sp) }
       Text(vm.agentReply,color=Ink)
       val planned=vm.plannedRecipe
       val q=vm.plannedQuote
       if(planned!=null && q!=null) {
        Text(planned.emoji+"  "+planned.name,color=Ink,fontSize=21.sp,fontWeight=FontWeight.Bold)
        Text("Interpreted preferences: ${vm.planDiet} · ${if(vm.planBudget>=0) money(vm.planBudget)+" upfront budget" else "no budget set"}${if(vm.planTime>0) " · under ${vm.planTime} minutes" else ""}",color=Muted,fontSize=12.sp)
        Text("To buy: "+planned.ingredients.filter { it.id in vm.planIds("ingredientIds") }.joinToString { it.name },color=Ink,fontSize=13.sp)
        Text("Already owned: "+planned.ingredients.filter { it.id in vm.planIds("ownedIngredientIds") }.joinToString { it.name }.ifBlank { "None specified" },color=Muted,fontSize=13.sp)
        Text("${q.store.name} · ${q.card.name}",color=Ink,fontWeight=FontWeight.Bold)
        Text("★ ${q.store.rating} · ${q.store.distanceMiles} mi · ${q.store.address}",color=Muted,fontSize=12.sp)
        Line("Grocery estimate",money(q.groceries)); Line("Fulfillment fees",money(q.fees))
        Line("Amount charged before rewards",money(q.charge),true)
        Line("Estimated future rewards",money(q.rewards))
        Line("Estimated cost after rewards",money(q.net),true)
        Text(q.card.label+". "+q.card.notes,color=Muted,fontSize=12.sp)
        if(!vm.planWithinBudget) Text("This is above your budget. Revise your request or budget before approving.",color=Color(0xFF9A3A28),fontWeight=FontWeight.Bold)
        Text("Correct a preference or pantry item in the revision box above. Package totals are recalculated from your selected servings and shopping preferences. Review the store screen before payment.",color=Muted,fontSize=12.sp)
        Primary("Review package-priced basket  →",enabled=vm.planWithinBudget&&!vm.aiBusy,onClick={vm.approvePlan()})
       }
       TextButton(onClick={vm.newPlan()},enabled=!vm.aiBusy) { Text("Start a new shop") }
       DemoNote()
      }
     }
     Eyebrow("OR CHOOSE A RECIPE YOURSELF")
     vm.catalog.recipes.forEach { r ->
      Panel(Modifier.clickable(enabled=!vm.aiBusy) { vm.choose(r.id) }) {
       Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(r.emoji,fontSize=48.sp,modifier=Modifier.background(Cream,RoundedCornerShape(16.dp)).padding(12.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
         Text(r.name,color=Ink,fontWeight=FontWeight.Bold,fontSize=19.sp)
         Text(r.description,color=Muted,fontSize=13.sp)
         Text("${r.prepTime} min · ${r.ingredients.size} ingredients  →",color=Ink,fontSize=12.sp)
        }
       }
      }
     }
     Text("All recipes, stores, cards and prices are illustrative demo data. Manual shopping uses whole packages and a configured 0% demo tax. AI suggestions are recalculated before checkout.",color=Muted,fontSize=12.sp)
    }
    1 -> {
     Eyebrow("YOUR RECIPE, YOUR SHOPPING LIST")
     Title(vm.recipe.name)
     Text("Unchecked ingredients are already in your pantry. Prices below are for whole packages.",color=Muted)
     ShoppingControls(vm)
     Panel {
      vm.recipe.ingredients.forEach { i ->
       Row(Modifier.fillMaxWidth().clickable { vm.toggle(i.id) },verticalAlignment=Alignment.CenterVertically) {
        Checkbox(checked=i.id in vm.selected,onCheckedChange={vm.toggle(i.id)})
        Column(Modifier.weight(1f)) {
         Text(i.name,color=Ink,fontWeight=FontWeight.Bold)
         Text("${kotlin.math.ceil(i.quantity.toDouble()*vm.servings/vm.recipe.baseServings).toLong()} ${i.unit} needed"+if(i.id !in vm.selected) " · already owned" else "",color=Muted,fontSize=12.sp)
        }
       }
      }
     }
     if(vm.selected.isEmpty()) Text("Your pantry covers this recipe. No grocery purchase is needed.",color=Ink)
     if(vm.rankings.none { it.complete }) Text("No store can supply this selection. Review your organic preference or fulfillment choice.",color=Color(0xFF9A3A28))
     Primary("Compare complete baskets →",enabled=vm.selected.isNotEmpty()&&vm.rankings.any { it.complete },onClick={vm.compare()})
     DemoNote()
    }
    2 -> {
     Eyebrow("WHOLE PACKAGES · CLEAR TOTALS")
     Title("Your meal.\nYour best store.")
     Text("Complete baskets ranked by today's payable total. Ratings describe stores, not ingredient quality.",color=Muted)
     ShoppingControls(vm)
     vm.rankings.forEachIndexed { index,q ->
      Panel {
       if(index==0&&q.complete) Tag("LOWEST UPFRONT TOTAL",Lime)
       Text(q.store.name,fontSize=22.sp,color=Ink,fontWeight=FontWeight.Bold)
       Text(q.store.address,color=Muted,fontSize=12.sp)
       if(!q.complete) {
        Text("Unavailable: "+q.missing.joinToString(),color=Color(0xFF9A3A28))
        Text("Partial baskets cannot be checked out.",color=Muted)
       } else {
        q.items.forEach { line ->
         Line("${line.name} · ${line.packages} × ${line.product.packageQuantity} ${line.unit}",money(line.cost))
         Text("Need ${line.quantity} ${line.unit} · buy ${line.packages*line.product.packageQuantity} ${line.unit}"+if("organic" in line.product.tags) " · organic" else "",fontSize=11.sp,color=Muted)
        }
        HorizontalDivider()
        Line("Whole-package groceries",money(q.groceries))
        Line(if(vm.fulfillment=="pickup") "Pickup fee" else "Delivery + service fees",money(q.fees))
        Line("Demo tax (0%)",money(q.tax))
        Line("Pay today",money(q.charge),true)
        Line("Estimated future rewards",money(q.rewards))
        Text("${q.card.name} · ${q.card.label}",fontSize=12.sp,color=Muted)
        Primary("Review checkout →",onClick={vm.checkout(q.store.id)})
       }
       val context=LocalContext.current
       OutlinedButton(onClick={
        val uri=Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(q.store.address)+"&travelmode=walking")
        try { context.startActivity(Intent(Intent.ACTION_VIEW,uri)) } catch (_:Exception) { vm.error="No browser or maps app is available" }
       }) { Text("Walking directions · Google Maps") }
       Text("Real store location. Product prices, availability and fulfillment are simulated; no retailer integration.",color=Muted,fontSize=11.sp)
      }
     }
     vm.error?.let { Text(it,color=Color(0xFF9A3A28)) }
    }
    3 -> {
     Eyebrow("MAKE YOUR CARD WORK HARDER")
     Title("The finishing touch.")
     Text("${vm.current.store.name} · ${vm.recipe.name} · ${vm.servings} servings · ${vm.fulfillment}",color=Muted)
     vm.current.items.forEach { Line("${it.name} · ${it.packages} package(s)",money(it.cost)) }
     DemoNote()
     val best=vm.catalog.cards.maxBy { it.rewards(vm.current.groceries) }
     vm.catalog.cards.forEach { card ->
      val selected=card.id==vm.current.card.id
      Column(Modifier.fillMaxWidth().border(if(selected) 2.dp else 1.dp,if(selected) Ink else Color(0xFFDDDFD5),RoundedCornerShape(18.dp)).background(if(selected) Color(0xFFEEF2E2) else Color.White,RoundedCornerShape(18.dp)).clickable(enabled=!vm.busy) { vm.switchCard(card.id) }.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
       Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("${if(selected) "●" else "○"}  ${card.name}",fontWeight=FontWeight.Bold,color=Ink); Text("VISA",color=Ink,fontWeight=FontWeight.Bold,fontSize=15.sp) }
       if(card.id==best.id) Tag("BEST ESTIMATED REWARDS",Lime)
       Line(card.label,"${money(card.rewards(vm.current.groceries))} est.")
       Text(card.notes,color=Muted,fontSize=12.sp)
      }
     }
     Panel {
      Line("Grocery estimate",money(vm.current.groceries)); Line("Fulfillment fees",money(vm.current.fees))
      Line("Demo tax (0%)",money(vm.current.tax)); Line("Test payment amount",money(vm.current.charge),true)
      Line("Estimated future rewards","−${money(vm.current.rewards)}")
      HorizontalDivider(color=Cream)
      Line("Estimated cost after rewards",money(vm.current.net),true)
      Text("Rewards do not reduce today's charge. Sample card selection is advisory; use a Stripe test card in PaymentSheet. No actual rewards are earned.",color=Muted,fontSize=12.sp)
     }
     vm.error?.let { Text(it,color=Color(0xFF9A3A28),modifier=Modifier.background(Color(0xFFFFECE4),RoundedCornerShape(12.dp)).padding(14.dp)) }
     if(vm.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
     Primary(if(vm.busy) "Processing…" else "Pay ${money(vm.current.charge)} · Stripe test",enabled=!vm.busy&&vm.current.complete,onClick=pay)
     if(vm.canVerify) OutlinedButton(onClick={vm.verify()},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Text("Verify pending test payment") }
     TextButton(onClick={vm.walkthrough()},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) { Text("Preview confirmation without payment") }
    }
    4 -> {
     val receipt=vm.receipt!!
     Text("✓",fontSize=56.sp,color=Ink,modifier=Modifier.background(Lime,RoundedCornerShape(28.dp)).padding(20.dp))
     Eyebrow(if(vm.paymentVerified) "STRIPE TEST PAYMENT VERIFIED" else "WALKTHROUGH · NO PAYMENT MADE")
     Title(if(vm.paymentVerified) "Demo complete.\nDinner imagined." else "Here's your\ndemo receipt.")
     Panel {
      Text(receipt.store.name,fontSize=22.sp,color=Ink,fontWeight=FontWeight.Bold)
      Text(receipt.card.name,color=Muted)
      Line(if(vm.paymentVerified) "Test amount paid" else "Test amount preview",money(receipt.charge))
      Line("Illustrative rewards",money(receipt.rewards))
      Line("Estimated cost after rewards",money(receipt.net),true)
     }
     DemoNote()
     Text("No rewards were earned. No delivery is scheduled. This prototype demonstrates the decision and payment flow.",color=Muted,fontSize=13.sp)
     Primary("Cook up another idea  →",onClick={vm.restart()})
    }
   }
   if(vm.step in 1..3) TextButton(onClick={vm.step--;vm.error=null},enabled=!vm.busy) { Text("← Back") }
   Spacer(Modifier.height(12.dp))
  }
 }
}
@Composable private fun Title(text: String) { Text(text,fontSize=34.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold,color=Ink) }
@Composable private fun Eyebrow(text: String) { Text(text,fontSize=10.sp,letterSpacing=1.6.sp,fontWeight=FontWeight.Bold,color=Muted) }
@Composable private fun Tag(text: String,bg: Color) { Text(text,fontSize=10.sp,fontWeight=FontWeight.Bold,color=Ink,modifier=Modifier.background(bg,RoundedCornerShape(6.dp)).padding(horizontal=9.dp,vertical=6.dp)) }
@Composable private fun Panel(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) { Column(modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(20.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) }
@Composable private fun Line(label: String,value: String,bold: Boolean=false) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text(label,color=if(bold) Ink else Muted,fontSize=13.sp,fontWeight=if(bold) FontWeight.Bold else FontWeight.Normal,modifier=Modifier.weight(1f).padding(end=12.dp)); Text(value,color=Ink,fontSize=if(bold) 19.sp else 14.sp,fontWeight=if(bold) FontWeight.Bold else FontWeight.Normal) } }
@Composable private fun Primary(text: String,enabled: Boolean=true,onClick: ()->Unit) { Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(14.dp)) { Text(text,fontWeight=FontWeight.Bold) } }
@Composable private fun DemoNote() { Text("SIMULATED CHECKOUT · No real grocery order is placed. Prices, fees and rewards are demo estimates. No actual rewards are earned.",fontSize=12.sp,color=Ink,modifier=Modifier.fillMaxWidth().background(Color(0xFFE9EDDC),RoundedCornerShape(12.dp)).padding(15.dp)) }

@Composable private fun ShoppingControls(vm:WalletViewModel) {
 Panel {
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
   OutlinedButton(onClick={vm.servings--;vm.settingsChanged()},enabled=vm.servings>1) { Text("−") }
   Text("${vm.servings} servings",fontWeight=FontWeight.Bold,color=Ink)
   OutlinedButton(onClick={vm.servings++;vm.settingsChanged()},enabled=vm.servings<12) { Text("+") }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   FilterChip(selected=vm.fulfillment=="pickup",onClick={vm.fulfillment="pickup";vm.settingsChanged()},label={Text("Pick up")})
   FilterChip(selected=vm.fulfillment=="delivery",onClick={vm.fulfillment="delivery";vm.settingsChanged()},label={Text("Delivery")})
  }
  Row(verticalAlignment=Alignment.CenterVertically) {
   Switch(checked=vm.organicOnly,onCheckedChange={vm.organicOnly=it;vm.settingsChanged()})
   Text("Organic products only",modifier=Modifier.padding(start=12.dp),color=Ink)
  }
  Text("Organic is an explicit demo product attribute. No quality is inferred from store rating.",fontSize=11.sp,color=Muted)
 }
}
