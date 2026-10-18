package com.recipewallet.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Citrus=Color(0xFFF7CD58)
internal val Mint=Color(0xFFDCECDF)
internal val Lilac=Color(0xFFEAE1F5)
internal val Peach=Color(0xFFFFE4D4)
internal val Good=Color(0xFF216548)
internal val Warn=Color(0xFF865200)
internal val Danger=Color(0xFF9E3737)

@Composable internal fun StudioCard(tint:Color=Color.White,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
 Column(modifier.fillMaxWidth().background(tint,RoundedCornerShape(24.dp)).border(1.dp,ShopLine,RoundedCornerShape(24.dp)).padding(22.dp).animateContentSize(),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
}
@Composable internal fun Eyebrow(text:String){Text(text.uppercase(),fontSize=11.sp,letterSpacing=1.5.sp,color=ShopMuted,fontWeight=FontWeight.Bold)}
@Composable internal fun StudioTitle(text:String){Text(text,fontSize=24.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold,color=ShopInk,modifier=Modifier.semantics { heading() })}
@Composable internal fun Signal(text:String,color:Color=Good){Text(text,color=color,fontSize=12.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.background(color.copy(alpha=.09f),RoundedCornerShape(50)).padding(horizontal=12.dp,vertical=8.dp))}
@Composable internal fun ChipRail(labels:List<String>,enabled:Boolean=true,onSelect:(Int)->Unit){
 Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {labels.forEachIndexed { i,s->SuggestionChip(onClick={onSelect(i)},enabled=enabled,label={Text(s)})}}
}
@Composable internal fun MoodDeck(state:MealStudioState,enabled:Boolean=true){
 var drag by remember { mutableFloatStateOf(0f) }
 val mood=mealMoods[state.mood]
 val tint=listOf(Peach,Mint,Lilac,Citrus)[state.mood]
 StudioCard(tint=tint,modifier=Modifier.graphicsLayer { translationX=drag*.18f;rotationZ=drag/100f }.pointerInput(enabled,state.mood){if(enabled)detectHorizontalDragGestures(onDragStart={drag=0f},onDragEnd={if(kotlin.math.abs(drag)>70)state.updateMood(state.mood+if(drag<0)1 else 3);drag=0f},onDragCancel={drag=0f}){change,delta->change.consume();drag+=delta}}){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Eyebrow("The flavor of your day");Text("${state.mood+1} / 4",fontSize=12.sp,color=ShopMuted)}
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){
   Text(mood.emoji,fontSize=52.sp)
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){StudioTitle(mood.name);Text(mood.subtitle,color=ShopInk,fontSize=14.sp)}
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   OutlinedButton(enabled=enabled,onClick={state.updateMood(state.mood+3)}){Text("← Previous mood")}
   TextButton(enabled=enabled,onClick={state.updateMood(state.mood+1)}){Text("Next mood →")}
  }
  Text("Swipe to explore · Tap “Use my taste profile” to apply to AI",fontSize=11.sp,color=ShopMuted)
 }
}
@Composable internal fun TasteControls(state:MealStudioState,enabled:Boolean=true){
 StudioCard {
  Eyebrow("Make it yours")
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   Text("At the table",fontWeight=FontWeight.SemiBold,color=ShopInk)
   Row(verticalAlignment=Alignment.CenterVertically){IconButton(modifier=Modifier.semantics { contentDescription="Fewer people" },enabled=enabled&&state.people>1,onClick={state.updatePeople(state.people-1)}){Text("−",fontSize=24.sp)};Text("${state.people}",fontWeight=FontWeight.Bold);IconButton(modifier=Modifier.semantics { contentDescription="More people" },enabled=enabled&&state.people<12,onClick={state.updatePeople(state.people+1)}){Text("+",fontSize=24.sp)}}
  }
  Text("Your pace · ${state.minutes.toInt()} min",fontWeight=FontWeight.Medium,color=ShopInk)
  Slider(modifier=Modifier.semantics { contentDescription="Maximum cooking time" },value=state.minutes,onValueChange={state.updateMinutes(it)},valueRange=15f..45f,steps=5,enabled=enabled)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){SmallNote("Quick & easy");SmallNote("Time to create")}
  Text("Your comfort zone",fontWeight=FontWeight.Medium,color=ShopInk)
  Slider(modifier=Modifier.semantics { contentDescription="Familiar favorites to something new" },value=state.novelty,onValueChange={state.updateNovelty(it)},valueRange=0f..1f,enabled=enabled)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){SmallNote("Familiar favorites");SmallNote("Something new")}
  Text("Meal budget · $${state.budget.toInt()}",fontWeight=FontWeight.Medium,color=ShopInk)
  Slider(modifier=Modifier.semantics { contentDescription="Meal budget in dollars" },value=state.budget,onValueChange={state.updateBudget(it)},valueRange=10f..80f,steps=13,enabled=enabled)
  Row(verticalAlignment=Alignment.CenterVertically){Switch(enabled=enabled,checked=state.vegetarian,onCheckedChange={state.updateVegetarian(it)});Text("Vegetarian recipes",Modifier.padding(start=12.dp),color=ShopInk)}
 }
}
@Composable internal fun RecipeTile(recipe:Recipe,studio:MealStudioState,index:Int,onClick:()->Unit){
 val have=recipe.ingredients.count { it.id in studio.owned }
 StudioCard(tint=listOf(Mint,Peach,Lilac)[index%3],modifier=Modifier.clickable(onClick=onClick)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
   Text(recipe.emoji,fontSize=44.sp)
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text(recipe.name,fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=ShopInk)
    Text("${recipe.prepTime} min · ${recipe.ingredients.size} ingredients",fontSize=12.sp,color=ShopMuted)
   }
   Text("↗",fontSize=26.sp,color=ShopInk)
  }
  if(have>0)Signal(if(have==recipe.ingredients.size)"✓ Pantry covers every ingredient" else "✓ You have $have of ${recipe.ingredients.size} ingredients")
  else SmallNote(recipe.description)
 }
}
@Composable internal fun PriceTicket(quote:Quote,best:Long?=null,budget:Long?=null){
 val over=budget!=null&&budget>=0&&quote.charge>budget
 val color=if(!quote.complete||over)Danger else if(best==null||quote.charge==best)Good else Warn
 Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(ShopInk,Color(0xFF284F43))),RoundedCornerShape(20.dp)).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("YOUR BASKET, DECODED",color=Citrus,fontSize=11.sp,letterSpacing=1.7.sp,fontWeight=FontWeight.Bold)
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.SpaceBetween){
   Column{Text("Pay today",color=Color.White.copy(alpha=.8f),fontSize=13.sp);Text(if(quote.complete)money(quote.charge)else "Incomplete",fontSize=36.sp,fontWeight=FontWeight.Bold,color=Color.White)}
   Text(quote.fulfillment.uppercase(),color=Citrus,fontSize=11.sp,fontWeight=FontWeight.Bold)
  }
  HorizontalDivider(color=Color.White.copy(alpha=.2f))
  listOf("Whole packages" to quote.groceries,"Fees" to quote.fees,"Demo tax (0%)" to quote.tax).forEach{(label,value)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Color.White.copy(alpha=.8f),fontSize=13.sp);Text(money(value),color=Color.White,fontSize=13.sp)}}
  Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.1f),RoundedCornerShape(12.dp)).padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Estimated future rewards",color=Color.White,fontSize=12.sp);Text("+ ${money(quote.rewards)}",color=Citrus,fontWeight=FontWeight.Bold)}
  Text("Rewards arrive later and do not lower today's charge.",color=Color.White.copy(alpha=.75f),fontSize=11.sp)
 }
 Signal(if(!quote.complete)"! Missing items — cannot check out" else if(over)"! Above your meal budget" else if(best!=null&&quote.charge>best)"↗ ${money(quote.charge-best)} above lowest upfront total" else "✓ Complete basket" ,color)
}
