package org.athar.app.learning

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.athar.app.BuildConfig
import org.json.JSONObject

private fun Context.activity(): Activity? = when(this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/** Only the official provider player is web content; Athar UI/credentials never enter it. */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun YouTubePlayer(video: String, resume: Int, retry: Int, hidden: Boolean, modifier: Modifier, event: (String,String,Int,Int,Int) -> Unit, failure: (Int) -> Unit, checkpoint: () -> Unit) {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current.lifecycle
    val language=LocalConfiguration.current.locales[0].language
    val latestEvent by rememberUpdatedState(event); val latestFailure by rememberUpdatedState(failure); val latestCheckpoint by rememberUpdatedState(checkpoint)
    var exitFullscreen by remember { mutableStateOf<(() -> Unit)?>(null) }
    BackHandler(exitFullscreen != null) { exitFullscreen?.invoke() }
    key(retry) {
        var web by remember { mutableStateOf<WebView?>(null) }
        var dead by remember { mutableStateOf(false) }
        var loaded by remember { mutableStateOf("") }
        var ready by remember { mutableStateOf(false) }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, action ->
                if (action == Lifecycle.Event.ON_PAUSE) { if (!dead) { web?.evaluateJavascript("window.atharPause&&window.atharPause()",null);web?.onPause() };latestCheckpoint() }
                if (action == Lifecycle.Event.ON_RESUME && !dead) web?.onResume()
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer);latestCheckpoint();exitFullscreen?.invoke();web?.let { (it.parent as? ViewGroup)?.removeView(it);if(!dead) { it.stopLoading();it.destroy() } };web=null }
        }
        AndroidView(modifier = modifier, factory = { ctx ->
            WebView(ctx).apply {
                // Chromium forces a zero CSS layout height for WRAP_CONTENT, even when
                // Compose measures the outer view to the requested player bounds.
                layoutParams=ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
                web=this;setBackgroundColor(Color.BLACK)
                settings.javaScriptEnabled=true;settings.domStorageEnabled=true
                settings.allowFileAccess=false;settings.allowContentAccess=false
                settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.mediaPlaybackRequiresUserGesture=true
                val origin=PlayerPolicy.origin(BuildConfig.APPLICATION_ID)
                if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                    WebViewCompat.addWebMessageListener(this,"atharEvents",setOf(origin.trimEnd('/'))) { _,message,source,main,_ ->
                        if(main&&source.scheme=="https"&&source.host==BuildConfig.APPLICATION_ID&&source.port == -1) {
                            val raw=message.data.orEmpty()
                            if(raw.length<=2048) runCatching {
                                val obj=JSONObject(raw);val id=obj.getString("video")
                                if(id==loaded&&obj.optString("type") in listOf("ready","state"))ready=true
                                val pos=PlayerPolicy.safeTime(obj.optDouble("position",0.0)) ?: return@runCatching
                                val length=PlayerPolicy.safeTime(obj.optDouble("duration",0.0)) ?: return@runCatching
                                if(PlayerPolicy.validVideo(id))latestEvent(id,obj.getString("type"),pos,length,obj.optInt("code",-1))
                            }
                        }
                    }
                } else post { latestFailure(-2) }
                webViewClient=object:WebViewClient() {
                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                        if(!request.isForMainFrame)return false
                        if(request.url.toString().startsWith(origin))return false
                        if(request.hasGesture()&&request.url.scheme=="https")runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,request.url)) }
                        return true
                    }
                    override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){if(request.isForMainFrame)latestFailure(-1)}
                    override fun onRenderProcessGone(view:WebView,detail:RenderProcessGoneDetail):Boolean { dead=true;view.destroy();latestFailure(-3);return true }
                }
                webChromeClient=object:WebChromeClient() {
                    override fun onPermissionRequest(request:PermissionRequest){request.deny()}
                    override fun onShowCustomView(view:View,callback:CustomViewCallback){
                        val activity=context.activity() ?: return callback.onCustomViewHidden()
                        if(exitFullscreen!=null)return callback.onCustomViewHidden()
                        val root=activity.window.decorView as ViewGroup
                        val frame=FrameLayout(activity).apply { setBackgroundColor(Color.BLACK);addView(view,FrameLayout.LayoutParams(-1,-1)) }
                        root.addView(frame,ViewGroup.LayoutParams(-1,-1))
                        WindowCompat.getInsetsController(activity.window,frame).hide(WindowInsetsCompat.Type.systemBars())
                        exitFullscreen={ root.removeView(frame);callback.onCustomViewHidden();WindowCompat.getInsetsController(activity.window,root).show(WindowInsetsCompat.Type.systemBars());exitFullscreen=null }
                    }
                    override fun onHideCustomView(){exitFullscreen?.invoke()}
                }
            }
        }, update = { view ->
            if(!dead) {
                view.visibility=if(hidden)View.INVISIBLE else View.VISIBLE
                if(hidden)view.evaluateJavascript("window.atharPause&&window.atharPause()",null)
                if(loaded.isEmpty()) {
                    loaded=video
                    view.loadDataWithBaseURL(PlayerPolicy.origin(BuildConfig.APPLICATION_ID), playerHtml(video,resume,language),"text/html","UTF-8",null)
                } else if(loaded!=video){ready=false;loaded=video;view.evaluateJavascript("window.atharCue&&window.atharCue(${JSONObject.quote(video)},${resume.coerceIn(0,86400)})",null)}
            }
        })
        LaunchedEffect(video,retry) { kotlinx.coroutines.delay(20_000); if(!ready)latestFailure(-1) }
    }
}

private fun playerHtml(video:String,resume:Int,language:String):String {
    require(PlayerPolicy.validVideo(video));val origin=PlayerPolicy.origin(BuildConfig.APPLICATION_ID).trimEnd('/')
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000}#player{position:absolute;inset:0;width:100vw;height:100vh;border:0}</style></head><body><div id="player"></div><script>
    let current=${JSONObject.quote(video)},start=${resume.coerceIn(0,86400)},player=null;
    function emit(type,code){if(!window.atharEvents)return;let p=0,d=0;try{let actual=player.getVideoUrl();if(actual&&new URL(actual).searchParams.get("v")!==current)return;p=player.getCurrentTime()||0;d=player.getDuration()||0}catch(e){}window.atharEvents.postMessage(JSON.stringify({video:current,type:type,position:p,duration:d,code:code||0}));}
    window.atharCue=function(id,time){current=id;start=time;if(player&&player.cueVideoById)player.cueVideoById({videoId:id,startSeconds:time});};
    window.atharPause=function(){if(player&&player.pauseVideo)player.pauseVideo();};
    function onYouTubeIframeAPIReady(){player=new YT.Player('player',{width:innerWidth,height:innerHeight,videoId:current,playerVars:{autoplay:0,playsinline:1,fs:1,hl:${JSONObject.quote(if(language=="en")"en" else "ar")},origin:${JSONObject.quote(origin)},start:start},events:{onReady:function(){player.setSize(innerWidth,innerHeight);player.cueVideoById({videoId:current,startSeconds:start});emit('ready',0);},onStateChange:function(e){emit('state',e.data);},onError:function(e){emit('error',e.data);}}});}
    addEventListener('resize',function(){if(player&&player.setSize)player.setSize(innerWidth,innerHeight)});
    setInterval(function(){if(player)emit('time',0)},1000);
    </script><script src="https://www.youtube.com/iframe_api"></script></body></html>"""
}
