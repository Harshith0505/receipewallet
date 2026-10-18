package com.recipewallet.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun RetailerScreen(ingredients: List<RetailerNeed>, onBack: () -> Unit, vm: RetailerViewModel = viewModel(),
    contextId: String = if (ingredients.isEmpty()) "browse" else "recipe") {
    LaunchedEffect(contextId, ingredients) { vm.configure(contextId, ingredients) }
    val back: () -> Unit = { if (vm.reviewing) vm.reviewing = false else onBack() }
    BackHandler(onBack = back)
    val context = LocalContext.current
    var directionsError by remember { mutableStateOf(false) }
    ShoppingPage(0, back) {
        if (vm.reviewing && vm.basket != null) {
            val basket = vm.basket!!
            PageHeading("Your live grocery basket", vm.selectedStore?.name.orEmpty())
            SmallNote("Kroger-listed regular prices · Refreshed ${basket.fetchedAt}")
            QuietCard {
                basket.lines.forEach { line ->
                    Text(line.name, color = ShopInk, fontWeight = FontWeight.Medium)
                    SmallNote("${line.quantity} × ${line.size} · ${money(line.price)} per package")
                    AmountRow("Line subtotal", money(line.subtotal))
                }
                AmountRow("Estimated item subtotal", money(basket.subtotal), prominent = true)
            }
            SmallNote("Taxes and fulfillment fees are not included. Exact stock quantities, promotions and rewards are not confirmed. This is not a final charge, reservation or grocery order.")
            SmallNote("Review that the packages cover your recipe. If the same product covers several ingredients, its package counts are added together; reduce duplicate counts before reviewing.")
            ShopButton("Edit products & quantities", enabled = !vm.busy, onClick = { vm.reviewing = false })
            ShopButton("Refresh retailer prices", outlined = true, enabled = !vm.busy, onClick = vm::refreshBasket)
        } else {
            PageHeading("Retailer prices", "Choose real products and whole packages for your grocery basket.")
            SmallNote("Your ZIP code and product searches are sent to Kroger. No location permission or card information is needed.")
            if (vm.selectedStore == null) {
                OutlinedTextField(value = vm.postalCode, onValueChange = { if (it.length <= 5) vm.postalCode = it },
                    label = { Text("US ZIP code") }, singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
                ShopButton("Find nearby stores", enabled = !vm.busy, onClick = vm::findStores)
                if (vm.searchedZip.isNotBlank()) {
                    SmallNote("Stores within 10 miles of ${vm.searchedZip}. Kroger-family stores only; no ratings are supplied.")
                    if (vm.stores.isEmpty()) SmallNote("No Kroger-family stores were returned near this ZIP code. Try another area.")
                }
                vm.stores.forEach { store ->
                    QuietCard {
                        Text(store.name, color = ShopInk, fontWeight = FontWeight.Medium)
                        SmallNote(store.address)
                        ShopButton("Check prices here", outlined = true, enabled = !vm.busy, onClick = { vm.chooseStore(store) })
                    }
                }
            }
            vm.selectedStore?.let { store ->
                QuietCard {
                    Text(store.name, color = ShopInk, fontWeight = FontWeight.Medium)
                    SmallNote(store.address)
                    ShopButton("Change store · clears live selections", outlined = true, enabled = !vm.busy, onClick = vm::changeStore)
                }
                if (vm.recipeMode) {
                    Text("Match your ingredients", color = ShopInk, fontWeight = FontWeight.Medium)
                    SmallNote("Pantry ingredients are excluded. Choose a product for each ingredient, then set enough packages yourself; package sizes are not automatically converted.")
                    if (ingredients.isEmpty()) SmallNote("Your pantry covers the recipe. No grocery purchase is needed.")
                    ingredients.forEach { ingredient ->
                        QuietCard(selected = vm.activeNeed == ingredient.id) {
                            Text(ingredient.name, color = ShopInk, fontWeight = FontWeight.Medium)
                            SmallNote(ingredient.amount)
                            SmallNote(vm.selections[ingredient.id]?.product?.name ?: "Not matched yet")
                            ShopButton("Search ${ingredient.name}", outlined = true, enabled = !vm.busy,
                                onClick = { vm.searchProducts(ingredient.name.take(80), ingredient.id) })
                        }
                    }
                }
                if (vm.selections.isNotEmpty()) {
                    Text("Selected packages", color = ShopInk, fontWeight = FontWeight.Medium)
                    vm.selections.forEach { (id, selection) ->
                        QuietCard {
                            Text(selection.label, color = ShopInk, fontWeight = FontWeight.Medium)
                            SmallNote("${selection.product.name} · ${selection.product.size}")
                            SmallNote("Listed regular price: ${money(selection.product.price!!)} per package")
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { vm.setQuantity(id, selection.quantity - 1) }, enabled = !vm.busy && selection.quantity > 1,
                                    modifier = Modifier.semantics { contentDescription = "Fewer packs for ${selection.label}" }) { Text("−") }
                                Text("${selection.quantity} packages", color = ShopInk)
                                TextButton(onClick = { vm.setQuantity(id, selection.quantity + 1) }, enabled = !vm.busy && selection.quantity < 20,
                                    modifier = Modifier.semantics { contentDescription = "More packs for ${selection.label}" }) { Text("+") }
                                TextButton(onClick = { vm.remove(id) }, enabled = !vm.busy) { Text("Remove") }
                            }
                        }
                    }
                    if (!vm.canReview) SmallNote("Match every required ingredient. Choose at most 12 distinct product variants and at most 20 combined packages of each variant.")
                    ShopButton("Refresh & review live basket", enabled = !vm.busy && vm.canReview, onClick = vm::refreshBasket)
                }
                Text(vm.activeLabel?.let { "Choose a product for $it" } ?: "Find a product", color = ShopInk, fontWeight = FontWeight.Medium)
                OutlinedTextField(value = vm.query, onValueChange = { if (it.length <= 80) vm.query = it },
                    label = { Text("Product or ingredient") }, singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
                ShopButton("Search retailer products", enabled = !vm.busy && vm.query.trim().length >= 2, onClick = { vm.searchProducts() })
                if (vm.searchedQuery.isNotBlank()) {
                    SmallNote("Results for “${vm.searchedQuery}” · Kroger API · Checked ${vm.fetchedAt}")
                    if (vm.products.isEmpty()) SmallNote("No matching products were returned. Try another search; this does not prove the store is out of stock.")
                }
                vm.products.forEach { product ->
                    QuietCard {
                        Text(product.name, color = ShopInk, fontWeight = FontWeight.Medium)
                        SmallNote(product.size ?: "Package size not provided")
                        AmountRow("Retailer-listed regular price", product.price?.let(::money) ?: "Not provided", prominent = product.price != null)
                        product.promo?.let { AmountRow("Listed promotional price", money(it)); SmallNote("Promotions are excluded from the subtotal because eligibility is not confirmed.") }
                        SmallNote("Stock: ${product.stock ?: "not provided"}${product.soldBy?.let { " · Sold by: $it" } ?: " · Price basis not provided"}")
                        ShopButton(vm.activeLabel?.let { "Use for $it" } ?: "Add product", outlined = true,
                            enabled = !vm.busy && product.selectable && (!vm.recipeMode || vm.activeNeed != null), onClick = { vm.selectProduct(product) })
                        if (!product.selectable) SmallNote("Choose a product with a listed unit price, package size and no reported out-of-stock status. Items sold by weight need a separate quantity calculation.")
                    }
                }
            }
        }
        vm.selectedStore?.let { store ->
            ShopButton("Directions to this store", outlined = true, onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(store.address)))
                directionsError = false
                try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { directionsError = true }
            })
            if (directionsError) SmallNote("No maps app or browser is available. Use the store address above.")
        }
        if (vm.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); SmallNote("Contacting Kroger…") }
        vm.error?.let { QuietCard { Text(it, color = ShopInk) } }
        SmallNote("Retailer prices can change. This live basket is separate from the sample basket used for Stripe test checkout. No real payment or grocery order is placed here.")
    }
}
