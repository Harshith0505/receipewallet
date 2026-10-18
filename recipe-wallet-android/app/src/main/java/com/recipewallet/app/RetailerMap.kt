package com.recipewallet.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable internal fun RetailerMap(stores:List<RetailerStore>,selectedId:String,enabled:Boolean,onSelect:(String)->Unit){
 val currentStores by rememberUpdatedState(stores)
 val canSelect by rememberUpdatedState(enabled)
 val onPick by rememberUpdatedState(onSelect)
 val points=stores.filter{it.hasCoordinates}
 val data=JSONArray(points.map{store->JSONObject().put("id",store.id).put("name",store.name).put("lat",store.latitude).put("lng",store.longitude).put("label",(stores.indexOf(store)+1).toString())})
 var ready by remember{mutableStateOf(false)}
 var directionsError by remember{mutableStateOf(false)}
 val context=LocalContext.current
 StudioCard{
  Eyebrow("Nearby store map")
  StudioTitle("A good meal starts nearby.")
  SmallNote("Tap a numbered pin to choose a store. Changing stores refreshes products and clears your package review. Pinch with two fingers to zoom.")
  if(points.isNotEmpty()){
   AndroidView(modifier=Modifier.fillMaxWidth().height(300.dp).clipToBounds().semantics{contentDescription="Map of nearby grocery stores"},factory={ctx->WebView(ctx).apply{
    settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.domStorageEnabled=false;settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
    webViewClient=object:WebViewClient(){
     override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
      val uri=request.url
      if(canSelect&&uri.scheme=="https"&&uri.host=="recipewallet.local"&&uri.path=="/select")uri.getQueryParameter("id")?.takeIf{id->currentStores.any{it.id==id&&it.hasCoordinates}}?.let{onPick(it)}
      return true
     }
     override fun onPageFinished(view:WebView,url:String){ready=true}
    }
    setOnTouchListener{view,event->view.parent?.requestDisallowInterceptTouchEvent(event.pointerCount>1);false}
    loadDataWithBaseURL("https://recipewallet.local/",retailerMapHtml(data.toString(),selectedId,enabled),"text/html","UTF-8",null)
   }},update={view->if(ready)view.evaluateJavascript("window.updateStores && window.updateStores(${data},${JSONObject.quote(selectedId)},$enabled);",null)},onRelease={it.stopLoading();it.destroy()})
  }else SmallNote("Map coordinates were not provided for these stores. Their addresses and selection buttons are available below.")
  SmallNote("Store locations supplied by Kroger · Map requires internet · © OpenStreetMap contributors. Your current location is not accessed.")
  stores.forEachIndexed{index,store->
   ShopButton("${index+1}. ${store.name}"+if(store.id==selectedId)" · selected"else "",outlined=store.id!=selectedId,enabled=enabled,onClick={onPick(store.id)})
   SmallNote(store.address)
  }
  stores.firstOrNull{it.id==selectedId}?.let{store->
   ShopButton("Directions to selected store",outlined=true,onClick={
    directionsError=false
    try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&destination="+Uri.encode(store.address))))}catch(_:android.content.ActivityNotFoundException){directionsError=true}
   })
   if(directionsError)SmallNote("No maps app or browser is available. Use the store address above.")
  }
 }
}

private fun retailerMapHtml(data:String,selected:String,enabled:Boolean):String {
 val safeData=data.replace("<","\\u003c")
 val safeSelected=JSONObject.quote(selected).replace("<","\\u003c")
 return """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"><style>html,body,#map{height:300px;min-height:300px;width:100%;margin:0;background:#fff7e9;font:15px sans-serif}.pin{border-radius:50%;width:38px;height:38px;display:grid;place-items:center;background:white;border:2px solid #503542;color:#503542;font-weight:bold;box-shadow:0 3px 8px #0002}.active{background:#f3d58a;border-width:3px}#status{position:absolute;bottom:25px;left:10px;z-index:900;background:white;padding:8px;border-radius:9px;max-width:80%}</style></head><body><div id="map"></div><div id="status">Loading map… Store buttons remain available below.</div><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>
const mapNotice=document.getElementById('status');
if(!window.L){mapNotice.textContent='Map unavailable. Use the store buttons below.';}else{
 const map=L.map('map',{scrollWheelZoom:false});const layer=L.layerGroup().addTo(map);let boundsKey='';
 const tiles=L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'&copy; OpenStreetMap'}).addTo(map);
 tiles.on('load',()=>mapNotice.style.display='none');tiles.on('tileerror',()=>{mapNotice.style.display='block';mapNotice.textContent='Map tiles unavailable. Use the store buttons below.'});
 window.updateStores=(rows,selected,enabled)=>{
  layer.clearLayers();
  rows.forEach(row=>{const label=document.createElement('div');label.className='pin '+(row.id===selected?'active':'');label.textContent=row.label;const tooltip=document.createElement('span');tooltip.textContent=row.name;
   const marker=L.marker([row.lat,row.lng],{icon:L.divIcon({className:'',html:label,iconSize:[42,42],iconAnchor:[21,21]})}).bindTooltip(tooltip).addTo(layer);
   marker.on('click',()=>{if(enabled)window.location.href='https://recipewallet.local/select?id='+encodeURIComponent(row.id)});
  });
  const nextKey=JSON.stringify(rows.map(r=>[r.id,r.lat,r.lng]));if(nextKey!==boundsKey&&rows.length){map.fitBounds(rows.map(r=>[r.lat,r.lng]),{padding:[32,32],maxZoom:15});boundsKey=nextKey;}setTimeout(()=>map.invalidateSize({pan:false}),0);
 };
 if(window.ResizeObserver)new ResizeObserver(()=>map.invalidateSize({pan:false})).observe(document.getElementById('map'));
 updateStores($safeData,$safeSelected,$enabled);
}
</script></body></html>"""
}
