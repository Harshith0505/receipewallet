package com.recipewallet.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun HomeScreen(vm:WalletViewModel){
 var mode by rememberSaveable { mutableIntStateOf(0) }
 var libraryPage by rememberSaveable { mutableStateOf(if(vm.isPersonal)"saved" else "home") }
 var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
 val context=LocalContext.current;val saved=remember{PersonalRecipeStore(context)}
 var recipes by remember{mutableStateOf(saved.read())}
 androidx.activity.compose.BackHandler(enabled=libraryPage!="home"||mode!=0) { if(libraryPage!="home")libraryPage=if(libraryPage=="saved")"home" else "saved" else mode=0 }
 val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
 val dismiss:()->Unit={focus.clearFocus();keyboard?.hide()}
 if(libraryPage!="home"){
  RecipeLibraryScreen(page=libraryPage,recipe=recipes.firstOrNull{it.id==selectedId},recipes=recipes,
   onBack={dismiss();libraryPage=if(libraryPage=="saved")"home" else "saved"},onAdd={selectedId=null;libraryPage="add"},
   onOpen={selectedId=it.id;libraryPage="detail"},onEdit={libraryPage="add"},onUse={dismiss();vm.choosePersonal(it)},
   onSave={r->runCatching{recipes=saved.save(r);dismiss();libraryPage="saved"}.exceptionOrNull()?.message})
  return
 }
 LaunchedEffect(vm.studio.currentWeek,vm.studio.autoWeek){if(vm.studio.autoWeek&&vm.studio.weekDue)vm.studio.buildWeek()}
 Scaffold(containerColor=ShopPaper){padding->
  BoxWithConstraints(Modifier.fillMaxSize().drawBehind {
   val gap=24.dp.toPx();var y=gap
   while(y<size.height){var x=gap;while(x<size.width){drawCircle(ShopLine.copy(alpha=.48f),.7.dp.toPx(),androidx.compose.ui.geometry.Offset(x,y));x+=gap};y+=gap}
  }.padding(padding),contentAlignment=Alignment.TopCenter){
   val wide=maxWidth>=840.dp
   Column(Modifier.widthIn(max=1200.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(if(wide)32.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
     Column{Text("recipe wallet",fontSize=28.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,color=ShopInk,letterSpacing=(-1).sp);Eyebrow("a little kitchen journal")}
     TextButton(enabled=!vm.aiBusy,onClick={dismiss();recipes=saved.read();libraryPage="saved"}){Text("Saved recipes ↗")}
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup().background(Color(0xFFF3E3D9),RoundedCornerShape(16.dp)).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
     listOf("Build with AI","Build manually","My pantry","My week","My wrap-up").forEachIndexed{i,label->
      Box(Modifier.defaultMinSize(minWidth=130.dp,minHeight=48.dp).background(if(mode==i)ShopInk else listOf(Peach,Citrus,Mint,Lilac,Peach)[i].copy(alpha=.45f),RoundedCornerShape(9.dp)).selectable(selected=mode==i,enabled=!vm.aiBusy,role=Role.Tab,onClick={dismiss();mode=i}).padding(horizontal=14.dp),contentAlignment=Alignment.Center){Text(label,fontSize=13.sp,color=if(mode==i)Color.White else ShopInk,fontWeight=FontWeight.SemiBold)}
     }
    }
    if(vm.studio.weekDue&&mode!=3&&mode!=4)StudioCard(tint=Lilac){
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
      Column(Modifier.weight(1f)){Eyebrow("A fresh week awaits");Text("Five dinners, one less thing to think about.",fontSize=14.sp,color=ShopInk)}
      TextButton(onClick={dismiss();mode=3}){Text("Plan my week →")}
     }
    }
    when(mode){
     0->{
      if(wide)Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
       Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(20.dp)){MealComposer(vm,{mode=1},dismiss);if(vm.agentReply.isNotBlank())MoodDeck(vm.studio,!vm.aiBusy)else HistorySuggestions(vm)}
       Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(20.dp)){if(vm.agentReply.isNotBlank())AgentPlanReview(vm)else {MoodDeck(vm.studio,!vm.aiBusy);TasteControls(vm.studio,!vm.aiBusy)}}
      }else{MoodDeck(vm.studio,!vm.aiBusy);MealComposer(vm,{mode=1},dismiss);if(vm.agentReply.isNotBlank())AgentPlanReview(vm);TasteControls(vm.studio,!vm.aiBusy);HistorySuggestions(vm)}
     }
     1->{
      if(wide)Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
       Column(Modifier.weight(.65f),verticalArrangement=Arrangement.spacedBy(18.dp)){MoodDeck(vm.studio);TasteControls(vm.studio)}
       Column(Modifier.weight(1.35f),verticalArrangement=Arrangement.spacedBy(18.dp)){ManualRecipes(vm){dismiss();selectedId=null;libraryPage="add"}}
      }else{MoodDeck(vm.studio);TasteControls(vm.studio);ManualRecipes(vm){dismiss();selectedId=null;libraryPage="add"}}
     }
     2->PantryStudio(vm)
     3->WeeklyStudio(vm)
     4->WeeklyWrapScreen(vm)
    }
    SmallNote("Demo groceries & rewards · No real grocery order. Preferences and pantry stay on this device; AI requests send the details you submit. Photos are sent only when you choose to scan.")
   }
  }
 }
}

@Composable private fun MealComposer(vm:WalletViewModel,onManual:()->Unit,dismiss:()->Unit){
 StudioCard{
  Eyebrow(if(vm.agentReply.isBlank())"Your meal studio"else "Keep the good. Remix the rest.")
  StudioTitle(if(vm.agentReply.isBlank())"What sounds good?"else "Adjust your meal")
  if(vm.agentReply.isNotBlank())Text("Keep your conversation going. Your earlier budget and preferences stay in this session.",color=ShopMuted,fontSize=13.sp)
  OutlinedTextField(value=vm.preference,onValueChange={if(it.length<=1200)vm.preference=it},enabled=!vm.aiBusy,label={Text(if(vm.agentReply.isBlank())"Tell us what you're craving"else "What would you like to change?")},placeholder={Text("Something cozy, under $30. I have garlic.")},minLines=3,maxLines=5,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
  ChipRail(if(vm.agentReply.isBlank())listOf("Use my taste profile ✨","Use what I have 🥬","Surprise me")else listOf("Less heat","More pantry, less shopping","Lower the cost","Try another recipe"),enabled=!vm.aiBusy){i->
   vm.preference=if(vm.agentReply.isBlank())when(i){0->vm.studio.prompt();1->vm.studio.prompt()+" Prioritize ingredients I already own.";else->vm.studio.prompt()+" Suggest a different recipe from my recent choices."}else when(i){0->"Make this meal mild by removing chili if present. Keep my budget and dietary constraints.";1->"Use my confirmed pantry: "+vm.studio.ingredients.filter{it.id in vm.studio.owned}.joinToString{it.name}+". Keep my other constraints.";2->"Find a cheaper complete basket while keeping my dietary constraints.";else->"Choose another supported recipe with ${mealMoods[vm.studio.mood].prompt}, keeping my budget and dietary constraints."}
  }
  ShopButton(if(vm.aiBusy)"Creating your meal…"else if(vm.agentReply.isBlank())"Build my meal"else "Update my meal",enabled=!vm.aiBusy&&vm.preference.isNotBlank(),onClick={dismiss();vm.suggest()})
  if(vm.aiBusy){LinearProgressIndicator(Modifier.fillMaxWidth());SmallNote("Matching your request to recipes and whole-package prices…")}
  vm.aiError?.let{Signal("Couldn't build your meal",Danger);Text(it,color=ShopMuted,fontSize=13.sp);TextButton(onClick=onManual){Text("Choose a recipe instead")}}
  SmallNote("Gemini plans; you approve. Review dietary needs and ingredient swaps before shopping.")
 }
}
@Composable private fun HistorySuggestions(vm:WalletViewModel){
 StudioCard(tint=Mint){Eyebrow("Made for your rhythm")
  val favorite=vm.studio.favorite
  StudioTitle(if(favorite==null)"Your story starts here."else "Back to a favorite?")
  Text(if(favorite==null)"Choose a few meals and this space will learn from your choices on this device."else "${favorite.name} appears most often in your recent choices.",fontSize=14.sp,color=ShopInk)
  if(favorite!=null){TextButton(onClick={vm.preference="Something like ${favorite.name}. "+vm.studio.prompt()}){Text("Make it my kind of dinner →")};TextButton(onClick={vm.studio.clearHistory()}){Text("Clear meal history")}}
  if(vm.studio.owned.isNotEmpty())SmallNote("${vm.studio.owned.size} confirmed pantry ingredients can shape your next request.")
 }
}
@Composable private fun ManualRecipes(vm:WalletViewModel,add:()->Unit){
 Eyebrow("Pin it. Cook it. Love it.")
 SmallNote("A short list tuned by your time, diet, pantry and recent choices. Mood nudges the order; prices come from whole packages.")
 RecipePinboard(vm)
 ShopButton("+ Add a recipe",outlined=true,onClick=add)
}
