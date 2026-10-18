package com.recipewallet.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PInk = Color(0xFF213E30)
private val PMuted = Color(0xFF677167)
private val PLime = Color(0xFFD8E9A1)

/** "Cook with what I have": pick owned ingredients, see ranked recipes, jump into the basket for what's missing. */
@Composable
fun PantryPanel(vm: WalletViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("What do you already have?", color = PInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    if (vm.pantry.isEmpty()) "Tick your ingredients and get recipe ideas"
                    else "${vm.pantry.size} ingredient${if (vm.pantry.size == 1) "" else "s"} saved",
                    color = PMuted, fontSize = 12.sp
                )
            }
            Text(if (expanded) "Hide" else "Open", color = PInk, fontWeight = FontWeight.Bold)
        }
        if (expanded) {
            if (vm.recentItems.isNotEmpty() && !vm.recentItems.all { it.id in vm.pantry }) {
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFFE9EDDC), RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("From your past orders you probably still have: " + vm.recentItems.joinToString { it.name.lowercase() } + ".", color = PInk, fontSize = 13.sp)
                    TextButton(onClick = { vm.addRecentToPantry() }) { Text("Add them to my pantry") }
                }
            }
            vm.allIngredients.forEach { ing ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.togglePantry(ing.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = ing.id in vm.pantry, onCheckedChange = { vm.togglePantry(ing.id) })
                    Text(ing.name, color = PInk, fontSize = 14.sp)
                }
            }
            Button(
                onClick = { vm.suggestFromPantry() },
                enabled = !vm.pantryBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (vm.pantryBusy) "Finding recipes…" else "Suggest recipes · AI", fontWeight = FontWeight.Bold) }
            if (vm.pantryBusy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            vm.pantryError?.let { Text(it, color = Color(0xFF9A3A28), fontSize = 13.sp) }
            if (vm.pantryReply.isNotBlank()) Text(vm.pantryReply, color = PInk)
            vm.pantrySuggestions.forEach { s ->
                val recipe = vm.catalog.recipes.first { it.id == s.recipeId }
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFFF7F6EF), RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("${recipe.emoji}  ${recipe.name}", color = PInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(s.summary, color = PMuted, fontSize = 12.sp)
                    if (s.canCookNow) {
                        Text("READY TO COOK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PInk,
                            modifier = Modifier.background(PLime, RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 6.dp))
                    } else {
                        TextButton(onClick = { vm.cookFromPantry(s) }) { Text("Shop the missing ingredients  →") }
                    }
                }
            }
        }
    }
}
