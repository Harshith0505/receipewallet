package com.recipewallet.app
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun RecipePinboard(vm:WalletViewModel,recipes:List<Recipe> = vm.studio.rankedRecipes()){
 var pinnedOnly by rememberSaveable{mutableStateOf(false)}
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){StudioTitle("Choose a recipe");FilterChip(selected=pinnedOnly,onClick={pinnedOnly=!pinnedOnly},label={Text(if(pinnedOnly)"♥ Pinned"else "♡ My pins")})}
 val items=recipes.filter{!pinnedOnly||it.id in vm.studio.pinnedIds}
 if(items.isEmpty())SmallNote("No recipes match. Try another cuisine, adjust filters, or turn off My pins.")
 BoxWithConstraints{
  val columns=if(maxWidth>=840.dp)3 else if(maxWidth>=440.dp)2 else 1
  Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
   items.chunked(columns).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){
    row.forEach{recipe->RecipeCard(vm,recipe,Modifier.weight(1f))}
    repeat(columns-row.size){Spacer(Modifier.weight(1f))}
   }}
  }
 }
}
@Composable private fun RecipeCard(vm:WalletViewModel,recipe:Recipe,modifier:Modifier){
 val pinned=recipe.id in vm.studio.pinnedIds;val estimate=vm.studio.estimate(recipe)
 Column(modifier.height(315.dp).shadow(2.dp,RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(when(recipe.id){"pasta"->Peach;"noodles"->Lilac;else->Mint}).clickable{vm.choose(recipe.id)}){
  Box(Modifier.fillMaxWidth().height(175.dp)){
   FoodSticker(recipe.id,Modifier.fillMaxSize())
   Text(if(recipe.id=="pasta")"Italian-inspired"else "◉ Vegetarian",fontSize=11.sp,color=ShopInk,modifier=Modifier.align(Alignment.TopStart).padding(8.dp).background(Citrus,RoundedCornerShape(20.dp)).padding(horizontal=9.dp,vertical=6.dp))
   IconToggleButton(checked=pinned,onCheckedChange={vm.studio.togglePin(recipe.id)},modifier=Modifier.align(Alignment.TopEnd).padding(5.dp).background(Mint,RoundedCornerShape(24.dp)).semantics{contentDescription=if(pinned)"Unpin ${recipe.name}"else "Pin ${recipe.name}"}){Text(if(pinned)"♥"else "♡",color=ShopInk,fontSize=25.sp)}
  }
  Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(recipe.name,color=ShopInk,fontWeight=FontWeight.Bold,fontSize=18.sp,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis)
   Text("◷ ${recipe.prepTime} min   ·   ${vm.studio.people} people",fontSize=12.sp,color=ShopMuted)
   Text(if(estimate!=null)"${money(estimate.charge)} pickup estimate"else "Check availability",fontWeight=FontWeight.SemiBold,fontSize=13.sp,color=ShopInk)
  }
 }
}
@Composable internal fun CuisinePicker(state:MealStudioState){
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("Cuisine · ${state.cuisine}",fontWeight=FontWeight.SemiBold,color=ShopInk)
  listOf(listOf("Any","Italian"),listOf("Asian","Mediterranean")).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{choice->FilterChip(selected=state.cuisine==choice,onClick={state.updateCuisine(choice)},modifier=Modifier.weight(1f),label={Text((if(state.cuisine==choice)"✓ "else "")+choice)})}}}
 }
}
