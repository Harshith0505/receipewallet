package com.recipewallet.app

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PantryStudio(vm:WalletViewModel){
 val state=vm.studio;val context=LocalContext.current;val scope=rememberCoroutineScope()
 var localError by remember { mutableStateOf<String?>(null) }
 var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->pendingPhoto=uri}
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()){bitmap->if(bitmap!=null)state.scan(bitmap)}
 LaunchedEffect(pendingPhoto){val uri=pendingPhoto?:return@LaunchedEffect
  scope.launch {try{
   val bitmap=withContext(Dispatchers.IO){
    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
    check(bounds.outWidth>0&&bounds.outHeight>0){"This image couldn't be opened."}
    var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>1400)sample*=2
    val options=BitmapFactory.Options().apply{inSampleSize=sample}
    context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}?:error("This image couldn't be opened.")
   };state.scan(bitmap)
  }catch(e:Exception){localError=e.message?:"Choose another photo."}finally{pendingPhoto=null}}
 }
 StudioCard(tint=Mint){
  Eyebrow("Pantry lens")
  StudioTitle("Meet your ingredients.")
  Text("Snap your fridge or pantry. Review what Gemini sees, then decide what belongs in your pantry.",color=ShopInk,fontSize=15.sp)
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
   Button(enabled=!state.scanBusy,onClick={runCatching{camera.launch(null)}.onFailure{localError="Camera unavailable. Choose a photo instead."}},modifier=Modifier.weight(1f)){Text("Take photo")}
   OutlinedButton(enabled=!state.scanBusy,onClick={gallery.launch("image/*")},modifier=Modifier.weight(1f)){Text("Choose photo")}
  }
  SmallNote("Choosing a photo sends a resized copy to Gemini for recognition. The latest photo stays in app storage until you remove it. Only supported catalog ingredients can be detected.")
  localError?.let { Text(it,color=Danger) }
  val photo=remember(state.photoPath,state.scanBusy){state.photoPath?.let{BitmapFactory.decodeFile(it)}}
  if(photo!=null){Image(photo.asImageBitmap(),contentDescription="Your latest pantry photo",modifier=Modifier.fillMaxWidth().height(210.dp),contentScale=ContentScale.Crop);TextButton(enabled=!state.scanBusy,onClick={state.clearPhoto()}){Text("Remove photo")}}
  if(state.scanBusy){LinearProgressIndicator(Modifier.fillMaxWidth());Text("Looking for familiar ingredients…",color=ShopInk)}
  state.scanError?.let{Text(it,color=Danger,fontSize=13.sp)}
 }
 if(state.scanIds.isNotEmpty())StudioCard(tint=Peach){
  Signal("? Photo suggestions · not confirmed",Warn)
  StudioTitle("Did we get these right?")
  SmallNote("Tap to exclude mistakes. Photos cannot establish how much you have, food safety, or freshness.")
  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   state.ingredients.filter{it.id in state.scanIds}.forEach { item->FilterChip(selected=item.id in state.scanSelected,onClick={state.toggleScan(item.id)},label={Text(item.name)},leadingIcon={Text(if(item.id in state.scanSelected)"✓"else "+")}) }
  }
  ShopButton("Confirm ${state.scanSelected.size} pantry items",enabled=state.scanSelected.isNotEmpty(),onClick={state.confirmScan()})
  TextButton(onClick={state.discardScan()}){Text("Discard suggestions")}
 }
 StudioCard{
  Eyebrow("Confirmed by you")
  StudioTitle("My pantry · ${state.owned.size} items")
  SmallNote("Green checks mean you confirmed the ingredient. Verify you have enough for each meal. Tap to add or remove; changes affect your next basket, not an already approved checkout.")
  FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   state.ingredients.forEach{item->FilterChip(selected=item.id in state.owned,onClick={state.toggleOwned(item.id)},label={Text(item.name)},leadingIcon={Text(if(item.id in state.owned)"✓"else "+")})}
  }
 }
 StudioTitle("What can I make?")
 SmallNote("Ranked by the share of ingredients you have. Dietary and time filters from your meal studio still apply.")
 val matches=state.rankedRecipes().sortedByDescending { r->r.ingredients.count { it.id in state.owned }.toFloat()/r.ingredients.size }
 if(matches.isEmpty())SmallNote("No supported recipes match your time and dietary preferences.")
 matches.forEachIndexed{i,r->RecipeTile(r,state,i){vm.choose(r.id)}}
}

@Composable internal fun WeeklyStudio(vm:WalletViewModel){
 val state=vm.studio
 StudioCard(tint=Lilac){
  Eyebrow("Your weekly ritual · week of ${state.currentWeek}")
  StudioTitle(if(state.weekDue)"Make room for a good week."else "Five dinners. One clear list.")
  Text("Your mood, recent choices and confirmed pantry help shape a five-day plan. Swap any dinner before making your list.",color=ShopInk,fontSize=14.sp)
  SmallNote("${state.people} people · ${state.minutes.toInt()} min per meal · ${mealMoods[state.mood].name} · ${if(state.vegetarian)"Vegetarian"else "Any diet"}")
  ShopButton(if(state.weekBusy)"Planning your week…"else "Let AI plan my week",enabled=!state.weekBusy,onClick={state.buildWeek()})
  if(state.weekBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
  state.weekError?.let{Text(it,color=Danger,fontSize=13.sp)}
  TextButton(enabled=!state.weekBusy,onClick={state.buildLocalWeek()}){Text("Build a local catalog plan instead")}
  Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Switch(checked=state.autoWeek,onCheckedChange={state.updateAutoWeek(it)});Text("Auto-plan on my first visit each week",modifier=Modifier.weight(1f).padding(start=10.dp),color=ShopInk,fontSize=13.sp)}
  Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Switch(checked=state.autoCart,onCheckedChange={state.updateAutoCart(it)});Text("Automatically prepare my weekly grocery cart",modifier=Modifier.weight(1f).padding(start=10.dp),color=ShopInk,fontSize=13.sp)}
  SmallNote("Builds a saved cart draft when your weekly plan is generated or edited. Review quantities and pantry stock before shopping; no automatic payment.")
  SmallNote("When enabled, your first visit each week sends your taste profile, recent recipe choices and pantry IDs to the backend. No background purchases or notifications.")
 }
 if(state.weeklyIds.isNotEmpty())ShopButton("Prepare weekly grocery cart",onClick={state.prepareWeeklyCart()})
 state.groceryCart?.let{cart->StudioCard(tint=Peach){
  Eyebrow("Saved shopping cart · ${state.cartPeople} people")
  StudioTitle("Groceries, planned ahead")
  cart.ingredients.forEach{item->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(item.name,color=ShopInk,modifier=Modifier.weight(1f));Text("${item.quantity} ${item.unit}",color=ShopInk)}}
  SmallNote("Includes pantry items until you confirm enough stock for the whole week. This draft is saved on this tablet. Shop individual dinners below.")
 }}
 if(state.weeklyIds.isNotEmpty()){
  Signal(if(state.weekSource=="gemini")"✨ AI arranged · edited by you"else "Local catalog plan · no AI used",if(state.weekSource=="gemini")Good else Warn)
  state.weeklyIds.forEachIndexed{index,id->
   val recipe=vm.catalog.recipes.first{it.id==id}
   StudioCard(tint=if(index%2==0)Mint else Peach){
    Eyebrow(listOf("Monday","Tuesday","Wednesday","Thursday","Friday")[index])
    Text("${recipe.emoji}  ${recipe.name}",fontSize=21.sp,color=ShopInk)
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
     TextButton(onClick={state.swapDay(index)}){Text("↻ Swap dinner")}
     TextButton(onClick={vm.choose(recipe.id)}){Text("Shop this meal →")}
    }
   }
  }
  val recipe=state.weeklyRecipe()!!
  StudioCard{
   Eyebrow("The big picture")
   StudioTitle("One weekly grocery list")
   SmallNote("Quantities combine all five dinners for ${state.people} people. Pantry items remain in this list until you verify the total quantity; a photo cannot count stock.")
   recipe.ingredients.forEach { item->
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(item.name,color=ShopInk,modifier=Modifier.weight(1f));Text("${item.quantity} ${item.unit}",color=ShopInk)}
    if(item.id in state.owned)Signal("✓ In pantry · check weekly quantity")
   }
   val quote=vm.catalog.stores.map{s->packageQuote(recipe,recipe.ingredients.map{it.id}.toSet(),s,vm.catalog.cards.first(),vm.catalog.productOffers,state.people,"pickup",false)}.filter{it.complete}.minByOrNull{it.charge}
   if(quote!=null){Signal("Combined package estimate · ${quote.store.name}");Text(money(quote.charge),fontSize=32.sp,color=ShopInk);SmallNote("Whole packages, pickup fees and demo tax. Before pantry deductions and future rewards. Shop each dinner above; combined weekly checkout is not available.")}
   else SmallNote("No single demo store can supply the full week. Shop each meal separately.")
  }
 }
}
