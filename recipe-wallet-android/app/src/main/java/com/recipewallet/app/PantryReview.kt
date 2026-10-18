package com.recipewallet.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PantryReview(
    detected: List<PantryDetection>, enabled: Boolean,
    onSelect: (String, Boolean) -> Unit, onEdit: (String) -> Unit,
    onSave: () -> Unit, onDismiss: () -> Unit
) {
    QuietCard {
        Text(if (detected.isEmpty()) "No ingredients found" else "Review your ingredients", color = ShopInk, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        SmallNote(if (detected.isEmpty()) "Try a closer, brighter photo, or add ingredients manually. Nothing was added to your pantry."
            else "Check the names and amounts. Uncheck anything we got wrong, or tap an ingredient to edit it.")
        detected.forEachIndexed { index, detection ->
            if (index > 0) HorizontalDivider(color = ShopLine)
            val item = detection.item
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = detection.selected, enabled = enabled, onCheckedChange = { onSelect(item.id, it) },
                    colors = CheckboxDefaults.colors(checkedColor = ShopInk),
                    modifier = Modifier.semantics { contentDescription = "Include ${item.name}" })
                Column(Modifier.weight(1f).clickable(enabled = enabled, role = Role.Button) { onEdit(item.id) }.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.name, color = ShopInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    SmallNote(item.amount.ifBlank { "Check amount" })
                }
                TextButton(enabled = enabled, onClick = { onEdit(item.id) },
                    modifier = Modifier.semantics { contentDescription = "Edit detected ${item.name}" }) { Text("Edit", color = ShopInk) }
            }
        }
        if (detected.isNotEmpty()) {
            val count = detected.count { it.selected }
            SmallNote("Only visible counts or readable package sizes are suggested. Already saved ingredients keep their existing amounts.")
            ShopButton("Save $count to pantry", enabled = enabled && count > 0, onClick = onSave)
        }
        ShopButton("Dismiss results", outlined = true, onClick = onDismiss)
    }
}
