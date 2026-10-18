package com.recipewallet.app

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
internal fun PantryScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val store = remember { PantryStore(context) }
    val initial = remember { runCatching { store.read() } }
    var items by remember { mutableStateOf(initial.getOrDefault(emptyList())) }
    var error by remember { mutableStateOf(if (initial.isFailure) "Couldn’t open your pantry. Please reopen this page." else null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewPath by rememberSaveable { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val existing = previewPath
        if (existing != null) photo = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(existing) }
    }
    fun usePhoto(load: () -> Bitmap) {
        scope.launch {
            loading = true; error = null
            val result = runCatching { withContext(Dispatchers.IO) {
                val bitmap = load()
                bitmap to PantryPhoto.save(context, bitmap)
            } }
            result.onSuccess { (bitmap, path) ->
                PantryPhoto.remove(context, previewPath)
                photo = bitmap; previewPath = path
            }.onFailure { error = "Couldn’t open this photo. Please try another." }
            loading = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { result ->
        if (result != null) usePhoto { result }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) usePhoto { PantryPhoto.read(context, uri) }
    }
    fun save(values: List<PantryItem>): Boolean = runCatching { store.write(values) }
        .onSuccess { items = values; error = null }
        .onFailure { error = it.message ?: "Couldn’t save your pantry. Please try again." }.isSuccess

    ShoppingPage(step = 0, onBack = onBack) {
        PageHeading("My pantry", "Keep the ingredients you already have in one place.")
        QuietCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FridgeMark()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (photo == null) "Start with a photo" else "Your fridge photo", color = ShopInk,
                        fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    SmallNote("A clear photo of your fridge, shelves or cupboard.")
                }
            }
            val preview = photo
            if (preview != null) {
                Image(preview.asImageBitmap(), contentDescription = "Selected pantry photo",
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 260.dp)
                        .clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Photo ready", color = ShopInk, fontSize = 13.sp)
                    TextButton(onClick = { PantryPhoto.remove(context, previewPath); previewPath = null; photo = null }) {
                        Text("Remove photo", color = ShopMuted)
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ShopInk)
            ShopButton(if (photo == null) "Take a photo" else "Retake photo", enabled = !loading) {
                try { camera.launch(null) }
                catch (_: ActivityNotFoundException) { error = "No camera app is available. Choose a photo instead." }
                catch (_: SecurityException) { error = "Camera access is unavailable. Choose a photo instead." }
            }
            ShopButton(if (photo == null) "Choose a photo" else "Choose another photo", outlined = true, enabled = !loading) {
                try { picker.launch("image/*") }
                catch (_: ActivityNotFoundException) { error = "No photo picker is available on this device." }
            }
            SmallNote("Photo preview only for now. Ingredient recognition comes next.")
        }
        error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFF9A3A28), fontSize = 13.sp) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Your ingredients", color = ShopInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    SmallNote(if (items.isEmpty()) "Nothing added yet" else "${items.size} ${if (items.size == 1) "ingredient" else "ingredients"}")
                }
                TextButton(enabled = initial.isSuccess, onClick = { editingId = null; editorOpen = true }) {
                    Text("+ Add manually", color = ShopInk)
                }
            }
            QuietCard {
                if (items.isEmpty()) {
                    Text("What’s in your kitchen?", color = ShopInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    SmallNote("Add an ingredient and how much you have. You can update it anytime.")
                } else items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = ShopLine)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable(role = Role.Button,
                            onClick = { editingId = item.id; editorOpen = true }).padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, color = ShopInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            SmallNote(item.amount.ifBlank { "Amount not set" })
                        }
                        TextButton(onClick = { save(items.filterNot { it.id == item.id }) },
                            modifier = Modifier.semantics { contentDescription = "Remove ${item.name}" }) {
                            Text("Remove", color = ShopMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
            SmallNote("Saved on this device. Recipe matching and cloud sync come next.")
        }
    }
    if (editorOpen) {
        val current = items.firstOrNull { it.id == editingId }
        PantryItemEditor(current, onDismiss = { editorOpen = false }, onSave = { name, amount ->
            val item = PantryItem(current?.id ?: UUID.randomUUID().toString(), name, amount)
            val updated = if (current == null) items + item else items.map { if (it.id == item.id) item else it }
            if (save(updated)) editorOpen = false
        }, error = error)
    }
}

@Composable
private fun PantryItemEditor(item: PantryItem?, onDismiss: () -> Unit, onSave: (String, String) -> Unit, error: String?) {
    var name by rememberSaveable(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var amount by rememberSaveable(item?.id) { mutableStateOf(item?.amount.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(16.dp),
        containerColor = ShopPaper, title = { Text(if (item == null) "Add an ingredient" else "Edit ingredient", color = ShopInk) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(name, { if (it.length <= 80) name = it }, singleLine = true,
                    label = { Text("Ingredient") }, placeholder = { Text("e.g. Milk") },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { if (it.length <= 40) amount = it }, singleLine = true,
                    label = { Text("Amount (optional)") }, placeholder = { Text("e.g. Half a carton") },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFF9A3A28), fontSize = 12.sp) }
            }
        }, confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), amount.trim()) }) { Text("Save ingredient", color = ShopInk) }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = ShopMuted) } })
}

@Composable
internal fun FridgeMark() {
    Canvas(Modifier.size(30.dp)) {
        val stroke = Stroke(width = 1.5.dp.toPx())
        drawRoundRect(ShopInk, Offset(size.width * .22f, size.height * .05f),
            Size(size.width * .56f, size.height * .9f), CornerRadius(3.dp.toPx()), style = stroke)
        drawLine(ShopInk, Offset(size.width * .22f, size.height * .38f), Offset(size.width * .78f, size.height * .38f), stroke.width)
        drawLine(ShopInk, Offset(size.width * .36f, size.height * .17f), Offset(size.width * .36f, size.height * .28f), stroke.width)
        drawLine(ShopInk, Offset(size.width * .36f, size.height * .49f), Offset(size.width * .36f, size.height * .65f), stroke.width)
    }
}
