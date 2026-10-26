package org.athar.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Locale
import java.util.UUID

/** Real HTTP/CAS on generated fixture account and this case's fresh UUIDs only. */
class NightConflictJourneyTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun conflictsRemainAfterRecreationAndOnlyReviewedChoicesChangeAccountData(){
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke")=="true")
        val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}).resources
        fun label(id:Int)=resources.getString(id)
        val first=UUID.randomUUID().toString();val second=UUID.randomUUID().toString();val ids=listOf(first,second)
        runBlocking{graph.session.restore();graph.session.login("native.night@example.test","NativeFixture123!");graph.settings.completeOnboarding();graph.settings.theme("light")}
        val owner=requireNotNull(graph.session.userId);val dao=graph.database.dao()
        fun body(count:Int)=JsonObject().apply{
            addProperty("night_date","2026-10-24");addProperty("timezone","UTC");addProperty("boundary_source","manual")
            addProperty("entry_type","manual");addProperty("verse_count",count);addProperty("note","Automated conflict fixture")
        }
        fun remote(id:String,count:Int,revision:Int)=runBlocking{
            graph.session.request("PUT","night-prayer/sessions/$id",mapOf("night_date" to "2026-10-24","timezone" to "UTC","boundary_source" to "manual","entry_type" to "manual","verse_count" to count,"note" to "Automated account fixture","expected_revision" to revision,"mutation_id" to UUID.randomUUID().toString()),expectedUser=owner)
        }
        fun capture(stage:String){compose.waitForIdle();val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());File(app.filesDir,"night-conflict-$language-$stage.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
        fun waitForReview(){compose.waitUntil(15_000){val node=compose.onAllNodesWithTag("night-keep-device").fetchSemanticsNodes().firstOrNull();node!=null&&!node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)&&compose.onAllNodesWithText(label(R.string.night_account_version)).fetchSemanticsNodes().isNotEmpty()}}
        try{
            remote(first,10,0)
            runBlocking{graph.nightPrayer.save(body(12),first);graph.nightPrayer.sync()}
            val before=runBlocking{dao.night(owner,first)};val queued=runBlocking{dao.sessionMutations(owner,first)}
            assertTrue(runBlocking{dao.hasNightConflict(owner,first)})
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
                compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.night_prayer)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.night_prayer)).performScrollTo().performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithTag("night-history-list").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("night-history-list").performScrollToNode(hasTestTag("night-conflict-$first"))
                compose.onNodeWithTag("night-conflict-$first").performScrollTo().performClick();waitForReview();capture("review")
                compose.onNodeWithText(label(R.string.cancel)).performClick()
                assertEquals(before,runBlocking{dao.night(owner,first)});assertEquals(queued,runBlocking{dao.sessionMutations(owner,first)})
                compose.onNodeWithTag("night-conflict-$first").performClick();waitForReview()
                remote(first,11,1)
                compose.onNodeWithTag("night-keep-device").performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.night_review_changed)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("night-keep-device").assertIsNotEnabled();capture("changed")
                assertEquals(before,runBlocking{dao.night(owner,first)});assertEquals(queued,runBlocking{dao.sessionMutations(owner,first)})
                compose.onNodeWithText(label(R.string.retry)).performClick();waitForReview()
                scenario.onActivity{it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
                compose.waitUntil(15_000){app.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
                compose.onNodeWithTag("night-keep-device").performScrollTo().assertIsDisplayed();capture("landscape")
                compose.onNodeWithTag("night-keep-device").performClick()
                compose.waitUntil(15_000){runBlocking{dao.night(owner,first)}?.dirty==false}
                val updated=runBlocking{graph.session.request("GET","night-prayer/sessions/$first",expectedUser=owner).asJsonObject}
                assertEquals(12,updated["verse_count"].asInt);assertEquals(3,updated["revision"].asInt)
                scenario.onActivity{it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
                compose.waitUntil(15_000){app.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
                remote(second,20,0);runBlocking{graph.nightPrayer.sync();graph.nightPrayer.delete(second)}
                remote(second,25,1);runBlocking{graph.nightPrayer.sync()}
                compose.onNodeWithTag("night-history-list").performScrollToNode(hasTestTag("night-conflict-$second"))
                scenario.recreate();scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
                compose.waitUntil(15_000){compose.onAllNodesWithTag("night-history-list").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("night-history-list").performScrollToNode(hasTestTag("night-conflict-$second"))
                compose.onNodeWithTag("night-conflict-$second").performScrollTo().performClick();waitForReview()
                compose.onNodeWithText(label(R.string.night_deleted_version)).performScrollTo().assertIsDisplayed();capture("deletion")
                compose.onNodeWithTag("night-keep-account").performClick()
                compose.waitUntil(15_000){runBlocking{dao.night(owner,second)}?.let{!it.deleted&&!it.dirty&&it.verseCount==25}==true}
                assertFalse(runBlocking{dao.hasNightConflict(owner,second)})
            }
        }finally{
            runBlocking{
                for(id in ids){
                    runCatching{val current=graph.session.request("GET","night-prayer/sessions/$id",expectedUser=owner).asJsonObject
                        if(!current["deleted"].asBoolean)graph.session.request("DELETE","night-prayer/sessions/$id",mapOf("expected_revision" to current["revision"].asInt,"mutation_id" to UUID.randomUUID().toString()),expectedUser=owner)}
                    dao.removeSessionMutations(owner,id)
                    graph.database.openHelper.writableDatabase.execSQL("DELETE FROM night_sessions WHERE userId=? AND id=?",arrayOf(owner,id))
                }
                graph.session.logout()
            }
        }
    }
}
