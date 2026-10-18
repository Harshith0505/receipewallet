package com.recipewallet.app

import android.content.ActivityNotFoundException
import android.net.Uri
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
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.util.UUID

@Composable
internal fun PantryScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val pantry: PantryViewModel = viewModel()
    val items = pantry.items
    val photo = pantry.photo
    val loading = pantry.busy
    val error = pantry.error
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingReview by rememberSaveable { mutableStateOf(false) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = cameraPath
        cameraPath = null
        if (success && path != null) pantry.loadPhoto {
            try { PantryPhoto.read(context, Uri.fromFile(File(path))) }
            finally { PantryPhoto.removeCapture(context, path) }
        } else PantryPhoto.removeCapture(context, path)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pantry.loadPhoto { PantryPhoto.read(context, uri) }
    }

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
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (pantry.detections == null) 160.dp else 90.dp, max = if (pantry.detections == null) 260.dp else 112.dp)
                        .clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (pantry.detections == null) "Photo ready" else "Photo scanned", color = ShopInk, fontSize = 13.sp)
                    TextButton(enabled = !loading, onClick = pantry::removePhoto) {
                        Text("Remove photo", color = ShopMuted)
                    }
                }
            }
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = ShopInk)
                SmallNote(if (pantry.scanning) "Looking for ingredients…" else "Opening your photo…")
            }
            if (photo != null && pantry.detections == null) {
                ShopButton(if (pantry.scanning) "Scanning…" else "Scan ingredients", enabled = !loading, onClick = pantry::scan)
                SmallNote("Sends this photo to Perceptron to identify ingredients. You’ll review the results before saving.")
            }
            val takePhoto: () -> Unit = {
                try {
                    PantryPhoto.removeCapture(context, cameraPath)
                    val file = PantryPhoto.cameraFile(context)
                    cameraPath = file.absolutePath
                    camera.launch(PantryPhoto.cameraUri(context, file))
                } catch (_: ActivityNotFoundException) {
                    PantryPhoto.removeCapture(context, cameraPath); cameraPath = null
                    pantry.showError("No camera app is available. Choose a photo instead.")
                } catch (_: Exception) {
                    PantryPhoto.removeCapture(context, cameraPath); cameraPath = null
                    pantry.showError("Camera access is unavailable. Choose a photo instead.")
                }
            }
            val choosePhoto: () -> Unit = {
                try { picker.launch("image/*") }
                catch (_: ActivityNotFoundException) { pantry.showError("No photo picker is available on this device.") }
            }
            if (photo == null) {
                ShopButton("Take a photo", enabled = !loading, onClick = takePhoto)
                ShopButton("Choose a photo", outlined = true, enabled = !loading, onClick = choosePhoto)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) { ShopButton("Retake photo", outlined = true, enabled = !loading, onClick = takePhoto) }
                    Column(Modifier.weight(1f)) { ShopButton("Choose another", outlined = true, enabled = !loading, onClick = choosePhoto) }
                }
                if (pantry.detections != null) TextButton(enabled = !loading, onClick = pantry::scan) { Text("Scan again", color = ShopInk) }
            }

        }
        error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFF9A3A28), fontSize = 13.sp) }
        pantry.notice?.let { SmallNote(it) }
        pantry.detections?.let { detected ->
            PantryReview(detected, enabled = pantry.writable && !loading,
                onSelect = pantry::select, onEdit = { id -> editingReview = true; editingId = id; editorOpen = true },
                onSave = pantry::saveReviewed, onDismiss = pantry::dismissReview)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Your ingredients", color = ShopInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    SmallNote(if (items.isEmpty()) "Nothing added yet" else "${items.size} ${if (items.size == 1) "ingredient" else "ingredients"}")
                }
                TextButton(enabled = pantry.writable, onClick = { editingReview = false; editingId = null; editorOpen = true }) {
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
                            onClick = { editingReview = false; editingId = item.id; editorOpen = true }).padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.name, color = ShopInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            SmallNote(item.amount.ifBlank { "Amount not set" })
                        }
                        TextButton(onClick = { pantry.save(items.filterNot { it.id == item.id }) },
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
        val current = if (editingReview) pantry.detections?.firstOrNull { it.item.id == editingId }?.item else items.firstOrNull { it.id == editingId }
        PantryItemEditor(current, onDismiss = { editorOpen = false }, onSave = { name, amount ->
            val item = PantryItem(current?.id ?: UUID.randomUUID().toString(), name, amount)
            val updated = if (current == null) items + item else items.map { if (it.id == item.id) item else it }
            if (editingReview) { pantry.editDetection(item); editorOpen = false }
            else if (pantry.save(updated)) editorOpen = false
        }, error = error)
    }
}

@Composable
internal fun PantryItemEditor(item: PantryItem?, onDismiss: () -> Unit, onSave: (String, String) -> Unit, error: String?) {
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
