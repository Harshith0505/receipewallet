package com.recipewallet.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun LiveShoppingScreen(contextId:String, title:String, needs:List<RetailerNeed>, cards:List<RewardCard>, budget:Long,
 onBack:()->Unit, onIllustrativeComparison:()->Unit, vm:RetailerViewModel,meal:JournalMeal?=null,onSampleCheckout:((String,Long)->Unit)?=null) {
 LaunchedEffect(contextId,needs){vm.startShopping(contextId,needs)}
 val back:()->Unit={if(vm.reviewing)vm.reviewing=false else onBack()}
 BackHandler { if(!vm.cartUncertain)back() }
 var changingStore by remember{mutableStateOf(false)}
 var expandedNeed by remember{mutableStateOf<String?>(null)}
 var search by remember{mutableStateOf("")}
 val scrollScope=rememberCoroutineScope()
 val ingredientAnchors=remember(needs){needs.associate{it.id to BringIntoViewRequester()}}
 fun chooseMissing(need:RetailerNeed){
  expandedNeed=need.id;search=need.name
  scrollScope.launch{withFrameNanos{};ingredientAnchors[need.id]?.bringIntoView()}
 }
 ShoppingPage(2,back,backEnabled=!vm.cartUncertain){
  PageHeading(if(vm.reviewing)"Review your grocery basket"else "Your shopping basket",title)
  if(needs.isEmpty()){
   Signal("Your pantry covers this meal")
   SmallNote("No grocery purchase is needed. Check that you have enough of each ingredient.")
  }else{
   if(meal!=null)InfoDisclosure("Your recipe","Ingredients and cooking steps stay with your basket"){RecipeFoods(meal);RecipeMethod(meal.steps)}
   SmallNote("Current Kroger-family products · Suggestions need your review · No order placed")
   if(vm.busy){LinearProgressIndicator(Modifier.fillMaxWidth());SmallNote("Finding products for your ingredients… ${vm.candidatesByNeed.size+vm.matchErrors.size} of ${needs.size}")}
   vm.error?.let{Signal(it,Danger)}
   if(!vm.reviewing){
    QuietCard{
     Eyebrow("Shopping near ${vm.searchedZip.ifBlank{vm.postalCode}}")
     Text(vm.selectedStore?.name?:"Finding a nearby store",color=ShopInk,fontWeight=FontWeight.Bold)
     vm.selectedStore?.let{SmallNote(it.address)}
     SmallNote("The first returned nearby store is selected. Choose a different store or ZIP below.")
     TextButton(enabled=!vm.editingBlocked,onClick={changingStore=!changingStore}){Text("Change store or ZIP")}
     if(changingStore||vm.error!=null&&vm.selectedStore==null){
      OutlinedTextField(value=vm.postalCode,onValueChange={if(it.length<=5)vm.postalCode=it},label={Text("US ZIP code")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!vm.editingBlocked)
      ShopButton("Update nearby stores",enabled=!vm.editingBlocked,onClick={vm.loadShopping(null)})
      vm.stores.forEach{store->
       TextButton(enabled=!vm.editingBlocked,onClick={changingStore=false;vm.loadShopping(store)}){Text(store.name+(if(store.id==vm.selectedStore?.id)" · selected"else ""))}
      }
     }
    }
    if(vm.stores.isNotEmpty())RetailerMap(vm.stores,vm.selectedStore?.id.orEmpty(),!vm.editingBlocked){id->
     vm.stores.firstOrNull{it.id==id&&it.id!=vm.selectedStore?.id}?.let{vm.loadShopping(it)}
    }
    if(vm.selections.values.groupBy{it.product.key}.any{it.value.size>1})SmallNote("Some ingredients use the same product. Their package counts are combined; check for duplicates before continuing.")
    if(vm.selections.isNotEmpty()) AmountRow("Selected item estimate",money(vm.selections.values.sumOf{it.product.price!!*it.quantity}),prominent=true)
    SmallNote("Pantry items are excluded. Suggested products are not approved yet: select alternatives below and check package quantities. Your ZIP and ingredient searches are sent to Kroger.")
    needs.forEach{need->
     val selection=vm.selections[need.id]
     val matches=vm.candidatesByNeed[need.id].orEmpty()
     QuietCard{
      Text(need.name,color=ShopInk,fontWeight=FontWeight.Bold,modifier=Modifier.bringIntoViewRequester(ingredientAnchors.getValue(need.id)))
      SmallNote(need.amount)
      if(selection!=null){
       Text(selection.product.name,color=ShopInk)
       SmallNote("${selection.product.size} · ${money(selection.product.price!!)} each")
       if(suggestedPackageCount(need,selection.product)==null) SmallNote("Package count needs your review; these units cannot be safely converted.")
       Row(verticalAlignment=Alignment.CenterVertically){
        TextButton(enabled=!vm.editingBlocked&&selection.quantity>1,onClick={vm.setQuantity(need.id,selection.quantity-1)},modifier=Modifier.semantics{contentDescription="Fewer packs for ${need.name}"}){Text("−")}
        Text("${selection.quantity} × ${selection.product.size?:"item"}",color=ShopInk)
        TextButton(enabled=!vm.editingBlocked&&selection.quantity<20,onClick={vm.setQuantity(need.id,selection.quantity+1)},modifier=Modifier.semantics{contentDescription="More packs for ${need.name}"}){Text("+")}
        Text(money(selection.product.price*selection.quantity),color=ShopInk)
       }
      }else SmallNote(if(vm.busy)"Looking for products…"else "Choose a product to match this ingredient.")
      vm.matchErrors[need.id]?.let{SmallNote(it)}
      TextButton(enabled=!vm.editingBlocked&&vm.selectedStore!=null,onClick={expandedNeed=if(expandedNeed==need.id)null else need.id;search=need.name}){Text(if(expandedNeed==need.id)"Hide alternatives"else if(selection==null)"Choose product"else "Change product")}
      if(expandedNeed==need.id){
       if(matches.isEmpty())SmallNote("No eligible priced items found. Try a more specific search.")
       matches.forEach{product->
        Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
         RadioButton(modifier=Modifier.semantics{contentDescription="Select ${product.name} for ${need.name}"},selected=selection?.product?.key==product.key,enabled=!vm.editingBlocked,onClick={vm.selectForNeed(need.id,product)})
         Column(Modifier.weight(1f)){Text(product.name,color=ShopInk);SmallNote("${product.size} · ${money(product.price!!)}")}
        }
       }
       OutlinedTextField(value=search,onValueChange={if(it.length<=80)search=it},label={Text("Search alternatives for ${need.name}")},modifier=Modifier.fillMaxWidth(),enabled=!vm.editingBlocked)
       ShopButton("Search alternatives",outlined=true,enabled=!vm.editingBlocked&&search.trim().length>=2,onClick={vm.searchProducts(search,need.id)})
      }
     }
    }
    if(vm.selectedStore!=null){
     if(!vm.canReview&&!vm.busy)QuietCard{
      Text(if(vm.unmatchedNeeds.isNotEmpty())"Finish matching your ingredients"else "Review your shopping quantities",fontWeight=FontWeight.Bold,color=ShopInk)
      SmallNote(vm.reviewBlockReason?:"Review each ingredient before continuing.")
      vm.unmatchedNeeds.forEach{need->ShopButton("Choose product for ${need.name}",outlined=true,enabled=!vm.editingBlocked,onClick={chooseMissing(need)})}
     }
     val reviewEnabled=!vm.editingBlocked&&vm.canReview
     Row(Modifier.fillMaxWidth().semantics{contentDescription="Review item sizes and quantities"}.toggleable(value=vm.packagesReviewed,enabled=reviewEnabled,role=Role.Checkbox,onValueChange=vm::confirmPackages),verticalAlignment=Alignment.CenterVertically){
      Checkbox(checked=vm.packagesReviewed,onCheckedChange=null,enabled=reviewEnabled)
      Text("I reviewed the products, sizes, and quantities",modifier=Modifier.weight(1f),color=if(reviewEnabled)ShopInk else ShopMuted)
     }
     if(vm.busy)SmallNote("Wait for product matching to finish before confirming your review.")
     SmallNote("One item means one bottle, bag, box, or bunch of the displayed size—not one serving. Check that the quantities cover your recipe.")
     ShopButton("Review live basket →",enabled=!vm.editingBlocked&&vm.canReview&&vm.packagesReviewed,onClick=vm::refreshBasket)
     SmallNote("Prices are checked again before review. Taxes and fulfillment fees are added by the retailer; rewards do not reduce today's charge.")
    }
    if(vm.error!=null||vm.matchErrors.isNotEmpty())ShopButton("Retry product matching",outlined=true,enabled=!vm.editingBlocked,onClick={vm.loadShopping()})
    InfoDisclosure("Illustrative comparison","Explore estimated store offers and Stripe test checkout"){
     SmallNote("This uses separate illustrative product prices. It does not pay for this retailer basket.")
     ShopButton("Use illustrative store comparison",outlined=true,enabled=!vm.cartUncertain,onClick=onIllustrativeComparison)
    }
   }else{
    vm.basket?.let{basket->
     QuietCard{
      Text(vm.selectedStore?.name.orEmpty(),color=ShopInk,fontWeight=FontWeight.Bold)
      basket.lines.forEach{line->AmountRow("${line.name} · ${line.quantity} × ${line.size}",money(line.subtotal))}
      AmountRow("Retailer item subtotal",money(basket.subtotal),prominent=true)
      SmallNote("Refreshed ${basket.fetchedAt} · Taxes and fulfillment fees not included")
      if(budget>=0)Signal(if(basket.subtotal>budget)"Items alone are ${money(basket.subtotal-budget)} over your budget"else "${money(budget-basket.subtotal)} remains for taxes and fees",if(basket.subtotal>budget)Danger else Good)
     }
     if(onSampleCheckout!=null)QuietCard{
      Text("Ready to keep cooking?",fontWeight=FontWeight.Bold,color=ShopInk)
      SmallNote("Try sample checkout, then find this meal and its cooking steps in My recipes. No payment or grocery order is placed.")
      ShopButton("Continue to sample checkout",enabled=!vm.editingBlocked,onClick={onSampleCheckout(vm.selectedStore?.name.orEmpty(),basket.subtotal)})
     }
     if(cards.isNotEmpty()){
      val best=cards.maxBy{it.rewards(basket.subtotal)}
      var cardId by remember(basket.quoteToken){mutableStateOf(best.id)}
      QuietCard{
       Text("Compare estimated Visa rewards",fontWeight=FontWeight.Bold,color=ShopInk)
       cards.forEach{card->
        Row(verticalAlignment=Alignment.CenterVertically){
         RadioButton(selected=cardId==card.id,onClick={cardId=card.id})
         Column(Modifier.weight(1f)){Text(card.name,color=ShopInk);SmallNote(card.label)}
         Text(money(card.rewards(basket.subtotal)),color=ShopInk)
        }
        if(cardId==card.id){SmallNote(card.notes);if(card.id==best.id)Signal("Highest estimated reward value")}
       }
       SmallNote("Illustrative card profiles. Actual category eligibility and rewards are not confirmed. Pay with your own eligible card at the retailer; these estimates do not reduce the checkout total.")
      }
     }
     ShopButton("Edit products & quantities",enabled=!vm.editingBlocked,onClick={vm.reviewing=false})
     ShopButton("Refresh retailer prices",outlined=true,enabled=!vm.editingBlocked,onClick=vm::refreshBasket)
     KrogerCartActions(vm)
    }
   }
  }
 }
}
