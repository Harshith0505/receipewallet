package com.recipewallet.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun RetailerScreen(ingredients: List<String>, onBack: () -> Unit, vm: RetailerViewModel = viewModel()) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var directionsError by remember { mutableStateOf(false) }
    ShoppingPage(0, onBack) {
        PageHeading("Retailer prices", "Find a Kroger-family store and check its listed product prices.")
        SmallNote("Your ZIP code and product searches are sent to Kroger. No location permission or card information is needed.")
        OutlinedTextField(value = vm.postalCode, onValueChange = { if (it.length <= 5) vm.postalCode = it },
            label = { Text("US ZIP code") }, singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
        ShopButton("Find nearby stores", enabled = !vm.busy, onClick = vm::findStores)
        if (vm.searchedZip.isNotBlank()) {
            SmallNote("Stores within 10 miles of ${vm.searchedZip}. Results cover Kroger-family stores only; no ratings are supplied.")
            if (vm.stores.isEmpty()) SmallNote("No Kroger-family stores were returned near this ZIP code. Try another area.")
        }
        vm.stores.forEach { store ->
            QuietCard(selected = vm.selectedStore?.id == store.id) {
                Text(store.name, color = ShopInk, fontWeight = FontWeight.Medium)
                SmallNote(store.address)
                ShopButton(if (vm.selectedStore?.id == store.id) "Selected store" else "Check prices here",
                    outlined = true, enabled = !vm.busy, onClick = { vm.chooseStore(store) })
            }
        }
        vm.selectedStore?.let { store ->
            Text("Product prices at ${store.name}", color = ShopInk, fontWeight = FontWeight.Medium)
            if (ingredients.isNotEmpty()) {
                SmallNote("Look up ingredients you need. Check each product and package size yourself.")
                ingredients.forEach { ingredient ->
                    TextButton(enabled = !vm.busy, onClick = { vm.searchProducts(ingredient) }) { Text("Search $ingredient") }
                }
            }
            OutlinedTextField(value = vm.query, onValueChange = { if (it.length <= 80) vm.query = it },
                label = { Text("Product or ingredient") }, singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
            ShopButton("Search retailer products", enabled = !vm.busy && vm.query.trim().length >= 2,
                onClick = { vm.searchProducts() })
            if (vm.searchedQuery.isNotBlank()) {
                SmallNote("Results for “${vm.searchedQuery}” · Kroger API · Checked ${vm.fetchedAt}")
                if (vm.products.isEmpty()) SmallNote("No matching products were returned. Try a different search; this does not prove the store is out of stock.")
            }
            vm.products.forEach { product ->
                QuietCard {
                    Text(product.name, color = ShopInk, fontWeight = FontWeight.Medium)
                    SmallNote(product.size ?: "Package size not provided")
                    AmountRow("Retailer-listed regular price", product.price?.let(::money) ?: "Not provided", prominent = product.price != null)
                    product.promo?.let { AmountRow("Listed promotional price", money(it)); SmallNote("Promotion eligibility and conditions must be checked with the retailer.") }
                    SmallNote("Stock: ${product.stock ?: "not provided"}${product.soldBy?.let { " · Sold by: $it" } ?: " · Price basis not provided"}")
                }
            }
            ShopButton("Directions to this store", outlined = true, onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(store.address)))
                directionsError = false
                try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { directionsError = true }
            })
            if (directionsError) SmallNote("No maps app or browser is available. Use the store address above.")
        }
        if (vm.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); SmallNote("Contacting Kroger…") }
        vm.error?.let { QuietCard { Text(it, color = ShopInk) } }
        SmallNote("Retailer prices can change. Taxes, fees, stock quantities and rewards are not confirmed here. Prices shown per retailer listing may be per item or by weight. This lookup does not place an order or change the sample basket used for Stripe test checkout.")
    }
}
