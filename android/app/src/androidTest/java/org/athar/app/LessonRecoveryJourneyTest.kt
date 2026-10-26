package org.athar.app

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.athar.app.core.data.ProtectedRead
import org.athar.app.learning.WatchViewModel
import org.athar.app.core.data.CachedDocument
import org.junit.*
import org.junit.Assert.*
import java.util.Locale

/** Temporary ciphertext only in the generated fixture partition; restores previous bytes. */
class LessonRecoveryJourneyTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun corruptedWatchCheckpointSurvivesRecreationAndExplicitRetryRecovers(){
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke")=="true")
        val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}).resources
        fun label(id:Int)=resources.getString(id)
        runBlocking{graph.session.restore();graph.settings.completeOnboarding();graph.settings.theme("light");graph.session.login("native.reader@example.test","NativeFixture123!")}
        val owner=requireNotNull(graph.session.userId);val dao=graph.database.dao()
        val original=runBlocking{dao.cache(owner,"lesson-progress:2")}
        val damaged=CachedDocument(owner,"lesson-progress:2","damaged fixture checkpoint",1)
        val remote=runBlocking{graph.session.request("GET","me/lesson-progress",query=mapOf("lesson_id" to "2")).toString()}
        runBlocking{dao.cache(damaged)}
        try{
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
                compose.waitUntil(25_000){compose.onAllNodesWithText(label(R.string.your_journey)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Interface test lesson 2"))
                compose.onNodeWithText("Interface test lesson 2").performClick()
                compose.waitUntil(20_000){compose.onAllNodesWithTag("watch-local-recovery").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.local_data_unreadable)).assertIsDisplayed()
                compose.onNodeWithTag("youtube-player").assertDoesNotExist();compose.onNodeWithTag("watch-actions").assertDoesNotExist()
                scenario.recreate();scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
                compose.waitUntil(20_000){compose.onAllNodesWithText(label(R.string.local_data_unreadable)).fetchSemanticsNodes().isNotEmpty()}
                assertEquals(damaged,runBlocking{dao.cache(owner,"lesson-progress:2")})
                // Explicitly restore only the fixture bytes, then retry the retained read.
                runBlocking{if(original==null)dao.removeCache(owner,"lesson-progress:2")else dao.cache(original)}
                compose.onNodeWithText(label(R.string.retry)).performClick()
                compose.waitUntil(20_000){compose.onAllNodesWithTag("watch-title").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("watch-local-recovery").assertDoesNotExist()
                compose.onNodeWithTag("watch-details").performScrollToNode(hasTestTag("watch-actions"))
                compose.onNodeWithTag("watch-actions").assertExists()
                assertEquals(remote,runBlocking{graph.session.request("GET","me/lesson-progress",query=mapOf("lesson_id" to "2")).toString()})
            }
            // Keep the ready old player composition during an ordinary metadata switch.
            val store=androidx.lifecycle.ViewModelStore()
            lateinit var model:WatchViewModel
            InstrumentationRegistry.getInstrumentation().runOnMainSync{
                model=androidx.lifecycle.ViewModelProvider(store,object:androidx.lifecycle.ViewModelProvider.Factory{
                    @Suppress("UNCHECKED_CAST") override fun<T:androidx.lifecycle.ViewModel>create(modelClass:Class<T>):T=WatchViewModel(graph,2) as T
                })[WatchViewModel::class.java]
            }
            try{
                runBlocking{withTimeout(20_000){while(model.state.value.loading)delay(30)}}
                assertTrue(model.state.value.localProgress is ProtectedRead.Ready)
                InstrumentationRegistry.getInstrumentation().runOnMainSync{
                    model.select(1)
                    assertTrue(model.state.value.loading)
                    assertTrue(model.state.value.localProgress is ProtectedRead.Ready)
                    assertEquals(2,model.state.value.lesson!!["id"].asInt)
                }
                runBlocking{withTimeout(20_000){while(model.state.value.loading)delay(30)}}
                assertEquals(1,model.state.value.lesson!!["id"].asInt)
            }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync{store.clear()}}
        }finally{
            runBlocking{if(original==null)dao.removeCache(owner,"lesson-progress:2")else dao.cache(original);graph.session.logout()}
        }
    }
}
