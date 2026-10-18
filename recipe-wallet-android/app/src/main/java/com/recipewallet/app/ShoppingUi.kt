package com.recipewallet.app

import androidx.compose.foundation.horizontalScroll
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

internal val ShopInk = Color(0xFF503542)
internal val ShopMuted = Color(0xFF78616A)
internal val ShopPaper = Color(0xFFE5C9B9)
internal val ShopLine = Color(0xFFE6D2C4)
internal val ShopTint = Color(0xFFF5E1E5)

@Composable
internal fun ShoppingPage(step: Int, onBack: () -> Unit, backEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = ShopPaper) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 1040.dp).fillMaxWidth().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    BrandWordmark(Modifier.weight(1f))
                    TextButton(onClick = onBack, enabled = backEnabled) { Text("← Back", color = ShopInk) }
                }
                if(step in 1..3)JourneySteps(step)
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
    Column(modifier.fillMaxWidth().background(if (selected) ShopTint else Color.White, RoundedCornerShape(22.dp))
        .border(1.dp, if (selected) ShopInk else ShopLine, RoundedCornerShape(22.dp)).padding(18.dp),
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) { Text(text) }
    else Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ShopInk)) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun SmallNote(text: String) { Text(text, color = ShopMuted, fontSize = 13.sp, lineHeight = 19.sp) }

@Composable internal fun JourneySteps(current:Int){
 Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalAlignment=Alignment.CenterVertically){
   listOf("Meal","Basket","Store","Checkout").forEachIndexed{i,label->
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
     Text((if(i<current)"✓ "else "")+label,color=if(i<=current)ShopInk else ShopMuted,fontSize=16.sp,fontWeight=if(i==current)FontWeight.Bold else FontWeight.Normal)
     Box(Modifier.width(76.dp).height(if(i==current)3.dp else 1.dp).background(if(i==current)ShopInk else ShopLine))
    }
   }
  }
 }
}

@Composable internal fun JournalNavigation(labels:List<String>,selected:Int,enabled:Boolean,onSelect:(Int)->Unit){
 ScrollableTabRow(selectedTabIndex=selected,edgePadding=0.dp,containerColor=Color.White,contentColor=ShopInk){
  labels.forEachIndexed{i,label->Tab(selected=selected==i,onClick={onSelect(i)},enabled=enabled,text={Text(label,fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontSize=18.sp,fontWeight=if(selected==i)FontWeight.Bold else FontWeight.Medium)})}
 }
}

@Composable internal fun BrandWordmark(modifier:Modifier=Modifier){
 Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
  androidx.compose.foundation.Image(painter=androidx.compose.ui.res.painterResource(R.drawable.ic_wallet),contentDescription="Recipe Wallet logo",modifier=Modifier.size(40.dp))
  Text("Recipe Wallet",color=ShopInk,fontFamily=androidx.compose.ui.text.font.FontFamily.SansSerif,fontWeight=FontWeight.ExtraBold,fontSize=26.sp,letterSpacing=(-1).sp,lineHeight=29.sp)
 }
}
