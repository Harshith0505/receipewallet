package com.recipewallet.app

import android.os.Bundle
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
    val back: () -> Unit = { if (!vm.busy) { if (vm.step == 4) vm.restart() else vm.step--; vm.error = null } }
    BackHandler(enabled = vm.step in 1..4 && !vm.busy, onBack = back)
    if (vm.step == 0) { HomeScreen(vm); return }
    key(vm.step) {
        ShoppingPage(vm.step, onBack = back, backEnabled = !vm.busy) {
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
    SmallNote(if (vm.isPersonal) "Review the suggested products and pack counts. Uncheck anything you already have or don’t need."
        else "Uncheck anything you already have or don’t need.")
    QuietCard {
        vm.recipe.ingredients.forEachIndexed { index, ingredient ->
            if (index > 0) HorizontalDivider(color = ShopLine)
            val included = ingredient.id in vm.selected
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable { vm.toggle(ingredient.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = included, onCheckedChange = { vm.toggle(ingredient.id) })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ingredient.name, color = ShopInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        if (!vm.isPersonal) SmallNote(ingredient.qty)
                    }
                    if (!vm.isPersonal || vm.productFor(ingredient.id) != null)
                        Text(money(ingredient.basePrice), color = ShopInk, fontSize = 14.sp)
                }
                if (vm.isPersonal && included) ProductChoice(vm, ingredient)
            }
        }
    }
    if (vm.isPersonal && !vm.canCompare && vm.selected.isNotEmpty())
        SmallNote("Choose a product for every included ingredient to see the complete estimate.")
    QuietCard {
        AmountRow(if (vm.canCompare) "Ingredient estimate" else "Matched items so far", money(vm.subtotal), prominent = true)
        SmallNote(if (vm.isPersonal) "Sample package prices. Check the pack sizes against your recipe quantities. Shop adjustments and delivery are shown next."
            else "Sample portion prices. Shop adjustments and delivery are shown next.")
    }
    vm.error?.let { SmallNote(it) }
    ShopButton("Compare stores  →", enabled = vm.canCompare, onClick = { vm.compare() })
    SmallNote("Demo prices · Tax excluded · No real grocery order")
}

@Composable
private fun ProductChoice(vm: WalletViewModel, ingredient: Ingredient) {
    var expanded by remember(ingredient.id) { mutableStateOf(false) }
    val product = vm.productFor(ingredient.id)
    val count = vm.packCounts[ingredient.id] ?: 1
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
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
        TextButton(onClick = { vm.setPacks(ingredient.id, count - 1) }, enabled = count > 1,
            modifier = Modifier.semantics { contentDescription = "Fewer packs of ${product.name}" }) { Text("−") }
        Text("$count", color = ShopInk)
        TextButton(onClick = { vm.setPacks(ingredient.id, count + 1) }, enabled = count < 20,
            modifier = Modifier.semantics { contentDescription = "More packs of ${product.name}" }) { Text("+") }
    }
}

@Composable
private fun ShopComparison(vm: WalletViewModel) {
    PageHeading("Choose your shop", vm.recipe.name)
    SmallNote("Sorted by estimated cost after rewards. Each shop includes its best sample Visa card.")
    vm.rankings.forEachIndexed { index, quote ->
        QuietCard {
            if (index == 0) Text("Lowest estimated total", color = ShopInk, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(quote.store.name, color = ShopInk, fontSize = 21.sp, fontWeight = FontWeight.Medium)
            SmallNote("${quote.store.rating} rating · ${quote.store.distanceMiles} mi · Demo location")
            AmountRow("Groceries", money(quote.groceries))
            AmountRow("Delivery", money(quote.store.deliveryFee))
            HorizontalDivider(color = ShopLine)
            AmountRow("Pay today", money(quote.charge), prominent = true)
            AmountRow("Estimated rewards later", money(quote.rewards))
            AmountRow("After rewards", money(quote.net))
            SmallNote(quote.card.name)
            ShopButton(if (index == 0) "Continue with this store  →" else "Choose this store",
                outlined = index != 0, onClick = { vm.checkout(quote.store.id) })
        }
    }
    SmallNote("Fictional shops and estimates. Rewards do not reduce today’s charge.")
}

@Composable
private fun CheckoutPage(vm: WalletViewModel, pay: () -> Unit) {
    PageHeading("Review & pay", "${vm.recipe.name} · ${vm.current.store.name}")
    QuietCard {
        Text("Your basket", color = ShopInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        vm.recipe.ingredients.filter { it.id in vm.selected }.forEach {
            Text(it.name, color = ShopInk, fontSize = 14.sp)
            if (vm.isPersonal) SmallNote(it.qty)
        }
    }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose your Visa card", color = ShopInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        val best = vm.catalog.cards.maxBy { it.rewards(vm.current.groceries) }
        vm.catalog.cards.forEach { card ->
            val selected = card.id == vm.current.card.id
            QuietCard(selected = selected, modifier = Modifier.selectable(selected = selected,
                enabled = !vm.busy, role = Role.RadioButton, onClick = { vm.switchCard(card.id) })) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(card.name, color = ShopInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        SmallNote(card.label)
                    }
                    Text(money(card.rewards(vm.current.groceries)), color = ShopInk, fontSize = 19.sp)
                }
                if (card.id == best.id) SmallNote("Best estimated rewards for this basket")
                if (selected) SmallNote(card.notes)
            }
        }
    }
    QuietCard {
        AmountRow("Groceries", money(vm.current.groceries))
        AmountRow("Delivery", money(vm.current.store.deliveryFee))
        HorizontalDivider(color = ShopLine)
        AmountRow("Pay today", money(vm.current.charge), prominent = true)
        AmountRow("Estimated rewards later", money(vm.current.rewards))
        AmountRow("Estimated cost after rewards", money(vm.current.net))
        SmallNote("Future rewards are separate from your payment. The card recommendation is advisory; enter a Stripe test card at payment.")
    }
    vm.error?.let { QuietCard { Text(it, color = ShopInk, fontSize = 14.sp) } }
    if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    ShopButton(if (vm.busy) "Processing…" else "Pay ${money(vm.current.charge)} · Stripe test",
        enabled = !vm.busy && vm.canCompare, onClick = pay)
    if (vm.canVerify) ShopButton("Verify pending test payment", enabled = !vm.busy,
        outlined = true, onClick = { vm.verify() })
    ShopButton("Preview confirmation without payment", enabled = !vm.busy,
        outlined = true, onClick = { vm.walkthrough() })
    SmallNote("Test checkout only. Tax excluded. No real grocery order, delivery, or rewards.")
}

@Composable
private fun ReceiptPage(vm: WalletViewModel) {
    val receipt = vm.receipt ?: return
    PageHeading(if (vm.paymentVerified) "Test payment confirmed" else "Receipt preview",
        if (vm.paymentVerified) "STRIPE TEST PAYMENT VERIFIED" else "No payment was made.")
    QuietCard {
        Text(vm.recipe.name, color = ShopInk, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        SmallNote(receipt.store.name)
        SmallNote(receipt.card.name)
        HorizontalDivider(color = ShopLine)
        AmountRow(if (vm.paymentVerified) "Test amount paid" else "Amount preview", money(receipt.charge), prominent = true)
        AmountRow("Illustrative rewards later", money(receipt.rewards))
        AmountRow("Estimated cost after rewards", money(receipt.net))
    }
    SmallNote("No real grocery order was placed. No delivery is scheduled and no actual rewards were earned.")
    ShopButton("Plan another meal", onClick = { vm.restart() })
}

@Composable
internal fun AgentPlanReview(vm: WalletViewModel) {
    QuietCard {
        Text("Your meal", color = ShopInk, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(vm.agentReply, color = ShopInk, fontSize = 14.sp)
        val recipe = vm.plannedRecipe
        val quote = vm.plannedQuote
        if (recipe != null && quote != null) {
            Text(recipe.name, color = ShopInk, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            SmallNote("${vm.planDiet} · ${if (vm.planBudget >= 0) money(vm.planBudget) + " upfront budget" else "No budget set"}${if (vm.planTime > 0) " · Under ${vm.planTime} min" else ""}")
            Text("To buy: " + recipe.ingredients.filter { it.id in vm.planIds("ingredientIds") }.joinToString { it.name }, color = ShopInk, fontSize = 13.sp)
            SmallNote("Already owned: " + recipe.ingredients.filter { it.id in vm.planIds("ownedIngredientIds") }.joinToString { it.name }.ifBlank { "None specified" })
            SmallNote("${quote.store.name} · ${quote.card.name}")
            AmountRow("Groceries", money(quote.groceries))
            AmountRow("Delivery", money(quote.store.deliveryFee))
            AmountRow("Amount charged before rewards", money(quote.charge), prominent = true)
            AmountRow("Estimated future rewards", money(quote.rewards))
            AmountRow("Estimated cost after rewards", money(quote.net))
            if (!vm.planWithinBudget) Text("This is above your budget. Revise your request or budget before approving.", color = ShopInk)
            ShopButton("Approve basket and continue  →", enabled = vm.planWithinBudget && !vm.aiBusy,
                onClick = { vm.approvePlan() })
        }
        var details by remember { mutableStateOf(false) }
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide comparison details" else "How this was chosen") }
        if (details) vm.agentTrace.forEach { SmallNote(it) }
        TextButton(onClick = { vm.newPlan() }, enabled = !vm.aiBusy) { Text("Start a new shop") }
        SmallNote("Sample prices and card terms. Ingredient portions and tax exclusions apply. Review dietary requirements yourself.")
    }
}
