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
 LaunchedEffect(mode){if(mode !in 0..3)mode=0}
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
     BrandWordmark(Modifier.weight(1f))
     TextButton(enabled=!vm.aiBusy,onClick={dismiss();recipes=saved.read();libraryPage="saved"}){Text("Saved recipes ↗")}
    }
    JournalNavigation(listOf("Build with AI","Build manually","My pantry","My week"),mode,!vm.aiBusy){dismiss();mode=it}
    if(mode in 0..1)JourneySteps(0)
    Column(Modifier.fillMaxWidth().background(Color(0xFFFFE6CE),RoundedCornerShape(10.dp)).border(1.dp,Warn.copy(alpha=.5f),RoundedCornerShape(10.dp)).padding(horizontal=12.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
     Text("! Dietary safety",color=ShopInk,fontWeight=FontWeight.Bold,fontSize=14.sp)
     Text((if(vm.studio.avoid.isBlank())"No exclusions set"else "Avoiding: ${vm.studio.avoid}")+(if(vm.studio.faith!="None")" · ${vm.studio.faith}: verify certification"else " · Check product labels"),color=ShopInk,fontSize=12.sp)
    }
    when(mode){
     0->{
      if(vm.agentReply.isNotBlank())AgentPlanReview(vm)
      MealComposer(vm,{mode=1},dismiss)
      InfoDisclosure("Preferences & dietary needs","${vm.studio.people} people · $${vm.studio.budget.toInt()} budget · ${vm.studio.minutes.toInt()} min"){TasteControls(vm.studio,!vm.aiBusy)}
      InfoDisclosure("Choose a mood",mealMoods[vm.studio.pickedMood].name){MoodDeck(vm.studio,!vm.aiBusy)}
      if(vm.studio.favorite!=null)InfoDisclosure("Your recent favorites",vm.studio.favorite?.name?:""){HistorySuggestions(vm)}
      if(vm.studio.weekDue)TextButton(onClick={dismiss();mode=3}){Text("Plan groceries for the week →")}
     }
     1->{
      InfoDisclosure("Preferences & dietary needs","${vm.studio.people} people · $${vm.studio.budget.toInt()} budget · ${vm.studio.minutes.toInt()} min"){TasteControls(vm.studio)}
      InfoDisclosure("Choose a mood",mealMoods[vm.studio.pickedMood].name){MoodDeck(vm.studio)}
      CuisinePicker(vm.studio)
      ManualRecipes(vm){dismiss();selectedId=null;libraryPage="add"}
     }
     2->PantryStudio(vm)
     3->WeeklyStudio(vm)

    }
    SmallNote("Demo prices & rewards · No real grocery order")
    InfoDisclosure("About this demo & privacy"){SmallNote("Preferences and pantry stay on this device. AI requests send the details you submit; photos are sent only when you choose to scan.")}
   }
  }
 }
}

@Composable private fun MealComposer(vm:WalletViewModel,onManual:()->Unit,dismiss:()->Unit){
 StudioCard{
  Eyebrow(if(vm.agentReply.isBlank())"Your meal studio"else "Keep the good. Remix the rest.")
  StudioTitle(if(vm.agentReply.isBlank())"What sounds good?"else "Adjust your meal")
  if(vm.agentReply.isNotBlank())Text("Keep your conversation going. Your earlier budget and preferences stay in this session.",color=ShopMuted,fontSize=13.sp)
  OutlinedTextField(value=vm.preference,onValueChange={if(it.length<=1200)vm.preference=it},enabled=!vm.aiBusy,label={Text(if(vm.agentReply.isBlank())"Tell us what you're craving"else "What would you like to change?")},placeholder={Text("Something cozy, under $30. I have garlic.")},minLines=2,maxLines=5,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth())
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
 StudioCard(tint=androidx.compose.ui.graphics.Color.White){Eyebrow("Made for your rhythm")
  val favorite=vm.studio.favorite
  StudioTitle(if(favorite==null)"Your story starts here."else "Back to a favorite?")
  Text(if(favorite==null)"Choose a few meals and this space will learn from your choices on this device."else "${favorite.name} appears most often in your recent choices.",fontSize=14.sp,color=ShopInk)
  if(favorite!=null){TextButton(onClick={vm.preference="Something like ${favorite.name}. "+vm.studio.prompt()}){Text("Make it my kind of dinner →")};TextButton(onClick={vm.studio.clearHistory()}){Text("Clear meal history")}}
  if(vm.studio.owned.isNotEmpty())SmallNote("${vm.studio.owned.size} confirmed pantry ingredients can shape your next request.")
 }
}
@Composable private fun ManualRecipes(vm:WalletViewModel,add:()->Unit){

 RecipePinboard(vm)
 ShopButton("+ Add a recipe",outlined=true,onClick=add)
}
