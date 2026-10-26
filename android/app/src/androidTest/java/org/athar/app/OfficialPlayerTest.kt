package org.athar.app

import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Optional network case. Report actual playback separately from provider failure handling. */
class OfficialPlayerTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun officialEmbedReportsItsOutcomeAndRotationKeepsItsView() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveYouTubeSmoke")=="true")
        val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        runBlocking{graph.session.restore();graph.settings.completeOnboarding();graph.session.login("native.reader@example.test","NativeFixture123!")}
        try{ActivityScenario.launch(MainActivity::class.java).use{scenario->
            compose.waitUntil(25_000){compose.onAllNodesWithText("مساحتك في أثر").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("المعرفة").performClick()
            compose.waitUntil(15_000){compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Official YouTube IFrame sample"))
            compose.onNodeWithText("Official YouTube IFrame sample").performClick()
            compose.waitUntil(35_000){compose.onAllNodesWithText("اضغط تشغيل لبدء الفيديو").fetchSemanticsNodes().isNotEmpty()||compose.onAllNodesWithTag("youtube-error").fetchSemanticsNodes().isNotEmpty()}
            var outcome="provider_error"
            if(compose.onAllNodesWithTag("youtube-error").fetchSemanticsNodes().isEmpty()){
                android.os.SystemClock.sleep(2000) // IFrame ready precedes its asynchronously painted controls.
                var height:Double?=null
                scenario.onActivity{activity->findWebView(activity.window.decorView)?.let{web->
                    WebView.setWebContentsDebuggingEnabled(true) // Instrumented debug test process only.
                    File(app.filesDir,"official-player-native.txt").writeText("width=${web.width},height=${web.height},measuredHeight=${web.measuredHeight},visibility=${web.visibility},layoutHeight=${web.layoutParams.height}")
                    assertEquals(ViewGroup.LayoutParams.MATCH_PARENT,web.layoutParams.height)
                    web.evaluateJavascript("JSON.stringify((function(){let e=document.getElementById('player'),r=e.getBoundingClientRect(),c=getComputedStyle(e),p=e.parentElement,pr=p.getBoundingClientRect();return {viewport:[innerWidth,innerHeight],tag:e.tagName,attrs:{height:e.getAttribute('height'),width:e.getAttribute('width'),style:e.getAttribute('style')},rect:[r.x,r.y,r.width,r.height],css:{height:c.height,width:c.width,position:c.position,display:c.display,visibility:c.visibility},parent:{tag:p.tagName,style:p.getAttribute('style'),rect:[pr.width,pr.height]},style:document.querySelector('style').textContent}})())"){File(app.filesDir,"official-player-geometry.txt").writeText(it)}
                    web.evaluateJavascript("document.getElementById('player').getBoundingClientRect().height"){height=it.toDoubleOrNull()}
                }}
                compose.waitUntil(10_000){height!=null}
                assertTrue("Official iframe must occupy at least200 CSS pixels, got $height",requireNotNull(height)>=200.0)
                capture(app,"ready")
                compose.onNodeWithTag("youtube-player").performTouchInput{click(center)}
                try {
                    compose.waitUntil(25_000){compose.onAllNodesWithText("جارٍ التشغيل").fetchSemanticsNodes().isNotEmpty()||compose.onAllNodesWithTag("youtube-error").fetchSemanticsNodes().isNotEmpty()}
                } catch(failure:Throwable) {
                    capture(app,"start-failure")
                    scenario.onActivity{activity->findWebView(activity.window.decorView)?.evaluateJavascript("JSON.stringify({state:player&&player.getPlayerState(),url:player&&player.getVideoUrl(),viewport:[innerWidth,innerHeight],frame:(function(){let r=document.getElementById('player').getBoundingClientRect();return [r.width,r.height]})()})"){File(app.filesDir,"official-player-probe.txt").writeText(it)}}
                    android.os.SystemClock.sleep(500)
                    throw failure
                }
                if(compose.onAllNodesWithTag("youtube-error").fetchSemanticsNodes().isEmpty()){
                    outcome="playing"
                    var original:WebView?=null
                    scenario.onActivity{activity->original=findWebView(activity.window.decorView);activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
                    compose.waitUntil(10_000){var landscape=false;scenario.onActivity{landscape=it.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE};landscape}
                    scenario.onActivity{activity->assertSame(original,findWebView(activity.window.decorView))}
                    compose.waitUntil(20_000){runBlocking{graph.lessons.saved(requireNotNull(graph.session.userId),4)?.positionSeconds?.let{it>0}==true}}
                }
            }
            val progress=runBlocking{graph.session.request("GET","me/lesson-progress",query=mapOf("lesson_id" to "4"))}
            assertTrue(progress.isJsonNull||progress.asJsonObject["is_complete"].asInt==0)
            File(app.filesDir,"official-player-outcome.txt").writeText(outcome)
            scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
        }}finally{runBlocking{graph.session.logout()}}
    }
    private fun capture(app:AtharApplication,name:String){compose.waitForIdle();val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());File(app.filesDir,"official-player-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun findWebView(view:View):WebView? {if(view is WebView)return view;if(view is ViewGroup)for(i in 0 until view.childCount)findWebView(view.getChildAt(i))?.let{return it};return null}
}
