package com.recipewallet.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HomeInk = Color(0xFF263A2E)
private val HomeMuted = Color(0xFF656B63)
private val HomePaper = Color(0xFFFAFAF7)
private val HomeLine = Color(0xFFE1E3DC)

@Composable
internal fun HomeScreen(vm: WalletViewModel) {
    var manual by rememberSaveable { mutableStateOf(false) }
    var libraryPage by rememberSaveable { mutableStateOf(if (vm.isPersonal) "saved" else "home") }
    var selectedRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    val library: RecipeCloudViewModel = viewModel()
    val personalRecipes = library.state.recipes
    var about by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissKeyboard: () -> Unit = { focusManager.clearFocus(force = true); keyboard?.hide() }

    LaunchedEffect(library.confirmationRequests) {
        if (library.confirmationRequests > 0) { libraryPage = "account"; library.consumeConfirmationRequest() }
    }
    LaunchedEffect(manual, libraryPage) {
        if (manual || libraryPage != "home") {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    if (libraryPage == "pantry") {
        PantryScreen(onBack = { libraryPage = "home" })
        return
    }
    if (libraryPage == "account") {
        CloudAccountScreen(library, onBack = { libraryPage = "saved" })
        return
    }
    if (libraryPage != "home") {
        RecipeLibraryScreen(
            page = libraryPage,
            recipe = personalRecipes.firstOrNull { it.id == selectedRecipeId },
            recipes = personalRecipes,
            library = library,
            onAccount = { libraryPage = "account" },
            onBack = { dismissKeyboard(); libraryPage = if (libraryPage == "saved") "home" else "saved" },
            onAdd = { selectedRecipeId = null; libraryPage = "add" },
            onOpen = { selectedRecipeId = it.id; libraryPage = "detail" },
            onEdit = { libraryPage = "add" },
            onUse = { dismissKeyboard(); vm.choosePersonal(it) },
            onSave = { recipe ->
                val error = library.save(recipe)
                if (error == null) withContext(Dispatchers.Main.immediate) { dismissKeyboard(); libraryPage = "saved" }
                error
            }
        )
        return
    }

    Scaffold(containerColor = HomePaper) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("recipe wallet", color = HomeInk, fontSize = 22.sp,
                        letterSpacing = (-0.6).sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { dismissKeyboard(); library.refresh(); libraryPage = "saved" }, enabled = !vm.aiBusy) {
                            Text("Saved recipes", color = HomeInk, fontSize = 13.sp)
                        }
                        Box {
                            IconButton(onClick = { dismissKeyboard(); menuOpen = true },
                                modifier = Modifier.semantics { contentDescription = "More options" }) {
                                Canvas(Modifier.size(24.dp)) {
                                    listOf(-6f, 0f, 6f).forEach { offset ->
                                        drawCircle(HomeInk, radius = 1.6.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2 + offset.dp.toPx()))
                                    }
                                }
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("About this demo") }, onClick = { menuOpen = false; about = true })
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What’s for dinner?", color = HomeInk, fontSize = 32.sp,
                        lineHeight = 38.sp, letterSpacing = (-0.8).sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
                    Text("Plan a meal, compare shops, and choose your Visa card.",
                        color = HomeMuted, fontSize = 15.sp, lineHeight = 22.sp)
                }
                Row(Modifier.fillMaxWidth().border(1.dp, HomeLine, RoundedCornerShape(14.dp))
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .clickable(enabled = !vm.aiBusy, role = Role.Button,
                        onClick = { dismissKeyboard(); libraryPage = "pantry" }).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FridgeMark()
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("My pantry", color = HomeInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Keep track of what’s already at home.", color = HomeMuted, fontSize = 12.sp)
                    }
                    Text("→", color = HomeInk, fontSize = 20.sp)
                }
                Row(
                    Modifier.fillMaxWidth().selectableGroup()
                        .background(Color(0xFFEEEFE9), RoundedCornerShape(12.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Build with AI", "Build manually").forEachIndexed { index, label ->
                        val selected = manual == (index == 1)
                        Box(
                            Modifier.weight(1f).heightIn(min = 48.dp)
                                .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(9.dp))
                                .selectable(selected = selected, enabled = !vm.aiBusy, role = Role.Tab,
                                    onClick = { dismissKeyboard(); manual = index == 1 }),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 14.sp, color = if (selected) HomeInk else HomeMuted,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                if (!manual) {
                    MealComposer(vm, onManual = { dismissKeyboard(); manual = true },
                        onSubmit = { dismissKeyboard(); vm.suggest() })
                    if (vm.agentReply.isNotBlank()) AgentPlanReview(vm)
                }
                if (manual || vm.agentReply.isBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (manual) "Choose a recipe" else "Or start with a recipe",
                                color = HomeInk, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.semantics { heading() })
                            Text(if (manual) "Edit the ingredients before comparing shops." else "Pick one to build your basket yourself.",
                                color = HomeMuted, fontSize = 13.sp)
                        }
                        Column(Modifier.fillMaxWidth().border(1.dp, HomeLine, RoundedCornerShape(14.dp))
                            .background(Color.White, RoundedCornerShape(14.dp))) {
                            vm.catalog.recipes.forEachIndexed { index, recipe ->
                                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = HomeLine)
                                Row(
                                    Modifier.fillMaxWidth().clickable(enabled = !vm.aiBusy, role = Role.Button,
                                        onClick = { dismissKeyboard(); vm.choose(recipe.id) }).padding(horizontal = 18.dp, vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text("0${index + 1}", color = HomeMuted, fontSize = 12.sp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(recipe.name, color = HomeInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Text("${recipe.prepTime} min  ·  ${recipe.ingredients.size} ingredients",
                                            color = HomeMuted, fontSize = 12.sp)
                                    }
                                    Text("→", color = HomeInk, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
                if (manual) {
                    OutlinedButton(onClick = { dismissKeyboard(); selectedRecipeId = null; libraryPage = "add" },
                        enabled = !vm.aiBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(12.dp)) {
                        Text("+ Add a recipe", color = HomeInk)
                    }
                }
                Text("Demo catalog · ${vm.catalog.recipes.size} recipes · Sample prices and rewards",
                    color = HomeMuted, fontSize = 11.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 12.dp))
            }
        }
    }
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            shape = RoundedCornerShape(16.dp),
            title = { Text("About Recipe Wallet") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Recipes use sample package prices, shops and Visa rewards. Demo tax is configured at 0%. Saved recipes use the packages you select.")
                    Text("Meal requests and recent preferences go to Gemini. Card details do not. Planning sessions expire after 20 minutes.")
                    Text("Checkout uses Stripe test mode. No real grocery order, delivery, or rewards result.")
                }
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("Got it") } }
        )
    }
}

@Composable
private fun MealComposer(vm: WalletViewModel, onManual: () -> Unit, onSubmit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (vm.agentReply.isBlank()) "Describe your meal" else "Adjust your meal",
            color = HomeInk, fontSize = 18.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() })
        OutlinedTextField(
            value = vm.preference,
            onValueChange = { if (it.length <= 600) vm.preference = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !vm.aiBusy,
            label = { Text(if (vm.agentReply.isBlank()) "Meal, budget, and pantry items" else "What would you like to change?") },
            placeholder = { Text(if (vm.agentReply.isBlank()) "Vegetarian noodles under $30.\nI already have garlic." else "I already have soy sauce too.") },
            minLines = 3, maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
                unfocusedBorderColor = Color(0xFFB9BFB5), focusedBorderColor = HomeInk)
        )
        if (vm.agentReply.isBlank() && !vm.aiBusy) {
            TextButton(
                onClick = { vm.preference = "Find me a vegetarian noodle dinner under $30. I already have garlic." },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
            ) { Text("Try: vegetarian noodles under $30", color = HomeInk, fontSize = 13.sp) }
        }
        Button(
            onClick = onSubmit, enabled = !vm.aiBusy && vm.preference.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HomeInk)
        ) {
            if (vm.aiBusy) {
                CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (vm.aiBusy) "Building your meal…" else if (vm.agentReply.isBlank()) "Build my meal" else "Update my meal",
                fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        if (vm.aiBusy) Text("Finding recipes and comparing shops.", color = HomeMuted, fontSize = 12.sp)
        vm.aiError?.let { error ->
            Column(Modifier.fillMaxWidth().background(Color(0xFFF7EEE8), RoundedCornerShape(12.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Couldn’t build your meal", color = Color(0xFF7B3A29), fontWeight = FontWeight.Medium)
                Text(error, color = Color(0xFF7B3A29), fontSize = 13.sp)
                TextButton(onClick = onManual, contentPadding = PaddingValues(0.dp)) { Text("Choose a recipe instead") }
            }
        }
        Text("Meal requests are processed by Gemini. You review the basket before checkout.",
            color = HomeMuted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}
