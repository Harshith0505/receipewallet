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
                surface = ShopTint, secondary = ShopInk,
                onSurface = ShopInk, onSurfaceVariant = ShopMuted, onBackground = ShopInk,
                outline = ShopLine, outlineVariant = ShopLine, surfaceTint = ShopInk,
                surfaceContainer = ShopTint,
                surfaceContainerLow = ShopPaper,
                surfaceContainerHigh = ShopTint,
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
        if(vm.canEditCheckout&&!retailer.editingBlocked) vm.step = 0
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
    val retailer:RetailerViewModel=androidx.lifecycle.viewmodel.compose.viewModel()
    if(vm.step==5){SampleCheckoutScreen(vm);return}
    if(vm.step==6){RecipeHistoryScreen(vm);return}
    if(vm.step==2&&!vm.illustrativeComparison){
        val needs=vm.recipe.ingredients.filter{it.id in vm.selected}.map{retailerNeed(it,vm.servings,vm.recipe.baseServings,vm.isPersonal).let{need->if(vm.organicOnly)need.copy(name="Organic "+need.name)else need}}
        LiveShoppingScreen(vm.recipeId,vm.recipe.name,needs,vm.catalog.cards,vm.planBudget,onBack={vm.back()},onIllustrativeComparison={vm.illustrativeComparison=true},vm=retailer,meal=vm.currentMealSnapshot(),onSampleCheckout={store,subtotal->vm.startSampleCheckout(store,subtotal)})
        return
    }
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
    RecipeHero(vm.currentMealSnapshot())
    if(!vm.studio.fits(vm.recipe))Signal("Excluded ingredients: "+vm.studio.conflicts(vm.recipe).joinToString()+". Choose another recipe or revise your exclusions.",Danger)
    if(vm.studio.faith!="None")Signal("${vm.studio.faith}: verify product certification and preparation.",Warn)
    SmallNote("Review ingredient quantities and uncheck what you already have. Current retailer products are matched in the next step.")
    ShoppingControls(vm)
    val activeIngredients=vm.recipe.ingredients.filter{it.id !in vm.omittedIngredientIds}
    Signal("${vm.selected.size} to buy · ${activeIngredients.size-vm.selected.size} already owned")
    if(vm.omittedIngredientIds.isNotEmpty())SmallNote("Left out of recipe: "+vm.recipe.ingredients.filter{it.id in vm.omittedIngredientIds}.joinToString{it.name})
    SmallNote("Uncheck an ingredient to keep it out of this purchase. Confirm you have enough; this is a meal-only choice.")
    QuietCard {
        activeIngredients.forEachIndexed { index, ingredient ->
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
                if(vm.isPersonal&&included&&vm.illustrativeComparison) ProductChoice(vm,ingredient)
            }
        }
    }
    if(vm.isPersonal&&vm.illustrativeComparison) {
        if(!vm.canCompare&&vm.selected.isNotEmpty()) SmallNote("Match every included ingredient and choose at most 20 packs per product.")
        AmountRow("Selected package base estimate",money(vm.subtotal),prominent=true)
        SmallNote("Review package counts: recipe quantities are not automatically converted. These illustrative product prices and attributes are unverified; use retailer lookup for current products.")
    }
    InfoDisclosure("Cooking method","Keep the full recipe with your shopping list"){RecipeMethod(vm.currentMealSnapshot().steps)}
    if(vm.selected.isEmpty()) SmallNote("Your pantry covers this recipe. No grocery purchase is needed.")
    if(vm.illustrativeComparison&&vm.canCompare&&vm.rankings.none { it.complete }) SmallNote("No store can supply this selection. Review the organic or fulfillment preference.")
    ShopButton("Compare complete baskets →",enabled=vm.canEditCheckout&&vm.selected.isNotEmpty()&&vm.studio.fits(vm.recipe),onClick={vm.compare()})
    vm.error?.let { SmallNote(it) }
    SmallNote("Current retailer products and prices load next. Check package sizes before approving.")
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
    SmallNote("Illustrative store comparison · Prices, availability and fees are examples.")
    ShopButton("Return to retailer basket",outlined=true,onClick={vm.illustrativeComparison=false})
    ShoppingControls(vm)
    StudentDiscountControl(vm)
    val quotes=vm.rankings
    var selected by remember { mutableStateOf(vm.storeId.takeIf { id->quotes.any { it.store.id==id&&it.complete } } ?: quotes.firstOrNull { it.complete }?.store?.id ?: quotes.first().store.id) }
    LaunchedEffect(vm.fulfillment,vm.organicOnly,vm.servings) { if(quotes.none { it.store.id==selected&&it.complete }) quotes.firstOrNull { it.complete }?.let { selected=it.store.id } }
    ShopMap(quotes,selected) { selected=it }
    ChipRail(quotes.map { it.store.name+if(it.complete) " · "+money(it.charge) else " · unavailable" }) { selected=quotes[it].store.id }
    val q=quotes.first { it.store.id==selected }
    val best=quotes.filter { it.complete }.minOfOrNull { it.charge }
    StudioCard {
        Eyebrow("Your selected shop")
        StudioTitle(q.store.name)
        SmallNote("${q.store.address}")
        if(q.complete) {
            StudentSavingsRow(q)
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
            StudentOfferLabel(quote.store,vm.studentEligible)
            AmountRow(quote.store.name,if(quote.complete)money(quote.charge)else "Unavailable",prominent=true)
            Signal(if(!quote.complete)"! Cannot supply this basket"else if(quote.charge==best)"✓ Lowest upfront total"else "↗ ${money(quote.charge-(best?:quote.charge))} more",if(!quote.complete)Danger else if(quote.charge==best)Good else Warn)
        }
    }
    vm.error?.let { Text(it,color=Danger) }
    SmallNote("Locations are approximate. Estimated baskets do not confirm retailer stock, fulfillment or final prices.")
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
        Text("Compare Visa reward profiles",color=ShopInk,fontSize=18.sp,fontWeight=FontWeight.Medium)
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
        AmountRow("Fulfillment fees",money(vm.current.fees));AmountRow("Tax not included",money(vm.current.tax))
        AmountRow("Total charged today · test",money(vm.current.charge),prominent=true)
        AmountRow("Estimated future rewards",money(vm.current.rewards));AmountRow("Estimated cost after rewards",money(vm.current.net))
        SmallNote("Rewards are estimates from illustrative card profiles and do not reduce today's charge. Stripe collects separate test card details; no rewards are earned.")
    }
    vm.error?.let { QuietCard { Text(it,color=ShopInk) } }
    if(!vm.checkoutWithinBudget) SmallNote("This revised basket exceeds your budget. Review the basket or revise your meal request before paying.")
    if(vm.awaitingVerification) SmallNote("Verify the existing test payment before editing this basket or trying another payment.")
    if(vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    ShopButton("Continue to sample checkout",enabled=vm.canEditCheckout&&vm.canCompare&&vm.current.complete,onClick={vm.startSampleCheckout(vm.current.store.name,vm.current.groceries)})
    InfoDisclosure("Optional Stripe test","A separate payment integration test"){
    ShopButton(if(vm.busy) "Processing…" else if(vm.awaitingVerification) "Verification required" else "Approve & pay ${money(vm.current.charge)}",enabled=vm.canEditCheckout&&vm.canCompare&&vm.current.complete&&vm.checkoutWithinBudget,onClick=pay)
    }
    if(vm.canVerify) ShopButton("Verify pending test payment",enabled=!vm.busy,outlined=true,onClick={vm.verify()})
    ShopButton("Preview confirmation without payment",enabled=vm.canEditCheckout&&vm.canCompare&&vm.current.complete,outlined=true,onClick={vm.walkthrough()})
    SmallNote("Test checkout only: no real charge, grocery order, delivery or rewards. Use 4242 4242 4242 4242, a future expiry, a three-digit test CVC and a test ZIP.")
}

@Composable
private fun ReceiptPage(vm:WalletViewModel) {
    val receipt=vm.receipt ?: return
    PageHeading(if(vm.paymentVerified) "Test payment confirmed" else "Receipt preview",if(vm.paymentVerified) "STRIPE TEST PAYMENT VERIFIED" else "No payment was made.")
    QuietCard {
        Text(vm.receiptRecipeName,color=ShopInk,fontSize=21.sp,fontWeight=FontWeight.Medium)
        SmallNote(receipt.store.name+" · "+receipt.fulfillment)
        BasketLines(receipt)
        AmountRow("Whole-package groceries",money(receipt.groceries));StudentSavingsRow(receipt);AmountRow("Fulfillment fees",money(receipt.fees));AmountRow("Tax not included",money(receipt.tax))
        AmountRow(if(vm.paymentVerified) "Test amount paid · USD" else "Amount preview · USD",money(receipt.charge),prominent=true)
        SmallNote("Advisory rewards card: "+receipt.card.name)
        AmountRow("Estimated future rewards",money(receipt.rewards));AmountRow("Estimated cost after rewards",money(receipt.net))
        vm.receiptPaymentId?.let { SmallNote("Stripe test reference: $it") }
    }
    SmallNote("No real grocery order was placed. No delivery is scheduled and no actual rewards were earned.")
    ShopButton("Open my recipes",onClick={vm.openRecipeHistory()})
    ShopButton("Plan another meal",onClick={vm.restart()})
}

@Composable
internal fun AgentPlanReview(vm: WalletViewModel) {
    StudioCard(tint=Color.White) {
        Eyebrow("Your meal · ready for your touch")
        StudioTitle(vm.plannedRecipe?.name ?: vm.generatedRecipe?.getString("name") ?: "Let's fine-tune your request")
        vm.plannedRecipe?.let{FoodSticker(it.id,Modifier.fillMaxWidth().height(170.dp))}
        vm.generatedRecipe?.let { draft ->
            SmallNote("${draft.getInt("servings")} servings · ${draft.getInt("prepMinutes")} min estimated")
            val budget = draft.getJSONObject("constraints").getLong("budgetCents")
            if (budget >= 0) SmallNote("Requested budget: ${money(budget)} · not yet priced")
            vm.journal.meals.firstOrNull{it.id=="saved:${draft.getString("id")}"}?.let{RecipeFoods(it);RecipeMethod(it.steps)}
            SmallNote("AI recipe draft. Review quantities and dietary requirements. Prices and availability are checked when you choose products; this draft has no quoted total.")
            ShopButton("Review ingredients →", enabled = !vm.aiBusy, onClick = { vm.approveGeneratedRecipe() })
        }
        vm.ownedHints.forEach { hint ->
            SmallNote(hint.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.confirmOwned(hint) }, enabled = !vm.aiBusy) { Text("I have it, remove") }
                TextButton(onClick = { vm.notOwned(hint) }, enabled = !vm.aiBusy) { Text("I’m out of it") }
            }
        }
        val recipe=vm.plannedRecipe
        val quote=vm.plannedQuote
        if(recipe==null||quote==null)Text(vm.agentReply,color=ShopInk,fontSize=14.sp)
        if(recipe!=null&&quote!=null) {
            Signal("${recipe.prepTime} min · ${vm.agentPlan?.optInt("servings",2)} servings · ${vm.planDiet} · ${if(vm.planBudget>=0)money(vm.planBudget)+" budget"else "No budget set"}")
            SmallNote("Review your ingredients, then choose current retailer products. Your shopping total is calculated from the packages you select.")
            ShopButton("Review basket →",enabled=!vm.aiBusy,onClick={vm.approvePlan()})
        InfoDisclosure("AI meal summary",vm.agentReply.take(100)+if(vm.agentReply.length>100)"…"else ""){Text(vm.agentReply,color=ShopInk,fontSize=14.sp)}
        if(vm.agentChanges.isNotEmpty()) {
            InfoDisclosure("What changed","${vm.agentChanges.size} updates to your meal") {
                vm.agentChanges.forEach { Text("• $it",color=ShopInk,fontSize=15.sp) }
            }
        }
            val toBuy=recipe.ingredients.filter { it.id in vm.planIds("ingredientIds") }
            ReviewSection("01  To buy", if(toBuy.isEmpty())"Nothing to buy · your pantry covers this meal"else "${toBuy.size} ingredients · "+toBuy.joinToString{it.name}, Peach) {
                if(toBuy.isEmpty())SmallNote("Nothing to buy. Check that your pantry quantities cover this meal.")
                recipe.ingredients.filter { it.id in vm.planIds("ingredientIds") }.forEach { item->
                    Row(Modifier.fillMaxWidth().semantics{contentDescription="To buy: ${item.name}"},horizontalArrangement=Arrangement.SpaceBetween){Text(item.name,color=ShopInk,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f));Text("${kotlin.math.ceil(item.quantity.toDouble()*(vm.agentPlan?.optInt("servings",2)?:2)/recipe.baseServings).toLong()} ${item.unit}",color=ShopInk)}
                    HorizontalDivider(color=ShopLine)
                }
            }
            ReviewSection("02  Already owned", "Confirmed pantry and items from this conversation", Mint) {
                val owned=recipe.ingredients.filter { it.id in vm.planIds("ownedIngredientIds") }
                if(owned.isEmpty())SmallNote("No pantry ingredients specified for this meal.")
                owned.forEach { Text("✓ ${it.name} · "+if(it.id in vm.studio.owned)"My pantry"else "This meal only",color=ShopInk) }
                SmallNote("My pantry is saved on your device. Meal-only items came from your request. Check you have enough for the recipe.")
            }
            ReviewSection("03  Ingredient remix", "Include or leave out ingredients; the shopping list updates here", Lilac) {
                if(vm.aiBusy){LinearProgressIndicator(Modifier.fillMaxWidth());SmallNote("Updating your shopping list…")}
                if(recipe.ingredients.any{it.id=="chili"}) {
                    ShopButton("Mild · remove chili",outlined=true,enabled=!vm.aiBusy,onClick={vm.remix("omit","chili")})
                    ShopButton("Spicy · include chili",outlined=true,enabled=!vm.aiBusy,onClick={vm.remix("include","chili")})
                }
                ShopButton("Use my pantry",outlined=true,enabled=!vm.aiBusy,onClick={vm.remix("pantry")})
                val omitted=vm.planIds("omittedIngredientIds")
                recipe.ingredients.forEach { ingredient ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=ingredient.id !in omitted,enabled=!vm.aiBusy,onCheckedChange={include->vm.remix(if(include)"include"else "omit",ingredient.id)},modifier=Modifier.semantics{contentDescription="Include ${ingredient.name} in recipe"})
                        Column { Text(ingredient.name,color=ShopInk);SmallNote(if(ingredient.id in omitted)"Left out of recipe"else if(ingredient.id in vm.planIds("ownedIngredientIds"))"Already owned · no purchase"else "Included · to buy") }
                    }
                }
                val omissions=vm.agentPlan?.optJSONArray("omittedIngredientIds")
                if(omissions!=null&&omissions.length()>0) SmallNote("Left out of the recipe: "+(0 until omissions.length()).map { omissions.getString(it) }.mapNotNull { id->recipe.ingredients.firstOrNull { it.id==id }?.name }.joinToString())
                SmallNote("These controls change this recipe. Leaving an ingredient out does not mark it as owned. Review the updated shopping list before approving.")
            }

        }
        if(recipe!=null)InfoDisclosure("Cooking method","Numbered steps saved with this recipe"){RecipeMethod(cookingMethod(recipe.id))}
        var details by remember { mutableStateOf(false) }
        TextButton(onClick={details=!details}) { Text(if(details)"Hide comparison details"else "How this was chosen") }
        if(details)vm.agentTrace.forEach { SmallNote(it) }
        TextButton(enabled=!vm.aiBusy,onClick={vm.newPlan()}){Text("Start a new shop")}
    }
}
@Composable private fun ReviewSection(title:String,subtitle:String,tint:androidx.compose.ui.graphics.Color,content:@Composable ColumnScope.()->Unit) {
    InfoDisclosure(title,subtitle,content)
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
  if(!vm.isPersonal) Text("Verify organic certification on the selected retailer product.",fontSize=11.sp,color=ShopMuted)
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
                Text("Explore student offers", color = ShopInk, fontWeight = FontWeight.Medium)
                SmallNote("Illustrative offer scenario · adjusts grocery estimates.")
            }
            Switch(checked = vm.studentEligible, enabled = vm.canEditCheckout,
                onCheckedChange = vm::changeStudentEligibility,
                modifier = Modifier.semantics { contentDescription = "Apply illustrative student offers" })
        }
        SmallNote("These offers are examples, not verified retailer promotions. No student eligibility is checked.")
    }
}

@Composable
private fun StudentOfferLabel(store: Store, eligible: Boolean) {
    val rate = store.studentDiscountBps / 100
    SmallNote(if (rate == 0L) "No illustrative student offer"
        else "$rate% illustrative student offer" + if (eligible) " · applied" else " · enable offer scenario to apply")
}

@Composable
private fun StudentSavingsRow(quote: Quote) {
    if (quote.studentDiscount > 0) AmountRow("Illustrative student offer (${quote.store.studentDiscountBps / 100}%)", "−${money(quote.studentDiscount)}")
}
