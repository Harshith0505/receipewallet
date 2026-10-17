package com.recipewallet.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ShopInk = Color(0xFF263A2E)
internal val ShopMuted = Color(0xFF656B63)
internal val ShopPaper = Color(0xFFFAFAF7)
internal val ShopLine = Color(0xFFE1E3DC)
internal val ShopTint = Color(0xFFF0F3EB)

@Composable
internal fun ShoppingPage(step: Int, onBack: () -> Unit, backEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = ShopPaper) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("recipe wallet", color = ShopInk, fontSize = 22.sp,
                        letterSpacing = (-0.6).sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onBack, enabled = backEnabled) { Text("← Back", color = ShopInk) }
                }
                if (step in 1..3) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("Ingredients", "Shops", "Checkout").forEachIndexed { index, label ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("${index + 1}  $label", color = if (index + 1 == step) ShopInk else ShopMuted,
                                fontSize = 12.sp, fontWeight = if (index + 1 == step) FontWeight.SemiBold else FontWeight.Normal)
                            HorizontalDivider(color = if (index + 1 == step) ShopInk else ShopLine,
                                thickness = if (index + 1 == step) 2.dp else 1.dp)
                        }
                    }
                }
                content()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
internal fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = ShopInk, fontSize = 30.sp, lineHeight = 36.sp,
            letterSpacing = (-0.6).sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = ShopMuted, fontSize = 15.sp, lineHeight = 22.sp)
    }
}

@Composable
internal fun QuietCard(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(if (selected) ShopTint else Color.White, RoundedCornerShape(14.dp))
        .border(1.dp, if (selected) ShopInk else ShopLine, RoundedCornerShape(14.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
internal fun AmountRow(label: String, value: String, prominent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (prominent) ShopInk else ShopMuted, fontSize = 13.sp,
            modifier = Modifier.weight(1f).padding(end = 16.dp))
        Text(value, color = ShopInk, fontSize = if (prominent) 25.sp else 14.sp,
            fontWeight = if (prominent) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
internal fun ShopButton(text: String, enabled: Boolean = true, outlined: Boolean = false, onClick: () -> Unit) {
    if (outlined) OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) { Text(text) }
    else Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopInk)) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun SmallNote(text: String) { Text(text, color = ShopMuted, fontSize = 12.sp, lineHeight = 18.sp) }
