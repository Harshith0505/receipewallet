package com.recipewallet.app

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

// Approximate store coordinates; navigation uses the catalog's full street addresses.
private val storeCoordinates=mapOf("market" to (47.6615 to -122.2985),"fresh" to (47.6615 to -122.3179),"pantry" to (47.6752 to -122.3263))
@SuppressLint("SetJavaScriptEnabled")
@Composable internal fun ShopMap(quotes:List<Quote>,selected:String,onSelect:(String)->Unit){
 val onPick by rememberUpdatedState(onSelect)
 val data=JSONArray(quotes.mapIndexed{index,q->val coords=storeCoordinates[q.store.id]?:when(index){0->47.6615 to -122.2985;1->47.6615 to -122.3179;else->47.6752 to -122.3263};JSONObject().put("id",q.store.id).put("name",q.store.name).put("lat",coords.first).put("lng",coords.second).put("price",if(q.complete)money(q.charge)else "Unavailable").put("complete",q.complete)})
 var ready by remember{mutableStateOf(false)}
 StudioCard(tint=Mint){
  Eyebrow("Explore your basket's neighborhood")
  StudioTitle("A good meal starts nearby.")
  SmallNote("Use two fingers to pan or pinch; tap a price pin. Approximate Seattle demo pins; prices and distances are sample data, not your current location.")
  AndroidView(modifier=Modifier.fillMaxWidth().height(330.dp),factory={context->WebView(context).apply{
   settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.domStorageEnabled=false
   settings.userAgentString=settings.userAgentString+" RecipeWalletDemo/1.0"
   webViewClient=object:WebViewClient(){
    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
     val uri=request.url
     if(uri.host=="recipewallet.local"&&uri.path=="/select")uri.getQueryParameter("id")?.takeIf{id->quotes.any{it.store.id==id}}?.let{onPick(it)}
     return true
    }
    override fun onPageFinished(view:WebView,url:String){ready=true}
   }
   setOnTouchListener{view,event->view.parent?.requestDisallowInterceptTouchEvent(event.pointerCount>1);false}
   loadDataWithBaseURL("https://recipewallet.local/",mapHtml(data.toString(),selected),"text/html","UTF-8",null)
  }},update={view->if(ready)view.evaluateJavascript("window.selectStore && window.selectStore(${JSONObject.quote(selected)},${data});",null)},onRelease={it.stopLoading();it.destroy()})
  SmallNote("Live map requires internet · Store cards below always work · © OpenStreetMap contributors")
 }
}
private fun mapHtml(data:String,selected:String)="""<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"><style>html,body,#map{height:100%;margin:0;background:#dcecdf;font:15px sans-serif}.pin{border-radius:30px;padding:10px 14px;background:#fff;border:2px solid #254637;color:#254637;font-weight:bold;white-space:nowrap;box-shadow:0 3px 10px #0002}.active{background:#f7cd58;border:3px solid #254637}.missing{background:#eee;color:#9e3737}#status{position:absolute;bottom:25px;left:10px;z-index:900;background:white;padding:8px;border-radius:9px;max-width:80%}</style></head><body><div id="map"></div><div id="status">Loading map… Store cards remain available below.</div><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
if(window.L){const map=L.map('map',{scrollWheelZoom:false}).setView([47.668,-122.313],13);const tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'}).addTo(map);tiles.on('load',()=>document.getElementById('status').style.display='none');tiles.on('tileerror',()=>{document.getElementById('status').style.display='block';document.getElementById('status').textContent='Map tiles unavailable. Use the store cards below.'});const markers={};let rows=$data;rows.forEach(q=>{const m=L.marker([q.lat,q.lng]).addTo(map);m.on('click',()=>{window.location.href='https://recipewallet.local/select?id='+encodeURIComponent(q.id)});markers[q.id]=m});window.selectStore=(id,next)=>{rows=next;rows.forEach(q=>{markers[q.id].setIcon(L.divIcon({className:'',html:'<div class="pin '+(q.id===id?'active ':'')+(!q.complete?'missing':'')+'">'+q.price+'</div>',iconSize:[110,44],iconAnchor:[55,22]}));markers[q.id].bindTooltip(q.name)});};selectStore(${JSONObject.quote(selected)},rows);}
</script></body></html>"""
