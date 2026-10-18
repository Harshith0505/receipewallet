package com.recipewallet.app

import androidx.compose.ui.graphics.Color

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

class MainActivity : ComponentActivity() {
    private val vm: WalletViewModel by viewModels()
    private lateinit var sheet: PaymentSheet
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sheet = PaymentSheet(this) { result -> when (result) {
            is PaymentSheetResult.Completed -> vm.verify()
            is PaymentSheetResult.Canceled -> vm.paymentCanceled()
            is PaymentSheetResult.Failed -> vm.paymentFailed(result.error.localizedMessage ?: "Payment failed. Try again.")
        } }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = ShopInk, background = ShopPaper,
                surface = androidx.compose.ui.graphics.Color.White, secondary = ShopInk,
                onSurface = ShopInk, onSurfaceVariant = ShopMuted, onBackground = ShopInk,
                outline = ShopLine, outlineVariant = ShopLine, surfaceTint = ShopInk,
                surfaceContainer = androidx.compose.ui.graphics.Color.White,
                surfaceContainerLow = androidx.compose.ui.graphics.Color.White,
                surfaceContainerHigh = androidx.compose.ui.graphics.Color.White,
                surfaceContainerHighest = ShopTint)) {
                Wallet(vm) { startTestCheckout() }
            }
        }
    }
    fun startTestCheckout() {
        vm.pay { key, secret ->
            PaymentConfiguration.init(this, key)
            sheet.presentWithPaymentIntent(secret, PaymentSheet.Configuration(
                merchantDisplayName = "Recipe Wallet · TEST", allowsDelayedPaymentMethods = false))
        }
    }
}

@Composable
private fun Wallet(vm: WalletViewModel, pay: () -> Unit) {
    val back: () -> Unit = { if (vm.step == 4) vm.restart() else vm.back() }
    BackHandler(enabled = vm.step in 1..4, onBack = back)
    if (vm.step == 0) { HomeScreen(vm); return }
    key(vm.step) {
        ShoppingPage(vm.step, onBack = back, backEnabled = vm.canEditCheckout) {
            when (vm.step) {
                1 -> IngredientPage(vm)
                2 -> ShopComparison(vm)
                3 -> CheckoutPage(vm, pay)
                4 -> ReceiptPage(vm)
            }
        }
    }
}

@Composable
private fun IngredientPage(vm: WalletViewModel) {
    PageHeading("Ingredients", vm.recipe.name)
    if(!vm.studio.fits(vm.recipe))Signal("Excluded ingredients: "+vm.studio.conflicts(vm.recipe).joinToString()+". Choose another recipe or revise your exclusions.",Danger)
    if(vm.studio.faith!="None")Signal("${vm.studio.faith}: verify product certification and preparation.",Warn)
    SmallNote(if(vm.isPersonal) "Confirm the suggested products and pack counts against your saved recipe. Uncheck pantry items." else "Unchecked ingredients are already in your pantry. Purchases use whole packages.")
    ShoppingControls(vm)
    Signal("${vm.selected.size} to buy · ${vm.recipe.ingredients.size-vm.selected.size} already owned")
    SmallNote("Uncheck an ingredient to keep it out of this purchase. Confirm you have enough; this is a meal-only choice.")
    QuietCard {
        vm.recipe.ingredients.forEachIndexed { index, ingredient ->
            if(index>0) HorizontalDivider(color=ShopLine)
            val included=ingredient.id in vm.selected
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable(enabled=vm.canEditCheckout) { vm.toggle(ingredient.id) },verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=included,onCheckedChange={vm.toggle(ingredient.id)},enabled=vm.canEditCheckout)
                    Column(Modifier.weight(1f)) {
                        Text(ingredient.name,color=ShopInk,fontSize=16.sp,fontWeight=FontWeight.Medium)
                        if(!vm.isPersonal) SmallNote("${kotlin.math.ceil(ingredient.quantity.toDouble()*vm.servings/vm.recipe.baseServings).toLong()} ${ingredient.unit} needed"+if(!included) " · already owned" else "")
                    }
                }
                if(vm.isPersonal&&included) ProductChoice(vm,ingredient)
            }
        }
    }
    if(vm.isPersonal) {
        if(!vm.canCompare&&vm.selected.isNotEmpty()) SmallNote("Match every included ingredient and choose at most 20 packs per product.")
        AmountRow("Selected package base estimate",money(vm.subtotal),prominent=true)
        SmallNote("Review pack counts yourself. Saved-recipe quantities are not automatically converted. Store adjustments and fees appear next; product stock and organic attributes are not verified for this separate demo catalog.")
    }
    if(vm.selected.isEmpty()) SmallNote("Your pantry covers this recipe. No grocery purchase is needed.")
    if(vm.canCompare&&vm.rankings.none { it.complete }) SmallNote("No store can supply this selection. Review the organic or fulfillment preference.")
    ShopButton("Compare complete baskets →",enabled=vm.canEditCheckout&&vm.canCompare&&vm.rankings.any { it.complete },onClick={vm.compare()})
    vm.error?.let { SmallNote(it) }
    SmallNote("Demo package prices · Configured tax 0% · No real grocery order")
}

@Composable
private fun ProductChoice(vm: WalletViewModel, ingredient: Ingredient) {
    var expanded by remember(ingredient.id) { mutableStateOf(false) }
    val product = vm.productFor(ingredient.id)
    val count = vm.packCounts[ingredient.id] ?: 1
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(enabled=vm.canEditCheckout,onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(product?.name ?: "Choose a product", modifier = Modifier.weight(1f))
            Text("⌄")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            vm.catalog.products.forEach { item ->
                DropdownMenuItem(text = {
                    Column { Text(item.name); SmallNote("${item.pack} · ${money(item.price)}") }
                }, onClick = { vm.matchProduct(ingredient.id, item.id); expanded = false })
            }
        }
    }
    if (product != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { SmallNote("${product.pack} · ${money(product.price)} / pack") }
        TextButton(onClick = { vm.setPacks(ingredient.id, count - 1) }, enabled = vm.canEditCheckout && count > 1,
            modifier = Modifier.semantics { contentDescription = "Fewer packs of ${product.name}" }) { Text("−") }
        Text("$count", color = ShopInk)
        TextButton(onClick = { vm.setPacks(ingredient.id, count + 1) }, enabled = vm.canEditCheckout && count < 20,
            modifier = Modifier.semantics { contentDescription = "More packs of ${product.name}" }) { Text("+") }
    }
}

@Composable
private fun ShopComparison(vm: WalletViewModel) {
    PageHeading("Find your basket", "${vm.recipe.name} · compare the whole picture")
    ShoppingControls(vm)
    val quotes=vm.rankings
    var selected by remember { mutableStateOf(quotes.firstOrNull { it.complete }?.store?.id ?: quotes.first().store.id) }
    LaunchedEffect(vm.fulfillment,vm.organicOnly,vm.servings) { if(quotes.none { it.store.id==selected&&it.complete }) quotes.firstOrNull { it.complete }?.let { selected=it.store.id } }
    ShopMap(quotes,selected) { selected=it }
    ChipRail(quotes.map { it.store.name+if(it.complete) " · "+money(it.charge) else " · unavailable" }) { selected=quotes[it].store.id }
    val q=quotes.first { it.store.id==selected }
    val best=quotes.filter { it.complete }.minOfOrNull { it.charge }
    StudioCard {
        Eyebrow("Your selected shop")
        StudioTitle(q.store.name)
        SmallNote("${q.store.rating} rating · ${q.store.distanceMiles} sample mi · ${q.store.address}")
        if(q.complete) {
            PriceTicket(q,best,if(vm.planBudget>=0)vm.planBudget else null)
            var expanded by remember(q.store.id) { mutableStateOf(false) }
            TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Hide package breakdown ↑" else "See every package & price ↓") }
            if(expanded) BasketLines(q)
            ShopButton("Review checkout →",onClick={vm.checkout(q.store.id)})
        } else {
            Signal("! This basket is incomplete",Danger)
            Text("Unavailable: "+q.missing.joinToString(),color=Danger)
            SmallNote("Choose another shop or revise your preferences. Partial baskets cannot be checked out.")
        }
        val context=LocalContext.current
        ShopButton("Walking directions · Google Maps",outlined=true,onClick={
            try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(q.store.address)+"&travelmode=walking"))) }
            catch(_:Exception) { vm.error="No browser or maps app is available" }
        })
    }
    StudioTitle("At a glance")
    SmallNote("Green = lowest upfront total · Amber = higher total · Red = unavailable. Estimated future rewards are separate.")
    quotes.forEach { quote ->
        StudioCard(tint=when(quote.store.id){"market"->Color(0xFFE2E9F7);"fresh"->Color(0xFFF6DFDC);else->Color(0xFFE1EADA)},modifier=Modifier.clickable { selected=quote.store.id }) {
            Text(when(quote.store.id){"market"->"QFC  /  FRESH PICKS";"fresh"->"TRADER JOE’S  /  NEIGHBORHOOD FINDS";else->"WHOLE FOODS  /  MARKET FINDS"},color=when(quote.store.id){"market"->Color(0xFF254C87);"fresh"->Color(0xFFA33235);else->Color(0xFF31593B)},fontWeight=FontWeight.Bold)
            AmountRow(quote.store.name,if(quote.complete)money(quote.charge)else "Unavailable",prominent=true)
            Signal(if(!quote.complete)"! Cannot supply this basket"else if(quote.charge==best)"✓ Lowest upfront total"else "↗ ${money(quote.charge-(best?:quote.charge))} more",if(!quote.complete)Danger else if(quote.charge==best)Good else Warn)
        }
    }
    vm.error?.let { Text(it,color=Danger) }
    SmallNote("Real locations for directions only. Prices, distances, stock, product attributes, fees and fulfillment are simulated.")
}

@Composable
private fun CheckoutPage(vm: WalletViewModel,pay:()->Unit) {
    PageHeading("Review & pay","${vm.recipe.name} · ${vm.current.store.name}")
    QuietCard {
        Text("Your basket",color=ShopInk,fontSize=18.sp,fontWeight=FontWeight.Medium)
        SmallNote(vm.fulfillment+if(vm.isPersonal) " · selected packs" else " · ${vm.servings} servings")
        BasketLines(vm.current)
        ShopButton("Edit basket or store",enabled=vm.canEditCheckout,outlined=true,onClick={vm.back()})
    }
    Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Choose your Visa card",color=ShopInk,fontSize=18.sp,fontWeight=FontWeight.Medium)
        val best=vm.catalog.cards.maxBy { it.rewards(vm.current.groceries) }
        vm.catalog.cards.forEach { card ->
            val selected=card.id==vm.current.card.id
            QuietCard(selected=selected,modifier=Modifier.selectable(selected=selected,enabled=vm.canEditCheckout,role=Role.RadioButton,onClick={vm.switchCard(card.id)})) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=selected,onClick=null)
                    Column(Modifier.weight(1f).padding(start=10.dp)) { Text(card.name,color=ShopInk);SmallNote(card.label) }
                    Text(money(card.rewards(vm.current.groceries)),color=ShopInk,fontSize=19.sp)
                }
                if(card.id==best.id) SmallNote("Best estimated rewards for this basket")
                if(selected) SmallNote(card.notes)
            }
        }
    }
    QuietCard {
        AmountRow("Whole-package groceries",money(vm.current.groceries))
        AmountRow("Fulfillment fees",money(vm.current.fees));AmountRow("Demo tax (0%)",money(vm.current.tax))
        AmountRow("Total charged today · test",money(vm.current.charge),prominent=true)
        AmountRow("Estimated future rewards",money(vm.current.rewards));AmountRow("Estimated cost after rewards",money(vm.current.net))
        SmallNote("Future rewards do not reduce today's payment. The sample card is advisory; Stripe collects separate test card details. No actual rewards are earned.")
    }
    vm.error?.let { QuietCard { Text(it,color=ShopInk) } }
    if(!vm.checkoutWithinBudget) SmallNote("This revised basket exceeds your budget. Review the basket or revise your meal request before paying.")
    if(vm.awaitingVerification) SmallNote("Verify the existing test payment before editing this basket or trying another payment.")
    if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    ShopButton(if(vm.busy) "Processing…" else if(vm.awaitingVerification) "Verification required" else "Approve & pay ${money(vm.current.charge)}",enabled=vm.canEditCheckout&&vm.canCompare&&vm.current.complete&&vm.checkoutWithinBudget,onClick=pay)
    if(vm.canVerify) ShopButton("Verify pending test payment",enabled=!vm.busy,outlined=true,onClick={vm.verify()})
    ShopButton("Preview confirmation without payment",enabled=vm.canEditCheckout&&vm.canCompare&&vm.current.complete,outlined=true,onClick={vm.walkthrough()})
    SmallNote("Simulated checkout. No real grocery order, delivery or rewards. Use test card 4242 4242 4242 4242, a future expiry, a three-digit test CVC and a test ZIP.")
}

@Composable
private fun ReceiptPage(vm:WalletViewModel) {
    val receipt=vm.receipt ?: return
    PageHeading(if(vm.paymentVerified) "Test payment confirmed" else "Receipt preview",if(vm.paymentVerified) "STRIPE TEST PAYMENT VERIFIED" else "No payment was made.")
    QuietCard {
        Text(vm.receiptRecipeName,color=ShopInk,fontSize=21.sp,fontWeight=FontWeight.Medium)
        SmallNote(receipt.store.name+" · "+receipt.fulfillment)
        BasketLines(receipt)
        AmountRow("Whole-package groceries",money(receipt.groceries));AmountRow("Fulfillment fees",money(receipt.fees));AmountRow("Demo tax (0%)",money(receipt.tax))
        AmountRow(if(vm.paymentVerified) "Test amount paid · USD" else "Amount preview · USD",money(receipt.charge),prominent=true)
        SmallNote("Advisory rewards card: "+receipt.card.name)
        AmountRow("Estimated future rewards",money(receipt.rewards));AmountRow("Estimated cost after rewards",money(receipt.net))
        vm.receiptPaymentId?.let { SmallNote("Stripe test reference: $it") }
    }
    SmallNote("No real grocery order was placed. No delivery is scheduled and no actual rewards were earned.")
    ShopButton("Plan another meal",onClick={vm.restart()})
}

@Composable
internal fun AgentPlanReview(vm: WalletViewModel) {
    StudioCard(tint=Mint) {
        Eyebrow("Your meal · ready for your touch")
        StudioTitle(vm.plannedRecipe?.name ?: "Let's fine-tune your request")
        Text(vm.agentReply,color=ShopInk,fontSize=14.sp)
        if(vm.agentChanges.isNotEmpty()) {
            StudioCard(tint=androidx.compose.ui.graphics.Color.White) {
                Signal("↻ What changed",Good)
                vm.agentChanges.forEach { Text("• $it",color=ShopInk,fontSize=15.sp) }
            }
        }
        val recipe=vm.plannedRecipe
        val quote=vm.plannedQuote
        if(recipe!=null&&quote!=null) {
            Signal("${recipe.prepTime} min · ${vm.agentPlan?.optInt("servings",2)} servings · ${vm.planDiet} · ${if(vm.planBudget>=0)money(vm.planBudget)+" budget"else "No budget set"}")
            ReviewSection("01  To buy", "Whole packages in your next basket", Peach) {
                recipe.ingredients.filter { it.id in vm.planIds("ingredientIds") }.forEach { Text("+ "+it.name,color=ShopInk) }
            }
            ReviewSection("02  Already owned", "Confirmed pantry and items from this conversation", Mint) {
                val owned=recipe.ingredients.filter { it.id in vm.planIds("ownedIngredientIds") }
                if(owned.isEmpty())SmallNote("No pantry ingredients specified for this meal.")
                owned.forEach { Text("✓ ${it.name} · "+if(it.id in vm.studio.owned)"My pantry"else "This meal only",color=ShopInk) }
                SmallNote("My pantry is saved on your device. Meal-only items came from your request. Check you have enough for the recipe.")
            }
            ReviewSection("03  Ingredient remix", "Change the vibe, then let the agent revise", Lilac) {
                ChipRail(listOf("Mild · remove chili","Spicy · include chili","Use my pantry"),enabled=!vm.aiBusy){i->
                    vm.preference=when(i){0->"Remove chili from this recipe to make it mild. Keep all other constraints.";1->"Include chili if this recipe supports it. Keep my other constraints.";else->"I already have "+vm.studio.ingredients.filter { it.id in vm.studio.owned }.joinToString { it.name }+". Revise this meal, keeping my constraints."}
                    vm.suggest()
                }
                val omissions=vm.agentPlan?.optJSONArray("omittedIngredientIds")
                if(omissions!=null&&omissions.length()>0) SmallNote("Left out of the recipe: "+(0 until omissions.length()).map { omissions.getString(it) }.mapNotNull { id->recipe.ingredients.firstOrNull { it.id==id }?.name }.joinToString())
                SmallNote("Only supported catalog ingredients can change. Review the result before approving.")
            }
            PriceTicket(quote,budget=vm.planBudget)
            ShopButton("Review package-priced basket  →",enabled=vm.planWithinBudget&&!vm.aiBusy,onClick={vm.approvePlan()})
        }
        var details by remember { mutableStateOf(false) }
        TextButton(onClick={details=!details}) { Text(if(details)"Hide comparison details"else "How this was chosen") }
        if(details)vm.agentTrace.forEach { SmallNote(it) }
        TextButton(enabled=!vm.aiBusy,onClick={vm.newPlan()}){Text("Start a new shop")}
    }
}
@Composable private fun ReviewSection(title:String,subtitle:String,tint:androidx.compose.ui.graphics.Color,content:@Composable ColumnScope.()->Unit) {
    var open by remember { mutableStateOf(true) }
    StudioCard(tint=tint) {
        TextButton(onClick={open=!open},modifier=Modifier.fillMaxWidth()) { Text(title,modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold);Text(if(open)"−"else "+") }
        if(open) {SmallNote(subtitle);content()}
    }
}

@Composable private fun ShoppingControls(vm:WalletViewModel) {
 QuietCard {
  if(!vm.isPersonal) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
   OutlinedButton(onClick={vm.changeServings(vm.servings-1)},enabled=vm.canEditCheckout&&vm.servings>1) { Text("−") }
   Text("${vm.servings} servings",fontWeight=FontWeight.Bold,color=ShopInk)
   OutlinedButton(onClick={vm.changeServings(vm.servings+1)},enabled=vm.canEditCheckout&&vm.servings<12) { Text("+") }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   FilterChip(enabled=vm.canEditCheckout,selected=vm.fulfillment=="pickup",onClick={vm.changeFulfillment("pickup")},label={Text("Pick up")})
   FilterChip(enabled=vm.canEditCheckout,selected=vm.fulfillment=="delivery",onClick={vm.changeFulfillment("delivery")},label={Text("Delivery")})
  }
  if(!vm.isPersonal) Row(verticalAlignment=Alignment.CenterVertically) {
   Switch(enabled=vm.canEditCheckout,checked=vm.organicOnly,onCheckedChange={vm.changeOrganicOnly(it)})
   Text("Organic products only",modifier=Modifier.padding(start=12.dp),color=ShopInk)
  }
  if(!vm.isPersonal) Text("Organic is an explicit demo product attribute. No quality is inferred from store rating.",fontSize=11.sp,color=ShopMuted)
 }
}

@Composable private fun BasketLines(q:Quote) {
 q.items.forEach { item ->
  AmountRow("${item.name} · ${item.packages} × ${item.product.packageQuantity} ${item.unit}",money(item.cost))
  SmallNote("Need ${item.quantity} ${item.unit} · buy ${item.packages*item.product.packageQuantity} ${item.unit}"+if("organic" in item.product.tags) " · organic" else "")
 }
 q.customItems.forEach { item -> AmountRow("${item.product.name} · ${item.packages} × ${item.product.pack}",money(item.cost)) }
}
