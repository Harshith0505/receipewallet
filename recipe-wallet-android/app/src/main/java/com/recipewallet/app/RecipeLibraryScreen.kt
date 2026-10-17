package com.recipewallet.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

private val LibraryInk = Color(0xFF263A2E)
private val LibraryMuted = Color(0xFF656B63)
private val LibraryLine = Color(0xFFE1E3DC)

@Composable
internal fun RecipeLibraryScreen(
    page: String,
    recipe: PersonalRecipe?,
    recipes: List<PersonalRecipe>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (PersonalRecipe) -> Unit,
    onEdit: () -> Unit,
    onSave: (PersonalRecipe) -> String?
) {
    BackHandler(onBack = onBack)
    Scaffold(containerColor = Color(0xFFFAFAF7)) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                    Text(if (page == "saved") "← Meal planner" else "← Saved recipes", color = LibraryInk)
                }
                when (page) {
                    "add" -> RecipeEditor(recipe, onSave)
                    "detail" -> if (recipe != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibraryTitle(recipe.name)
                            Text("${recipe.ingredients.size} ingredients" + (recipe.prepMinutes?.let { " · $it min" } ?: ""), color = LibraryMuted)
                        }
                        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp))
                            .border(1.dp, LibraryLine, RoundedCornerShape(14.dp)).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("Ingredients", color = LibraryInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            recipe.ingredients.forEach { Text(it, color = LibraryInk, fontSize = 16.sp) }
                        }
                        LibraryButton("Edit recipe", onClick = onEdit)
                        Text("Saved on this tablet. Shop and card comparisons are currently available for the demo recipes.",
                            color = LibraryMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LibraryTitle("Saved recipes")
                            Text(if (recipes.isEmpty()) "Keep the meals you come back to." else
                                "${recipes.size} ${if (recipes.size == 1) "recipe" else "recipes"} · Saved on this tablet",
                                color = LibraryMuted, fontSize = 15.sp)
                        }
                        LibraryButton("+ Add a recipe", onClick = onAdd)
                        if (recipes.isEmpty()) {
                            Column(Modifier.fillMaxWidth().border(1.dp, LibraryLine, RoundedCornerShape(14.dp)).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Your recipe book starts here", color = LibraryInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                Text("Add a name and your ingredients. No account needed.", color = LibraryMuted, fontSize = 14.sp, lineHeight = 21.sp)
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp))
                                .border(1.dp, LibraryLine, RoundedCornerShape(14.dp))) {
                                recipes.forEachIndexed { index, item ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = LibraryLine)
                                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { onOpen(item) }).padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(item.name, color = LibraryInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                            Text("${item.ingredients.size} ingredients" + (item.prepMinutes?.let { " · $it min" } ?: ""),
                                                color = LibraryMuted, fontSize = 12.sp)
                                        }
                                        Text("→", color = LibraryInk, fontSize = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RecipeEditor(recipe: PersonalRecipe?, onSave: (PersonalRecipe) -> String?) {
    var name by rememberSaveable(recipe?.id) { mutableStateOf(recipe?.name ?: "") }
    var ingredients by rememberSaveable(recipe?.id) { mutableStateOf(recipe?.ingredients?.joinToString("\n") ?: "") }
    var minutes by rememberSaveable(recipe?.id) { mutableStateOf(recipe?.prepMinutes?.toString() ?: "") }
    var error by rememberSaveable(recipe?.id) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LibraryTitle(if (recipe == null) "Add a recipe" else "Edit recipe")
        Text("Your recipe, written your way.", color = LibraryMuted, fontSize = 15.sp)
    }
    OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text("Recipe name") },
        placeholder = { Text("e.g. Mum’s mac and cheese") }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
    OutlinedTextField(minutes, { if (it.length <= 3 && it.all(Char::isDigit)) minutes = it },
        label = { Text("Prep time in minutes (optional)") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
    OutlinedTextField(ingredients, { if (it.length <= 8000) ingredients = it },
        label = { Text("Ingredients") },
        placeholder = { Text("200 g noodles\n2 garlic cloves\n1 tbsp soy sauce") },
        supportingText = { Text("One ingredient per line. Include quantities if you know them.") },
        minLines = 5, maxLines = 12, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
    error?.let { Text(it, color = Color(0xFF9A3A28), fontSize = 13.sp) }
    LibraryButton(if (recipe == null) "Save recipe" else "Save changes", enabled = name.isNotBlank() && ingredients.isNotBlank()) {
        val lines = ingredients.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        error = onSave(PersonalRecipe(recipe?.id ?: UUID.randomUUID().toString(), name.trim(), lines,
            minutes.toIntOrNull()))
    }
    Text("Saved only on this tablet. You can edit it later in Saved recipes.", color = LibraryMuted, fontSize = 12.sp)
}

@Composable
private fun LibraryTitle(text: String) {
    Text(text, color = LibraryInk, fontSize = 30.sp, lineHeight = 36.sp,
        letterSpacing = (-0.6).sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun LibraryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LibraryInk), shape = RoundedCornerShape(12.dp)) {
        Text(text, fontWeight = FontWeight.Medium)
    }
}
