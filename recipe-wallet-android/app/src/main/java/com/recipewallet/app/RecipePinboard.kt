package com.recipewallet.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun RecipePinboard(vm:WalletViewModel,recipes:List<Recipe> = vm.studio.rankedRecipes()) {
 var pinnedOnly by rememberSaveable { mutableStateOf(false) }
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
  Column(Modifier.weight(1f)){Eyebrow("Collected for your cravings");StudioTitle("Choose a recipe")}
  FilterChip(selected=pinnedOnly,onClick={pinnedOnly=!pinnedOnly},label={Text(if(pinnedOnly)"♥ Pinned"else "♡ My pins")})
 }
 val items=recipes.filter{!pinnedOnly||it.id in vm.studio.pinnedIds}
 if(items.isEmpty())StudioCard(tint=Peach){Text(if(pinnedOnly)"Your board is waiting for a little love."else "No recipe fits these filters yet.",color=ShopInk);SmallNote(if(pinnedOnly)"Tap a heart on a recipe to keep it here."else "Try more cooking time or change your dietary preference.")}
 BoxWithConstraints {
  val count=if(maxWidth>=850.dp)3 else if(maxWidth>=360.dp)2 else 1
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){
   repeat(count){col->Column(Modifier.weight(1f).padding(top=if(col%2==1)22.dp else 0.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
    items.mapIndexed{i,r->i to r}.filter{it.first%count==col}.forEach{(i,r)->RecipePin(vm,r,i)}
   }}
  }
 }
}
@Composable private fun RecipePin(vm:WalletViewModel,recipe:Recipe,index:Int){
 val tint=listOf(Peach,Mint,Lilac)[index%3]
 val pinned=recipe.id in vm.studio.pinnedIds
 val estimate=vm.studio.estimate(recipe)
 val have=recipe.ingredients.count{it.id in vm.studio.owned}
 Column(Modifier.fillMaxWidth().shadow(3.dp,RoundedCornerShape(7.dp)).background(Color(0xFFFFFCF6),RoundedCornerShape(7.dp)).border(1.dp,ShopLine,RoundedCornerShape(7.dp)).clickable{vm.choose(recipe.id)}.padding(10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Box(Modifier.fillMaxWidth().height(if(index%2==0)175.dp else 214.dp).background(tint,RoundedCornerShape(4.dp)),contentAlignment=Alignment.Center){
   FoodSticker(recipe.id,Modifier.size(if(index%2==0)145.dp else 174.dp).rotate(if(index%2==0)-7f else 6f))
   IconToggleButton(checked=pinned,onCheckedChange={vm.studio.togglePin(recipe.id)},modifier=Modifier.align(Alignment.TopEnd).semantics{contentDescription=if(pinned)"Unpin ${recipe.name}"else "Pin ${recipe.name}"}){Text(if(pinned)"♥"else "♡",color=ShopInk,fontSize=26.sp)}
   Text("${recipe.prepTime} min",color=ShopInk,fontSize=12.sp,modifier=Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0xFFFFFCF6),RoundedCornerShape(3.dp)).padding(6.dp))
  }
  Text(recipe.name,fontFamily=FontFamily.Serif,fontSize=22.sp,lineHeight=27.sp,fontWeight=FontWeight.Bold,color=ShopInk,modifier=Modifier.padding(horizontal=4.dp))
  Text(recipe.description,color=ShopMuted,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.padding(horizontal=4.dp))
  if(have>0)Signal("✓ $have pantry ingredients")
  if(estimate!=null&&have<recipe.ingredients.size)Signal("${money(estimate.charge)} pickup · ${vm.studio.people} people",if(estimate.charge>vm.studio.budget.toLong()*100)Danger else Good)
  Row(Modifier.fillMaxWidth().padding(4.dp),horizontalArrangement=Arrangement.SpaceBetween){SmallNote("${recipe.ingredients.size} ingredients");Text("Make it mine ↗",fontSize=12.sp,fontWeight=FontWeight.Bold,color=ShopInk)}
 }
}
