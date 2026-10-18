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

@Composable internal fun rememberRecipeSections(vm:WalletViewModel,columns:Int):List<HomeSection>{
 var pinnedOnly by rememberSaveable{mutableStateOf(false)}
 val recipes=vm.studio.rankedRecipes().filter{!pinnedOnly||it.id in vm.studio.pinnedIds}
 return buildList {
  add(HomeSection("recipe-heading"){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){StudioTitle("Choose a recipe");FilterChip(selected=pinnedOnly,onClick={pinnedOnly=!pinnedOnly},label={Text(if(pinnedOnly)"♥ Pinned"else "♡ My pins")})}
  })
  if(recipes.isEmpty())add(HomeSection("recipe-empty"){SmallNote("No recipes match. Try another cuisine, adjust filters, or turn off My pins.")})
  val rows=recipes.chunked(columns)
  rows.forEachIndexed{index,row->add(HomeSection("recipe-row:"+row.joinToString{it.id},if(index==rows.lastIndex)22.dp else 16.dp){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){
    row.forEach{recipe->RecipeCard(vm,recipe,Modifier.weight(1f))}
    repeat(columns-row.size){Spacer(Modifier.weight(1f))}
   }
  })}
 }
}
@Composable private fun RecipeCard(vm:WalletViewModel,recipe:Recipe,modifier:Modifier){
 val pinned=recipe.id in vm.studio.pinnedIds;val estimate=vm.studio.estimate(recipe)
 Column(modifier.height(315.dp).shadow(2.dp,RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).clickable{vm.choose(recipe.id)}){
  Box(Modifier.fillMaxWidth().height(175.dp)){
   FoodSticker(recipe.id,Modifier.fillMaxSize())
   Text(recipeCuisine(recipe.id)+"-inspired",fontSize=11.sp,color=ShopInk,modifier=Modifier.align(Alignment.TopStart).padding(8.dp).background(Citrus,RoundedCornerShape(20.dp)).padding(horizontal=9.dp,vertical=6.dp))
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
  cuisineChoices.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{choice->FilterChip(selected=state.cuisine==choice,onClick={state.updateCuisine(choice)},colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=ShopInk,selectedLabelColor=Color.White),modifier=Modifier.weight(1f),label={Text((if(state.cuisine==choice)"✓ "else "")+choice)})}}}
 }
}
