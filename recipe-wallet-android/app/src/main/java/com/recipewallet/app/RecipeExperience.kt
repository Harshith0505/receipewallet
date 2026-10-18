package com.recipewallet.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun RecipeHero(meal:JournalMeal){
 QuietCard{
  val photoId=meal.id.substringBefore(":shop:")
  if(photoId in listOf("noodles","bowl","pasta","chana","mexican-rice","sesame-noodles","chickpea-salad"))FoodSticker(photoId,Modifier.fillMaxWidth().height(190.dp))
  Eyebrow("Your recipe")
  StudioTitle(meal.name)
  if(meal.description.isNotBlank())SmallNote(meal.description)
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
   Text("${meal.minutes} min",Modifier.background(Mint,RoundedCornerShape(20.dp)).padding(10.dp),color=ShopInk,fontWeight=FontWeight.SemiBold)
   Text("${meal.servings} servings",Modifier.background(Citrus,RoundedCornerShape(20.dp)).padding(10.dp),color=ShopInk,fontWeight=FontWeight.SemiBold)
  }
 }
}
@Composable internal fun RecipeMethod(steps:List<String>){
 QuietCard{
  Text("Let's cook",color=ShopInk,fontWeight=FontWeight.Bold,fontSize=22.sp)
  if(steps.isEmpty())SmallNote("No cooking method was saved for this personal recipe. Add your own instructions before cooking.")
  steps.forEachIndexed{i,step->
   Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){
    Text("${i+1}",Modifier.background(Mint,RoundedCornerShape(12.dp)).padding(horizontal=12.dp,vertical=8.dp),color=ShopInk,fontWeight=FontWeight.Bold)
    Text(step,Modifier.weight(1f).padding(vertical=5.dp),color=ShopInk,lineHeight=24.sp)
   }
  }
  SmallNote("Follow your reviewed ingredients and dietary needs. Preparation time and quantities are estimates.")
 }
}
@Composable internal fun RecipeFoods(meal:JournalMeal){
 QuietCard{
  Text("Ingredients for ${meal.servings}",color=ShopInk,fontWeight=FontWeight.Bold,fontSize=22.sp)
  meal.foods.forEach{food->
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    Column(Modifier.weight(1f)){Text(food.name,color=ShopInk,fontWeight=FontWeight.Medium);SmallNote(if(food.owned)"Already have"else "On your shopping list")}
    Text(food.amount,color=ShopInk,modifier=Modifier.widthIn(max=170.dp))
   }
   HorizontalDivider(color=ShopLine)
  }
 }
}
@Composable internal fun RecipeHistoryScreen(vm:WalletViewModel){
 BackHandler{vm.step=0}
 ShoppingPage(0,{vm.step=0}){
  PageHeading("My recipes","Your meals, ready whenever you are")
  SmallNote("Saved on this device · Viewed recipes and sample checkouts · No real purchase history is inferred")
  val meals=vm.journal.meals
  val featured=meals.filter{it.sampleAt>0}.maxByOrNull{it.sampleAt}?:meals.firstOrNull()
  var selected by rememberSaveable{mutableStateOf<String?>(null)}
  val current=meals.firstOrNull{it.id==selected}?:featured
  if(current==null){Signal("Your recipe collection starts with your next meal");ShopButton("Find a meal",onClick={vm.step=0})}
  current?.let{meal->
   Eyebrow(if(meal.sampleAt>0&&meal.id==featured?.id)"Your latest sample shop · ready to cook"else if(meal.sampleAt>0)"A saved sample shop"else "Recently viewed")
   if(meal.sampleAt>0){Signal("Sample checkout complete · no charge or order");SmallNote("${meal.store} · ${money(meal.subtotal)} item subtotal · taxes and fees excluded")}
   RecipeHero(meal);RecipeFoods(meal);RecipeMethod(meal.steps)
  }
  if(meals.size>1){
   Text("Previously viewed",fontSize=22.sp,fontWeight=FontWeight.Bold,color=ShopInk)
   meals.filter{it.id!=current?.id}.forEach{meal->QuietCard{
    Text(meal.name,color=ShopInk,fontWeight=FontWeight.Bold)
    SmallNote("${meal.minutes} min · ${meal.servings} servings"+if(meal.sampleAt>0)" · sample checkout"else " · viewed")
    ShopButton("Open ${meal.name}",outlined=true,onClick={selected=meal.id})
   }}
  }
  ShopButton("Plan another meal",outlined=true,onClick={vm.restart()})
 }
}
@Composable internal fun SampleCheckoutScreen(vm:WalletViewModel){
 BackHandler{vm.step=2}
 ShoppingPage(3,{vm.step=2}){
  PageHeading("Sample checkout","Finish your meal plan")
  Signal("Simulation only · no charge · no grocery order")
  SmallNote("This presentation flow never opens Stripe or sends products to a retailer. Real Kroger cart transfer remains a separate option.")
  vm.currentMealSnapshot()?.let{RecipeHero(it)}
  QuietCard{
   Text(vm.sampleStore,color=ShopInk,fontWeight=FontWeight.Bold)
   AmountRow("Reviewed item subtotal",money(vm.sampleSubtotal),prominent=true)
   SmallNote("Taxes and fees excluded. This is not a final retailer checkout amount. No card details are needed.")
  }
  vm.error?.let{Signal(it,Danger)}
  ShopButton("Simulate payment & view recipe",onClick={vm.completeSampleCheckout()})
  ShopButton("Back to basket",outlined=true,onClick={vm.step=2})
 }
}
