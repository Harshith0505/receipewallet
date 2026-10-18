package com.recipewallet.app

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
import androidx.compose.runtime.saveable.rememberSaveable
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
    private val library: RecipeCloudViewModel by viewModels()
    private val retailer: RetailerViewModel by viewModels()
    private lateinit var sheet: PaymentSheet
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sheet = PaymentSheet(this) { result -> when (result) {
            is PaymentSheetResult.Completed -> vm.verify()
            is PaymentSheetResult.Canceled -> vm.paymentCanceled()
            is PaymentSheetResult.Failed -> vm.paymentFailed(result.error.localizedMessage ?: "Payment failed. Try again.")
        } }
        receiveConfirmation(intent)
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
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveConfirmation(intent)
    }
    private fun receiveConfirmation(incoming: Intent?) {
        val callback = incoming?.data?.toString() ?: return
        if (isKrogerCartReturn(callback)) {
            setIntent(Intent(incoming).apply { data = null })
            retailer.checkCartConnection()
            return
        }
        if (!isSignupCallback(callback)) return
        // The callback is handled in memory; never retain authorization codes in the activity intent.
        setIntent(Intent(incoming).apply { data = null })
        if(vm.canEditCheckout) vm.step = 0
        library.confirmFromLink(callback)
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
    var retailerPrices by rememberSaveable { mutableStateOf(false) }
    if (retailerPrices) {
        RetailerScreen(if (vm.step == 1) vm.recipe.ingredients.filter { it.id in vm.selected }.map {
            RetailerNeed(it.id, it.name, if (vm.isPersonal) "Review the saved recipe amount and choose sufficient packages."
                else "${kotlin.math.ceil(it.quantity.toDouble() * vm.servings / vm.recipe.baseServings).toLong()} ${it.unit} needed for ${vm.servings} servings")
        } else emptyList(), onBack = { retailerPrices = false }, contextId = if (vm.step == 1) vm.recipeId else "browse")
        return
    }
    val openRetailerPrices: () -> Unit = { if (vm.canEditCheckout) retailerPrices = true }
    val back: () -> Unit = { if (vm.step == 4) vm.restart() else vm.back() }
    BackHandler(enabled = vm.step in 1..4, onBack = back)
    if (vm.step == 0) { HomeScreen(vm, openRetailerPrices); return }
    key(vm.step) {
        ShoppingPage(vm.step, onBack = back, backEnabled = vm.canEditCheckout) {
            when (vm.step) {
                1 -> IngredientPage(vm, openRetailerPrices)
                2 -> ShopComparison(vm)
                3 -> CheckoutPage(vm, pay)
                4 -> ReceiptPage(vm)
            }
        }
    }
}

@Composable
private fun IngredientPage(vm: WalletViewModel, onRetailerPrices: () -> Unit) {
    PageHeading("Ingredients", vm.recipe.name)
    SmallNote(if(vm.isPersonal) "Confirm the suggested products and pack counts against your saved recipe. Uncheck pantry items." else "Unchecked ingredients are already in your pantry. Purchases use whole packages.")
    ShoppingControls(vm)
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
    ShopButton("Look up retailer prices", outlined = true, enabled = vm.canEditCheckout, onClick = onRetailerPrices)
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
    PageHeading("Choose your shop",vm.recipe.name)
    SmallNote("Complete baskets ranked by estimated cost after rewards. Today's charge includes whole packages and fulfillment fees.")
    ShoppingControls(vm)
    StudentDiscountControl(vm)
    vm.rankings.forEachIndexed { index,q ->
        QuietCard {
            if(index==0&&q.complete) Text("Lowest estimated cost",color=ShopInk,fontWeight=FontWeight.Medium)
            Text(q.store.name,color=ShopInk,fontSize=21.sp,fontWeight=FontWeight.Medium)
            SmallNote("${q.store.rating} rating · ${q.store.distanceMiles} mi")
            SmallNote(q.store.address)
            StudentOfferLabel(q.store,vm.studentEligible)
            if(q.complete) {
                BasketLines(q)
                AmountRow("Whole-package groceries",money(q.groceries))
                StudentSavingsRow(q)
                AmountRow("Fulfillment fees",money(q.fees));AmountRow("Demo tax (0%)",money(q.tax))
                AmountRow("Pay today",money(q.charge),prominent=true)
                AmountRow("Estimated future rewards",money(q.rewards));AmountRow("Estimated cost after rewards",money(q.net))
                SmallNote(q.card.name)
                ShopButton("Review checkout →",outlined=index!=0,onClick={vm.checkout(q.store.id)})
            } else SmallNote("Unavailable: "+q.missing.joinToString()+". Partial baskets cannot be checked out.")
            val context=LocalContext.current
            ShopButton("Walking directions · Google Maps",outlined=true,onClick={
                try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(q.store.address)+"&travelmode=walking"))) }
                catch(_:Exception) { vm.error="No browser or maps app is available" }
            })
        }
    }
    vm.error?.let { SmallNote(it) }
    SmallNote(if(vm.isPersonal) "Sample products and store adjustments; saved-recipe stock and organic attributes are unverified. No retailer integration." else "Real locations for directions only. Prices, stock, organic attributes, fees and fulfillment are simulated.")
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
    StudentDiscountControl(vm)
    StudentOfferLabel(vm.current.store,vm.studentEligible)
    Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Choose your Visa card",color=ShopInk,fontSize=18.sp,fontWeight=FontWeight.Medium)
        val best=vm.catalog.cards.maxBy { it.rewards(vm.current.discountedGroceries) }
        vm.catalog.cards.forEach { card ->
            val selected=card.id==vm.current.card.id
            QuietCard(selected=selected,modifier=Modifier.selectable(selected=selected,enabled=vm.canEditCheckout,role=Role.RadioButton,onClick={vm.switchCard(card.id)})) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=selected,onClick=null)
                    Column(Modifier.weight(1f).padding(start=10.dp)) { Text(card.name,color=ShopInk);SmallNote(card.label) }
                    Text(money(card.rewards(vm.current.discountedGroceries)),color=ShopInk,fontSize=19.sp)
                }
                if(card.id==best.id) SmallNote("Best estimated rewards for this basket")
                if(selected) SmallNote(card.notes)
            }
        }
    }
    QuietCard {
        AmountRow("Whole-package groceries",money(vm.current.groceries))
        StudentSavingsRow(vm.current)
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
        AmountRow("Whole-package groceries",money(receipt.groceries));StudentSavingsRow(receipt);AmountRow("Fulfillment fees",money(receipt.fees));AmountRow("Demo tax (0%)",money(receipt.tax))
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
    QuietCard {
        Text("Your meal", color = ShopInk, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(vm.agentReply, color = ShopInk, fontSize = 14.sp)
        if(vm.agentChanges.isNotEmpty()) {
            Text("What changed",color=ShopInk,fontWeight=FontWeight.Medium)
            vm.agentChanges.forEach { SmallNote(it) }
        }
        vm.generatedRecipe?.let { draft ->
            Text(draft.getString("name"), color = ShopInk, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            SmallNote("${draft.getInt("servings")} servings · ${draft.getInt("prepMinutes")} min estimated")
            val budget = draft.getJSONObject("constraints").getLong("budgetCents")
            if (budget >= 0) SmallNote("Requested budget: ${money(budget)} · not yet priced")
            val ingredients = draft.getJSONArray("ingredients")
            for (index in 0 until ingredients.length()) {
                val item = ingredients.getJSONObject(index)
                Text("${item.getString("amount")} ${item.getString("name")}" + if (item.getBoolean("owned")) " · already owned" else "", color = ShopInk, fontSize = 13.sp)
            }
            val instructions = draft.getJSONArray("steps")
            for (index in 0 until instructions.length()) SmallNote("${index + 1}. ${instructions.getString(index)}")
            SmallNote("AI recipe draft. Review quantities and dietary requirements. Prices and availability are checked when you choose products; this draft has no quoted total.")
            ShopButton("Review ingredients →", enabled = !vm.aiBusy, onClick = { vm.approveGeneratedRecipe() })
        }
        val recipe = vm.plannedRecipe
        val quote = vm.plannedQuote
        if (recipe != null && quote != null) {
            Text(recipe.name, color = ShopInk, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            SmallNote("${vm.planDiet} · ${if (vm.planBudget >= 0) money(vm.planBudget) + " upfront budget" else "No budget set"}${if (vm.planTime > 0) " · Under ${vm.planTime} min" else ""}")
            Text("To buy: " + recipe.ingredients.filter { it.id in vm.planIds("ingredientIds") }.joinToString { it.name }, color = ShopInk, fontSize = 13.sp)
            SmallNote("Already owned: " + recipe.ingredients.filter { it.id in vm.planIds("ownedIngredientIds") }.joinToString { it.name }.ifBlank { "None specified" })
            SmallNote("${quote.store.name} · ${quote.card.name}")
            SmallNote(quote.card.label+". "+quote.card.notes)
            AmountRow("Groceries", money(quote.groceries))
            StudentSavingsRow(quote)
            AmountRow("Fulfillment fees", money(quote.fees))
            AmountRow("Demo tax (0%)",money(quote.tax))
            AmountRow("Amount charged before rewards", money(quote.charge), prominent = true)
            AmountRow("Estimated future rewards", money(quote.rewards))
            AmountRow("Estimated cost after rewards", money(quote.net))
            if (!vm.planWithinBudget) Text("This is above your budget. Revise your request or budget before approving.", color = ShopInk)
            ShopButton("Review package-priced basket  →", enabled = vm.planWithinBudget && !vm.aiBusy,
                onClick = { vm.approvePlan() })
        }
        var details by remember { mutableStateOf(false) }
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide comparison details" else "How this was chosen") }
        if (details) vm.agentTrace.forEach { SmallNote(it) }
        TextButton(onClick = { vm.newPlan() }, enabled = !vm.aiBusy) { Text("Start a new shop") }
        if (vm.generatedRecipe == null) SmallNote("Two-serving pickup plan with whole packages and configured 0% demo tax. Adjust shopping preferences on the next screen. Review dietary requirements yourself.")
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


@Composable
private fun StudentDiscountControl(vm: WalletViewModel) {
    QuietCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Student discounts", color = ShopInk, fontWeight = FontWeight.Medium)
                SmallNote("Demo student mode · applies sample offers to groceries.")
            }
            Switch(checked = vm.studentEligible, enabled = vm.canEditCheckout,
                onCheckedChange = vm::changeStudentEligibility,
                modifier = Modifier.semantics { contentDescription = "Apply demo student discounts" })
        }
        SmallNote("MVP offers only. Student status and retailer offers are not verified.")
    }
}

@Composable
private fun StudentOfferLabel(store: Store, eligible: Boolean) {
    val rate = store.studentDiscountBps / 100
    SmallNote(if (rate == 0L) "No student offer in this demo"
        else "$rate% student offer · demo" + if (eligible) " · applied" else " · enable student mode to apply")
}

@Composable
private fun StudentSavingsRow(quote: Quote) {
    if (quote.studentDiscount > 0) AmountRow("Student discount · demo (${quote.store.studentDiscountBps / 100}%)", "−${money(quote.studentDiscount)}")
}
