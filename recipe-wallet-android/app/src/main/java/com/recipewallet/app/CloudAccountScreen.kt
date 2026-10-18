package com.recipewallet.app

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
internal fun CloudAccountScreen(vm: RecipeCloudViewModel, onBack: () -> Unit, backLabel: String = "← Saved recipes") {
    BackHandler(onBack = onBack)
    val ink = Color(0xFF263A2E)
    val muted = Color(0xFF656B63)
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords intentionally stay in memory only and are cleared after submission.
    var password by remember { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm.state.pendingEmail) {
        if (email.isBlank()) email = vm.state.pendingEmail.orEmpty()
    }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Scaffold(containerColor = Color(0xFFFAFAF7)) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text(backLabel, color = ink) }
                Text("Your recipe library", color = ink, fontSize = 30.sp, fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.6).sp)
                if (!vm.configured) {
                    Text("Cloud sync isn’t connected in this build yet. You can keep adding recipes on this device.", color = muted)
                } else if (vm.state.email != null) {
                    Text(vm.state.email.orEmpty(), color = ink, fontSize = 17.sp)
                    Text("Recipes in this account are private. Sign in on another device to use the same library.", color = muted)
                    ShopButton(if (vm.busy) "Syncing…" else "Sync recipes", enabled = !vm.busy, onClick = vm::refresh)
                    if (vm.state.importable > 0) {
                        Text("${vm.state.importable} recipes from this device can be copied into your account.", color = muted)
                        ShopButton("Import recipes from this device", outlined = true, enabled = !vm.busy, onClick = vm::importLocalRecipes)
                    }
                    if (vm.state.pending > 0) Text("${vm.state.pending} edits are waiting to sync. They’ll stay on this device if you sign out.", color = muted)
                    TextButton(onClick = { scope.launch { vm.signOut() } }, enabled = !vm.busy) { Text("Sign out", color = ink) }
                } else {
                    Text("Keep your recipes across devices. Your existing device recipes stay available without an account.", color = muted)
                    if (vm.state.pendingEmail != null) Text("Check your email. Open the confirmation link on this tablet, or sign in here after confirming it on another device.", color = muted, fontSize = 13.sp)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !vm.busy,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(),
                        enabled = !vm.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    ShopButton(if (vm.busy) "Please wait…" else if (creating) "Create account" else "Sign in",
                        enabled = !vm.busy && email.isNotBlank() && password.isNotEmpty(), onClick = {
                            focus.clearFocus(); keyboard?.hide()
                            val submitted = password; password = ""
                            scope.launch {
                                if (creating) { if (vm.signUp(email, submitted) == null) creating = false }
                                else vm.signIn(email, submitted)
                            }
                        })
                    TextButton(onClick = { creating = !creating }, enabled = !vm.busy) {
                        Text(if (creating) "Already have an account? Sign in" else "New here? Create an account", color = ink)
                    }
                    if (creating) Text("Use a strong password. Open the confirmation email on this tablet to return to the app.", color = muted, fontSize = 13.sp)
                }
                if (vm.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ink)
                vm.status?.let { Text(it, color = muted, fontSize = 13.sp, lineHeight = 19.sp) }
            }
        }
    }
}
