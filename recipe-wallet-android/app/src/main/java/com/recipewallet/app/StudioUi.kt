package com.recipewallet.app

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import kotlin.random.Random
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.stateDescription
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

internal val Citrus=Color(0xFFF3D58A)
internal val Mint=Color(0xFFF1D8DF)
internal val Lilac=Color(0xFFE6DDF2)
internal val Peach=Color(0xFFF6D2B7)
internal val Good=Color(0xFF596139)
internal val Warn=Color(0xFF95612D)
internal val Danger=Color(0xFFA64451)

@Composable internal fun StudioCard(tint:Color=Color(0xFFF8DED0),modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
 Box(modifier.fillMaxWidth().padding(top=8.dp)) {
  Column(Modifier.fillMaxWidth().shadow(2.dp,RoundedCornerShape(8.dp)).background(tint,RoundedCornerShape(8.dp)).border(1.dp,ShopLine,RoundedCornerShape(8.dp)).drawBehind {
   val random=Random(42)
   repeat(1800){val x=random.nextFloat()*size.width;val y=random.nextFloat()*size.height;drawLine(ShopInk.copy(alpha=.07f),Offset(x,y),Offset(x+random.nextFloat()*5f,y+1.3f),.6f)}
   val edge=Random(9);var x=0f
   while(x<size.width){drawCircle(ShopPaper,edge.nextFloat()*2.dp.toPx()+1.dp.toPx(),Offset(x,0f));drawCircle(ShopPaper,edge.nextFloat()*2.dp.toPx()+1.dp.toPx(),Offset(x,size.height));x+=4.dp.toPx()}
  }.padding(start=30.dp,end=22.dp,top=27.dp,bottom=22.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
  WashiTape(Modifier.align(Alignment.TopCenter).offset(y=(-7).dp).size(72.dp,17.dp),color=Color(0xFFD0A8BF))
 }
}
/** Secondary information opens on demand; its current summary stays visible. */
@Composable internal fun InfoDisclosure(title:String,summary:String="",content:@Composable ColumnScope.()->Unit){
 var open by rememberSaveable(title){mutableStateOf(false)}
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
  TextButton(onClick={open=!open},modifier=Modifier.fillMaxWidth().semantics{stateDescription=if(open)"Expanded"else "Collapsed"}){
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
    Text(title,color=ShopInk,fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.semantics{heading()})
    if(summary.isNotBlank())Text(summary,color=ShopMuted,fontSize=12.sp)
   }
   Text(if(open)"−"else "+",color=ShopInk,fontSize=22.sp)
  }
  AnimatedVisibility(open){Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
  HorizontalDivider(color=ShopLine.copy(alpha=.5f))
 }
}
@Composable internal fun Eyebrow(text:String){Text(text.uppercase(),fontSize=11.sp,letterSpacing=1.5.sp,color=ShopMuted,fontWeight=FontWeight.Bold)}
@Composable internal fun StudioTitle(text:String){Text(text,fontSize=25.sp,lineHeight=31.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.SemiBold,color=ShopInk,modifier=Modifier.semantics { heading() })}
@Composable internal fun Signal(text:String,color:Color=Good){Text(text,color=color,fontSize=12.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.background(color.copy(alpha=.09f),RoundedCornerShape(50)).padding(horizontal=12.dp,vertical=8.dp))}
@Composable internal fun ChipRail(labels:List<String>,enabled:Boolean=true,onSelect:(Int)->Unit){
 Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {labels.forEachIndexed { i,s->SuggestionChip(onClick={onSelect(i)},enabled=enabled,label={Text(s)})}}
}
@Composable internal fun MoodDeck(state:MealStudioState,enabled:Boolean=true){
 var drag by remember { mutableFloatStateOf(0f) }
 val movement by animateFloatAsState(drag, spring(),label="Mood card motion")
 val mood=mealMoods[state.mood]
 val tint=listOf(Peach,Mint,Lilac,Citrus)[state.mood]
 StudioCard(tint=tint,modifier=Modifier.graphicsLayer { translationX=movement;rotationZ=movement/24f }.pointerInput(enabled,state.mood){if(enabled)detectHorizontalDragGestures(onDragStart={drag=0f},onDragEnd={if(kotlin.math.abs(drag)>100){if(drag>0)state.chooseMood();state.updateMood(state.mood+1)};drag=0f},onDragCancel={drag=0f}){change,delta->change.consume();drag+=delta}}){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Eyebrow("The flavor of your day");Text("${state.mood+1} / 4",fontSize=12.sp,color=ShopMuted)}
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){
   FoodSticker(when(state.mood){0->"bread";1->"avo";2->"tomato";else->"lemon"},Modifier.size(145.dp))
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){StudioTitle(mood.name);Text(mood.subtitle,color=ShopInk,fontSize=14.sp)}
  }
  if(kotlin.math.abs(drag)>35)Signal(if(drag>0)"LOVE THIS ♥" else "PASS ↶",if(drag>0)Good else Warn)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
   OutlinedButton(enabled=enabled,onClick={state.updateMood(state.mood+1)}){Text("← Pass")}
   TextButton(enabled=enabled,onClick={state.chooseMood();state.updateMood(state.mood+1)}){Text("Love this ♥ →")}
  }
  Text("Swipe left to pass · Swipe right to choose · Chosen: ${mealMoods[state.pickedMood].name}",fontSize=11.sp,color=ShopMuted)
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
  CuisinePicker(state)
  Text("Dietary preferences",fontWeight=FontWeight.Bold,color=ShopInk)
  OutlinedTextField(value=state.avoid,onValueChange={state.updateAvoid(it)},enabled=enabled,label={Text("Allergens / ingredients to avoid")},placeholder={Text("milk, wheat, sesame, peanuts")},supportingText={Text("Comma-separated. Matches named ingredients; labels and cross-contact still need review.")},modifier=Modifier.fillMaxWidth())
  Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("None","Halal","Kosher").forEach{choice->FilterChip(selected=state.faith==choice,onClick={state.updateFaith(choice)},enabled=enabled,label={Text(choice)})}}
  if(state.faith!="None")Signal("${state.faith} · certification must be checked",Warn)
  Row(verticalAlignment=Alignment.CenterVertically){Switch(enabled=enabled,checked=state.vegetarian,onCheckedChange={state.updateVegetarian(it)});Text("Vegetarian recipes",Modifier.padding(start=12.dp),color=ShopInk)}
 }
}
@Composable internal fun RecipeTile(recipe:Recipe,studio:MealStudioState,index:Int,onClick:()->Unit){
 val have=recipe.ingredients.count { it.id in studio.owned }
 StudioCard(tint=listOf(Mint,Peach,Lilac)[index%3],modifier=Modifier.clickable(onClick=onClick)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
   FoodSticker(recipe.id,Modifier.size(78.dp))
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text(recipe.name,fontSize=20.sp,fontWeight=FontWeight.SemiBold,color=ShopInk)
    Text("${recipe.prepTime} min · ${recipe.ingredients.size} ingredients",fontSize=12.sp,color=ShopMuted)
   }
   Text("↗",fontSize=26.sp,color=ShopInk)
  }
  if(have>0)Signal(if(have==recipe.ingredients.size)"✓ Pantry covers every ingredient" else "✓ You have $have of ${recipe.ingredients.size} ingredients")
  else SmallNote(recipe.description)
  val estimate=studio.estimate(recipe)
  if(estimate!=null&&have<recipe.ingredients.size)Signal("${money(estimate.charge)} pickup estimate · ${studio.people} people"+if(estimate.charge>studio.budget.toLong()*100)" · above target"else " · within target",if(estimate.charge>studio.budget.toLong()*100)Danger else Good)
 }
}
@Composable internal fun PriceTicket(quote:Quote,best:Long?=null,budget:Long?=null){
 val over=budget!=null&&budget>=0&&quote.charge>budget
 val color=if(!quote.complete||over)Danger else if(best==null||quote.charge==best)Good else Warn
 Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(ShopInk,Color(0xFF79505B))),RoundedCornerShape(20.dp)).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("YOUR BASKET, DECODED",color=Citrus,fontSize=11.sp,letterSpacing=1.7.sp,fontWeight=FontWeight.Bold)
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.SpaceBetween){
   Column{Text("Pay today",color=Color.White.copy(alpha=.8f),fontSize=13.sp);Text(if(quote.complete)money(quote.charge)else "Incomplete",fontSize=36.sp,fontWeight=FontWeight.Bold,color=Color.White)}
   Text(quote.fulfillment.uppercase(),color=Citrus,fontSize=11.sp,fontWeight=FontWeight.Bold)
  }
  var breakdown by remember(quote){mutableStateOf(false)}
  TextButton(onClick={breakdown=!breakdown}){Text(if(breakdown)"Hide price breakdown −"else "View price breakdown +",color=Color.White)}
  if(breakdown) listOf("Whole packages" to quote.groceries,"Fees" to quote.fees,"Demo tax (0%)" to quote.tax).forEach{(label,value)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,color=Color.White.copy(alpha=.8f),fontSize=13.sp);Text(money(value),color=Color.White,fontSize=13.sp)}}
  Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.1f),RoundedCornerShape(12.dp)).padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Estimated future rewards",color=Color.White,fontSize=12.sp);Text("+ ${money(quote.rewards)}",color=Citrus,fontWeight=FontWeight.Bold)}
  Text("Rewards arrive later and do not lower today's charge.",color=Color.White.copy(alpha=.75f),fontSize=11.sp)
 }
 Signal(if(!quote.complete)"! Missing items — cannot check out" else if(over)"! Above your meal budget" else if(best!=null&&quote.charge>best)"↗ ${money(quote.charge-best)} above lowest upfront total" else "✓ Complete basket" ,color)
}
