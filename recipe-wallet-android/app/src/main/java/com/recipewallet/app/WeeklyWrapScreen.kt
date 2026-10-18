package com.recipewallet.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WrapRose=Color(0xFFF1C8CB)
private val WrapButter=Color(0xFFF5DF95)
private val WrapLavender=Color(0xFFE5D9EE)
private val WrapPeach=Color(0xFFF1C7AC)
private val WrapPlum=Color(0xFF573D52)
private fun macroNumber(value:Double)=String.format(Locale.US,"%.1f",value)
private fun portionNumber(value:Double)=String.format(Locale.US,"%.2f",value).trimEnd('0').trimEnd('.')
private fun shortDate(date:LocalDate)=date.format(DateTimeFormatter.ofPattern("MMM d",Locale.US))

@Composable internal fun WeeklyWrapScreen(vm:WalletViewModel) {
 val state=vm.wrap
 var editor by rememberSaveable{mutableStateOf(false)}
 var editingId by rememberSaveable{mutableStateOf<String?>(null)}
 var editingRevision by rememberSaveable{mutableIntStateOf(0)}
 val week=state.report
 StudioCard(tint=WrapButter){
  Eyebrow("A delicious little time capsule")
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
   Column(Modifier.weight(1f)){StudioTitle("Your week,\nwith extra flavor.");Text("The meals you ate. The story they made.",color=ShopInk,fontSize=15.sp)}
   FoodSticker("tomato",Modifier.size(90.dp).graphicsLayer{rotationZ=9f})
  }
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   FilterChip(selected=!state.previousWeek,onClick={state.selectWeek(false);editor=false},label={Text("This week")})
   FilterChip(selected=state.previousWeek,onClick={state.selectWeek(true);editor=false},label={Text("Last week")})
  }
  Text("${shortDate(state.weekStart)} – ${shortDate(state.weekStart.plusDays(6))}",fontWeight=FontWeight.SemiBold,color=ShopInk)
  SmallNote("A personal food journal, built from meals you explicitly log. Shopping, pantry scans and meal plans never count as food eaten.")
 }
 if(state.sample)StudioCard(tint=WrapRose){
  Signal("SAMPLE STORY · Fictional meals",WrapPlum)
  Text("A peek at what's possible. These six example meals are separate from your journal and never saved.",color=ShopInk,fontSize=14.sp)
  TextButton(onClick={state.previewSample(false)}){Text("Back to my own journal →")}
 }else{
  state.journalWarning?.let{Signal(it,Warn)}
  if(week.entries.isEmpty())StudioCard(tint=WrapLavender){
   Eyebrow("Every food story starts somewhere")
   StudioTitle("One meal is enough to begin.")
   Text("Log what you actually ate, including ingredients you already had. A portion of a recipe is one serving, not the whole pot.",color=ShopInk,fontSize=14.sp)
   ShopButton("+ Log a meal I ate",onClick={editingId=null;editingRevision++;editor=true})
   TextButton(onClick={editor=false;state.previewSample(true)}){Text("Preview a sample food story ↗")}
  }else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
   Column{Eyebrow("Your meal journal");Text("${week.entries.size} meals · ${week.daysLogged} of 7 days",color=ShopInk,fontWeight=FontWeight.SemiBold)}
   OutlinedButton(onClick={editingId=null;editingRevision++;editor=true}){Text("+ Log meal")}
  }
 }
 AnimatedVisibility(visible=editor&&!state.sample){
  key(editingRevision){MealLogEditor(vm,editingId,onSaved={editor=false},onCancel={editor=false})}
 }
 if(week.entries.isNotEmpty()){
  WrapStory(vm,week)
  WrapAgent(vm)
 }
 if(!state.sample&&week.entries.isNotEmpty()){
  StudioCard{
   Eyebrow("The receipts for your story")
   StudioTitle("Meals you logged")
   SmallNote("Edit portions, dates or consumed ingredients at any time. Saved recipes outside the small nutrition catalog aren't estimated yet.")
   week.entries.forEach{entry->
    val recipe=vm.catalog.recipes.first{it.id==entry.recipeId}
    Column(Modifier.fillMaxWidth().background(ShopPaper,RoundedCornerShape(14.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
      FoodSticker(recipe.id,Modifier.size(48.dp))
      Column(Modifier.weight(1f)){Text(recipe.name,color=ShopInk,fontWeight=FontWeight.SemiBold);Text("${shortDate(LocalDate.parse(entry.date))} · ${portionNumber(entry.servings)} servings eaten",color=ShopMuted,fontSize=12.sp)}
     }
     Text("${entry.ingredientIds.size} of ${recipe.ingredients.size} ingredients included",color=ShopMuted,fontSize=12.sp)
     Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
      TextButton(onClick={editingId=entry.id;editingRevision++;editor=true}){Text("Edit meal")}
      TextButton(onClick={state.removeMeal(entry.id);if(editingId==entry.id)editor=false}){Text("Remove")}
     }
    }
   }
  }
 }
 state.error?.let{Signal(it,Danger)}
 NutritionFootnotes(vm)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MealLogEditor(vm:WalletViewModel,entryId:String?,onSaved:()->Unit,onCancel:()->Unit){
 val state=vm.wrap;val existing=state.entries.firstOrNull{it.id==entryId}
 var recipeId by rememberSaveable{mutableStateOf(existing?.recipeId?:vm.catalog.recipes.first().id)}
 val recipe=vm.catalog.recipes.first{it.id==recipeId}
 var amount by rememberSaveable{mutableStateOf(existing?.servings?.toString()?:"1.0")}
 var date by rememberSaveable{mutableStateOf(existing?.date?:if(state.previousWeek)state.weekStart.toString()else LocalDate.now().toString())}
 var selected by remember{mutableStateOf(existing?.ingredientIds?.toSet()?:recipe.ingredients.map{it.id}.toSet())}
 StudioCard(tint=WrapPeach){
  Eyebrow(if(existing==null)"A moment worth remembering"else "Edit this food memory")
  StudioTitle(if(existing==null)"What did you eat?"else "Make your log accurate")
  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   vm.catalog.recipes.forEach{r->FilterChip(selected=r.id==recipeId,onClick={recipeId=r.id;selected=r.ingredients.map{it.id}.toSet()},label={Text(r.name)})}
  }
  Text("Date eaten",fontWeight=FontWeight.SemiBold,color=ShopInk)
  Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
   (0L..6L).forEach{offset->val day=state.weekStart.plusDays(offset)
    FilterChip(selected=date==day.toString(),enabled=!day.isAfter(LocalDate.now()),onClick={date=day.toString()},label={Text(day.format(DateTimeFormatter.ofPattern("EEE d",Locale.US)))})
   }
  }
  OutlinedTextField(value=amount,onValueChange={amount=it.take(6)},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Servings you personally ate")},supportingText={Text("0.25–12 servings · recipe makes ${recipe.baseServings} servings")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
  val parsed=amount.toDoubleOrNull()
  Slider(value=(parsed?.takeIf{it.isFinite()}?:1.0).toFloat().coerceIn(.25f,4f),onValueChange={amount=String.format(Locale.US,"%.2f",kotlin.math.round(it*4)/4)},valueRange=.25f..4f,steps=14,modifier=Modifier.semantics{contentDescription="Servings actually eaten"})
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){SmallNote("A little taste · ¼");SmallNote("4 servings · or type above")}
  Text("What was in your meal?",fontWeight=FontWeight.SemiBold,color=ShopInk)
  SmallNote("Include pantry ingredients too. Uncheck only ingredients you left out of the food you ate. Quantities scale from the recipe; custom quantities aren't estimated.")
  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   recipe.ingredients.forEach{item->FilterChip(selected=item.id in selected,onClick={selected=if(item.id in selected)selected-item.id else selected+item.id},label={Text(item.name)},leadingIcon={Text(if(item.id in selected)"✓"else "+")})}
  }
  ShopButton(if(existing==null)"Save my food memory"else "Save meal changes",enabled=parsed!=null&&parsed.isFinite()&&parsed in .25..12.0&&selected.isNotEmpty(),onClick={if(state.saveMeal(entryId,recipeId,date,parsed?:1.0,recipe.ingredients.filter{it.id in selected}.map{it.id}))onSaved()})
  state.error?.let{Text(it,color=Danger,fontSize=13.sp)}
  TextButton(onClick=onCancel){Text("Cancel")}
 }
}

@Composable private fun WrapStory(vm:WalletViewModel,week:FoodWeek){
 val state=vm.wrap;val pager=rememberPagerState(pageCount={5});val scope=rememberCoroutineScope()
 val favorite=vm.catalog.recipes.firstOrNull{it.id==week.favoriteRecipeId}
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Eyebrow(if(state.sample)"The sample food story"else "The food story");Text("Swipe to turn the page ↔",color=ShopMuted,fontSize=12.sp)}
  HorizontalPager(state=pager,key={"${state.weekStart}-${state.sample}-$it"},modifier=Modifier.fillMaxWidth().heightIn(min=455.dp),pageSpacing=16.dp,verticalAlignment=Alignment.Top){page->
   val tint=listOf(WrapRose,WrapButter,WrapLavender,WrapPeach,WrapButter)[page]
   Column(Modifier.fillMaxWidth().heightIn(min=445.dp).background(tint,RoundedCornerShape(27.dp)).border(1.dp,WrapPlum.copy(alpha=.15f),RoundedCornerShape(27.dp)).padding(25.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("FOOD MEMORIES / ${if(state.sample)"SAMPLE"else "${state.weekStart.year}"}",fontSize=11.sp,letterSpacing=1.sp,color=WrapPlum,fontWeight=FontWeight.Bold);Text("0${page+1} / 05",color=WrapPlum,fontSize=12.sp)}
    when(page){
     0->{
      FoodSticker(favorite?.id?:"tomato",Modifier.size(105.dp).align(Alignment.CenterHorizontally).graphicsLayer{rotationZ=-7f})
      Text("Look what\nyou brought\nto the table.",fontFamily=FontFamily.Serif,fontSize=37.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold,color=WrapPlum)
      Text("${week.entries.size} logged meals. ${portionNumber(week.servings)} servings.\n${week.daysLogged} days with a food memory.",color=WrapPlum,fontSize=17.sp,lineHeight=25.sp)
      Text("${shortDate(week.weekStart)} — ${shortDate(week.weekStart.plusDays(6))}",color=WrapPlum,fontSize=13.sp)
     }
     1->{
      Text("Your macros,\nin the margins.",fontFamily=FontFamily.Serif,fontSize=34.sp,lineHeight=37.sp,fontWeight=FontWeight.Bold,color=WrapPlum)
      Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){MacroSquare("Protein",week.totals.proteinG,"g",Color.White,Modifier.weight(1f));MacroSquare("Carbs",week.totals.carbsG,"g",WrapPeach,Modifier.weight(1f))}
      Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){MacroSquare("Total sugars",week.totals.sugarG,"g",WrapRose,Modifier.weight(1f));MacroSquare("Fat",week.totals.fatG,"g",Color.White,Modifier.weight(1f))}
      Text("${macroNumber(week.totals.fiberG)} g fiber · ${macroNumber(week.totals.kcal)} kcal",color=WrapPlum,fontWeight=FontWeight.SemiBold,fontSize=15.sp)
      Text("Estimated from logged recipe portions only. Total sugars are part of carbs, not an added amount. No daily target or health score.",color=WrapPlum,fontSize=12.sp,lineHeight=18.sp)
     }
     2->{
      FoodSticker("lemon",Modifier.size(95.dp).graphicsLayer{rotationZ=12f})
      Text("A little variety.\nA lot of you.",fontFamily=FontFamily.Serif,fontSize=35.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold,color=WrapPlum)
      Text("${week.ingredientCount}",fontSize=62.sp,fontWeight=FontWeight.Bold,color=WrapPlum)
      Text("different ingredients in the meals you logged",color=WrapPlum,fontSize=18.sp)
      Text("Your repeat character: ${favorite?.name?:"your next meal"}.\nBased on logged meals, not shopping history.",color=WrapPlum,fontSize=14.sp,lineHeight=21.sp)
     }
     3->{
      FoodSticker("avo",Modifier.size(88.dp))
      Text("The empty pages\nbelong to you.",fontFamily=FontFamily.Serif,fontSize=34.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold,color=WrapPlum)
      Text("${week.daysLogged} / 7",fontSize=54.sp,fontWeight=FontWeight.Bold,color=WrapPlum)
      Text("days have at least one logged meal",color=WrapPlum,fontSize=18.sp)
      Text("A blank day means no meal was logged. It doesn't mean you didn't eat. This is a scrapbook of what you tell us, not your complete nutritional intake.",color=WrapPlum,fontSize=15.sp,lineHeight=23.sp)
     }
     4->{
      FoodSticker("tomato",Modifier.size(88.dp).graphicsLayer{rotationZ=-9f})
      Text("Your next chapter\nis on the menu.",fontFamily=FontFamily.Serif,fontSize=34.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold,color=WrapPlum)
      Text("Let your food agent turn these logged meals and your preferences into a small, practical next-meal idea.",color=WrapPlum,fontSize=16.sp,lineHeight=24.sp)
      Text("It reads the log → checks the ingredient math → looks through supported recipes → suggests a next step for you to approve.",color=WrapPlum,fontSize=14.sp,lineHeight=21.sp)
      Text("Use “Ask my food agent” just below your story.",color=WrapPlum,fontWeight=FontWeight.Bold,fontSize=14.sp)
     }
    }
   }
  }
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
   TextButton(enabled=pager.currentPage>0,onClick={scope.launch{pager.animateScrollToPage(pager.currentPage-1)}}){Text("← Back")}
   Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.semantics{contentDescription="Story page ${pager.currentPage+1} of 5"}){
    repeat(5){n->Box(Modifier.size(10.dp).background(if(n==pager.currentPage)WrapPlum else WrapPlum.copy(alpha=.2f),CircleShape))}
   }
   TextButton(enabled=pager.currentPage<4,onClick={scope.launch{pager.animateScrollToPage(pager.currentPage+1)}}){Text("Next →")}
  }
 }
}

@Composable private fun MacroSquare(label:String,value:Double,unit:String,tint:Color,modifier:Modifier=Modifier){
 Column(modifier.background(tint.copy(alpha=.75f),RoundedCornerShape(16.dp)).padding(13.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
  Text(label,color=WrapPlum,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
  Text("${macroNumber(value)} $unit",color=WrapPlum,fontSize=23.sp,fontWeight=FontWeight.Bold)
 }
}

@Composable private fun WrapAgent(vm:WalletViewModel){
 val state=vm.wrap;val result=state.agentFor(vm.studio.prompt(),vm.studio.vegetarian)
 StudioCard(tint=WrapLavender){
  Eyebrow("Your food-story agent")
  StudioTitle("A thoughtful next bite.")
  SmallNote(if(state.sample)"Sample meals and your taste preferences are sent when you tap. The sample stays separate from your journal."else "When you tap, this week's logged meals and taste preferences are sent to the backend for a recipe suggestion. The agent cannot change your journal or buy anything.")
  ShopButton(if(state.busy)"Reading your food story…"else if(result==null)"Ask my food agent ✦"else "Refresh my food story",enabled=!state.busy,onClick={state.askAgent(vm.studio.prompt(),vm.studio.vegetarian)})
  if(state.busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text("Checking recipe portions, then finding your next meal…",color=ShopInk,fontSize=13.sp)}
  if(result!=null){
   val live=result.optString("source")=="gemini"
   Signal(if(live)"Gemini food agent"else "Local food agent · rule-based",WrapPlum)
   if(!live)SmallNote("A deterministic local planner built this story. Live generative AI wasn't used.")
   val cards=result.optJSONArray("cards")
   if(cards!=null)repeat(cards.length()){n->val card=cards.getJSONObject(n)
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha=.6f),RoundedCornerShape(14.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
     Text(card.optString("title"),fontWeight=FontWeight.SemiBold,color=ShopInk,fontSize=16.sp)
     Text(card.optString("body"),color=ShopInk,fontSize=14.sp,lineHeight=21.sp)
    }
   }
   val id=result.optString("nextRecipeId");val next=vm.catalog.recipes.firstOrNull{it.id==id}
   if(next!=null&&vm.studio.fits(next)){SmallNote("A recipe idea, not a nutrition prescription. Review ingredients and your dietary needs.");ShopButton("Make ${next.name} →",onClick={vm.choose(next.id)})}
   var traceOpen by remember(result){mutableStateOf(false)}
   TextButton(onClick={traceOpen=!traceOpen}){Text(if(traceOpen)"Hide the agent's steps ↑"else "See how it got here ↓")}
   if(traceOpen){val trace=result.optJSONArray("trace");if(trace!=null)repeat(trace.length()){n->Text("${n+1}. ${trace.getJSONObject(n).optString("summary")}",fontSize=13.sp,lineHeight=20.sp,color=ShopInk)}}
  }
 }
}

@Composable private fun NutritionFootnotes(vm:WalletViewModel){
 var open by remember{mutableStateOf(false)}
 StudioCard{
  Eyebrow("Small print. Clear meaning.")
  Text("Portion estimates, not your full intake.",color=ShopInk,fontWeight=FontWeight.SemiBold)
  SmallNote("Values use illustrative ingredient profiles and the recipe quantities for each serving you logged. They exclude unlogged meals, drinks, snacks and ingredients. Brands, preparation and portion sizes change actual values.")
  TextButton(onClick={open=!open}){Text(if(open)"Hide estimate details ↑"else "See ingredients & estimate details ↓")}
  if(open){
   SmallNote(vm.wrap.reference.optString("note"))
   vm.catalog.recipes.flatMap{it.ingredients}.distinctBy{it.id}.forEach{ingredient->Text("${ingredient.name} · ${vm.wrap.assumption(ingredient.id)}",color=ShopMuted,fontSize=12.sp,lineHeight=18.sp)}
   val sources=vm.wrap.reference.optJSONArray("sources")
   if(sources!=null)repeat(sources.length()){index->val source=sources.getJSONObject(index);SmallNote("Reference: ${source.optString("title")} — ${source.optString("url")}")}
  }
 }
}
