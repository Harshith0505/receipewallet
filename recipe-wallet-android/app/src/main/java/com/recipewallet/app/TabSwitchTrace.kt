package com.recipewallet.app

/** Optional debug probe: selection callback to the first draw of that destination. */
internal object TabSwitchTrace {
 var listener:((Int,Long)->Unit)?=null
 private var pending=-1
 private var started=0L
 fun selected(mode:Int){
  if(!BuildConfig.DEBUG||listener==null)return
  pending=mode;started=System.nanoTime()
 }
 fun drawn(mode:Int){
  if(!BuildConfig.DEBUG||pending!=mode)return
  val elapsed=(System.nanoTime()-started)/1_000_000
  pending=-1
  listener?.invoke(mode,elapsed)
 }
 fun clear(){listener=null;pending=-1}
}
