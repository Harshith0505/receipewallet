package com.recipewallet.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun RetailerScreen(ingredients: List<RetailerNeed>, onBack: () -> Unit, vm: RetailerViewModel = viewModel(),
    contextId: String = if (ingredients.isEmpty()) "browse" else "recipe") {
    LaunchedEffect(contextId, ingredients, vm.cartUncertain) { vm.configure(contextId, ingredients) }
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
            ShopButton("Edit products & quantities", enabled = !vm.editingBlocked, onClick = { vm.reviewing = false })
            ShopButton("Refresh retailer prices", outlined = true, enabled = !vm.editingBlocked, onClick = vm::refreshBasket)
            KrogerCartActions(vm)
        } else {
            PageHeading("Retailer prices", "Choose real products and whole packages for your grocery basket.")
            SmallNote("Your ZIP code and product searches are sent to Kroger. No location permission or card information is needed.")
            if (vm.selectedStore == null) {
                OutlinedTextField(value = vm.postalCode, onValueChange = { if (it.length <= 5) vm.postalCode = it },
                    label = { Text("US ZIP code") }, singleLine = true, enabled = !vm.editingBlocked, modifier = Modifier.fillMaxWidth())
                ShopButton("Find nearby stores", enabled = !vm.editingBlocked, onClick = vm::findStores)
                if (vm.searchedZip.isNotBlank()) {
                    SmallNote("Stores within 10 miles of ${vm.searchedZip}. Kroger-family stores only; no ratings are supplied.")
                    if (vm.stores.isEmpty()) SmallNote("No Kroger-family stores were returned near this ZIP code. Try another area.")
                }
                vm.stores.forEach { store ->
                    QuietCard {
                        Text(store.name, color = ShopInk, fontWeight = FontWeight.Medium)
                        SmallNote(store.address)
                        ShopButton("Check prices here", outlined = true, enabled = !vm.editingBlocked, onClick = { vm.chooseStore(store) })
                    }
                }
            }
            vm.selectedStore?.let { store ->
                QuietCard {
                    Text(store.name, color = ShopInk, fontWeight = FontWeight.Medium)
                    SmallNote(store.address)
                    ShopButton("Change store · clears live selections", outlined = true, enabled = !vm.editingBlocked, onClick = vm::changeStore)
                }
                if (vm.recipeMode) {
                    Text("Match your ingredients", color = ShopInk, fontWeight = FontWeight.Medium)
                    SmallNote("Pantry ingredients are excluded. Choose a product for each ingredient, then set enough packages yourself; package sizes are not automatically converted.")
                    if (vm.needs.isEmpty()) SmallNote("Your pantry covers the recipe. No grocery purchase is needed.")
                    vm.needs.forEach { ingredient ->
                        QuietCard(selected = vm.activeNeed == ingredient.id) {
                            Text(ingredient.name, color = ShopInk, fontWeight = FontWeight.Medium)
                            SmallNote(ingredient.amount)
                            SmallNote(vm.selections[ingredient.id]?.product?.name ?: "Not matched yet")
                            ShopButton("Search ${ingredient.name}", outlined = true, enabled = !vm.editingBlocked,
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
                                TextButton(onClick = { vm.setQuantity(id, selection.quantity - 1) }, enabled = !vm.editingBlocked && selection.quantity > 1,
                                    modifier = Modifier.semantics { contentDescription = "Fewer packs for ${selection.label}" }) { Text("−") }
                                Text("${selection.quantity} packages", color = ShopInk)
                                TextButton(onClick = { vm.setQuantity(id, selection.quantity + 1) }, enabled = !vm.editingBlocked && selection.quantity < 20,
                                    modifier = Modifier.semantics { contentDescription = "More packs for ${selection.label}" }) { Text("+") }
                                TextButton(onClick = { vm.remove(id) }, enabled = !vm.editingBlocked) { Text("Remove") }
                            }
                        }
                    }
                    if (!vm.canReview) SmallNote("Match every required ingredient. Choose at most 12 distinct product variants and at most 20 combined packages of each variant.")
                    ShopButton("Refresh & review live basket", enabled = !vm.editingBlocked && vm.canReview, onClick = vm::refreshBasket)
                }
                Text(vm.activeLabel?.let { "Choose a product for $it" } ?: "Find a product", color = ShopInk, fontWeight = FontWeight.Medium)
                OutlinedTextField(value = vm.query, onValueChange = { if (it.length <= 80) vm.query = it },
                    label = { Text("Product or ingredient") }, singleLine = true, enabled = !vm.editingBlocked, modifier = Modifier.fillMaxWidth())
                ShopButton("Search retailer products", enabled = !vm.editingBlocked && vm.query.trim().length >= 2, onClick = { vm.searchProducts() })
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
                            enabled = !vm.editingBlocked && product.selectable && (!vm.recipeMode || vm.activeNeed != null), onClick = { vm.selectProduct(product) })
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
        SmallNote("Retailer prices can change. This live basket is separate from the estimated basket used for Stripe test checkout. Adding to a retailer cart does not place an order or payment. Complete your purchase with the retailer.")
    }
}

internal fun retailerCartUrl(store: RetailerStore?): String = when {
    store?.name?.lowercase()?.let { "quality food center" in it || Regex("\\bqfc\\b").containsMatchIn(it) } == true -> "https://www.qfc.com/cart"
    store?.name?.lowercase()?.contains("fred meyer") == true -> "https://www.fredmeyer.com/cart"
    else -> "https://www.kroger.com/cart"
}

@Composable
internal fun KrogerCartActions(vm: RetailerViewModel, openBrowser: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var browserError by remember { mutableStateOf(false) }
    var confirmationQuote by remember { mutableStateOf<String?>(null) }
    val open: (String) -> Unit = { url ->
        browserError = false
        if (openBrowser != null) openBrowser(url) else try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) { browserError = true }
    }
    QuietCard {
        Text("Continue with Kroger", color = ShopInk, fontWeight = FontWeight.Medium)
        SmallNote("Sign in on Kroger's website to grant cart access. Signing in does not transfer products. Your Kroger password is not collected by Recipe Wallet; the backend keeps the short-lived connection in memory.")
        if (vm.cartConnection in listOf("disconnected", "expired", "failed") || vm.cartTransfer == "failed") {
            ShopButton("Connect Kroger account", enabled = !vm.editingBlocked, onClick = { vm.connectCart(open) })
        }
        if (vm.cartConnection == "pending") SmallNote("Finish Kroger sign-in in the browser, then return to this app. If the return link cannot open the app, come back manually and check the connection.")
        if (vm.cartConnection != "disconnected") {
            SmallNote("Connection: ${vm.cartConnection}")
            ShopButton("Check Kroger connection", outlined = true, enabled = !vm.busy, onClick = vm::checkCartConnection)
        }
        if (vm.cartConnection == "connected" && vm.cartTransfer != "added") {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("PICKUP" to "Pickup", "DELIVERY" to "Delivery").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().selectable(selected = vm.cartFulfillment == value, role = Role.RadioButton,
                        enabled = !vm.editingBlocked, onClick = { vm.changeCartFulfillment(value) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = vm.cartFulfillment == value, onClick = null)
                        Text(label, color = ShopInk)
                    }
                }
            }
            ShopButton("Review cart transfer", enabled = vm.canTransfer, onClick = { confirmationQuote = vm.basket?.quoteToken })
            if (vm.basket?.quoteToken == null) SmallNote("Refresh retailer prices before transferring this basket.")
        }
        if (vm.cartTransfer == "added") {
            Text("${vm.cartItemCount} packages sent to your Kroger-family cart", color = ShopInk, fontWeight = FontWeight.Medium)
            SmallNote("Kroger confirmed the cart transfer. No grocery order or payment was placed. Check the cart's existing products and quantities before checkout.")
        }
        if (vm.cartUncertain) {
            SmallNote("The transfer may already have changed your cart. Check the connection or open the retailer cart before another attempt. Basket edits and repeat transfers are paused.")
        }
        ShopButton("Open retailer cart", outlined = true, onClick = { open(retailerCartUrl(vm.selectedStore)) })
        SmallNote("At the retailer, confirm ${vm.selectedStore?.name.orEmpty()}, ${vm.cartFulfillment.lowercase()}, product quantities, final prices, taxes and fees. The Cart API does not set your shopping location. Other Kroger-family banners may require opening their own website.")
        if (vm.cartConnection != "disconnected") {
            if (vm.cartUncertain) {
                ShopButton("I checked my cart · disconnect", outlined = true, enabled = !vm.busy,
                    onClick = { vm.disconnectCart(checkedUncertainCart = true) })
            } else ShopButton("Disconnect Recipe Wallet", outlined = true, enabled = !vm.busy, onClick = { vm.disconnectCart() })
            SmallNote("Disconnecting forgets this app's connection. It does not log you out of the retailer's website or remove cart items.")
        }
        if (browserError) SmallNote("No browser is available. Install a browser or use a device with a browser to complete retailer sign-in.")
    }
    if (confirmationQuote != null) {
        AlertDialog(onDismissRequest = { confirmationQuote = null }, title = { Text("Add these packages to Kroger?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${vm.basket?.lines?.sumOf { it.quantity } ?: 0} packages · ${vm.cartFulfillment.lowercase()} · ${vm.selectedStore?.name.orEmpty()}")
                Text("This transfers the reviewed quantities to your existing retailer cart. Prices are checked again before transfer. Confirm the shopping location, final totals and payment on the retailer's website.")
                Text("No order is placed and no payment is taken by this action.")
            } }, confirmButton = { TextButton(enabled = vm.canTransfer, onClick = {
                val approved = confirmationQuote
                confirmationQuote = null
                if (vm.basket?.quoteToken == approved) vm.transferCart()
            }) { Text("Add packages to Kroger cart") } },
            dismissButton = { TextButton(onClick = { confirmationQuote = null }) { Text("Keep reviewing") } })
    }
}
