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

/** Real backend 422, retained outbox and explicit repair on generated fixture records only. */
class NightRejectionJourneyTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun rejectedOperationSurvivesCancelRecreationRetryAndExplicitCorrection(){
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke")=="true")
        val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}).resources
        fun label(id:Int)=resources.getString(id)
        val id=UUID.randomUUID().toString();val healthy=UUID.randomUUID().toString();val ids=listOf(id,healthy)
        runBlocking{graph.session.restore();graph.session.login("native.night@example.test","NativeFixture123!");graph.settings.completeOnboarding();graph.settings.theme("light")}
        val owner=requireNotNull(graph.session.userId);val dao=graph.database.dao()
        fun body(count:Int)=JsonObject().apply{
            addProperty("night_date","2026-10-24");addProperty("timezone","UTC");addProperty("boundary_source","manual")
            addProperty("entry_type","manual");addProperty("verse_count",count);addProperty("note","Automated rejection fixture")
        }
        fun capture(stage:String){compose.waitForIdle();InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(400);val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());File(app.filesDir,"night-rejection-$language-$stage.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
        fun openReview(){
            compose.onNodeWithTag("night-history-list").performScrollToNode(hasTestTag("night-rejection-$id"))
            compose.onNodeWithTag("night-rejection-$id").performScrollTo().performClick()
            compose.waitUntil(15_000){compose.onAllNodesWithTag("night-rejection-retry").fetchSemanticsNodes().isNotEmpty()}
        }
        try{
            runBlocking{graph.nightPrayer.save(body(100001),id);graph.nightPrayer.save(body(20),healthy);graph.nightPrayer.sync(owner)}
            val bytes=runBlocking{dao.sessionMutations(owner,id)};assertEquals(422,bytes.single().rejectedStatus)
            assertFalse(runBlocking{dao.night(owner,healthy)}!!.dirty)
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
                compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.night_prayer)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.night_prayer)).performScrollTo().performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithTag("night-history-list").fetchSemanticsNodes().isNotEmpty()}
                openReview();compose.onNodeWithText(label(R.string.night_rejection_invalid)).assertExists();capture("review")
                compose.onNodeWithText(label(R.string.cancel)).performScrollTo().performClick()
                assertEquals(bytes,runBlocking{dao.sessionMutations(owner,id)})
                scenario.recreate();scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
                compose.waitUntil(15_000){compose.onAllNodesWithTag("night-history-list").fetchSemanticsNodes().isNotEmpty()}
                openReview();compose.onNodeWithTag("night-rejection-retry").performScrollTo().performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithTag("night-history-list").fetchSemanticsNodes().isNotEmpty()&&runBlocking{dao.hasNightRejection(owner,id)}}
                assertEquals(bytes,runBlocking{dao.sessionMutations(owner,id)})
                openReview()
                scenario.onActivity{it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
                compose.waitUntil(15_000){app.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
                compose.onNodeWithTag("night-rejection-repair").performScrollTo().assertIsDisplayed();capture("landscape")
                compose.onNodeWithTag("night-rejection-repair").performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithTag("night-entry-fields").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("night-entry-fields").performScrollToNode(hasText(label(R.string.verse_count)))
                compose.onNodeWithText(label(R.string.verse_count)).performScrollTo().performTextReplacement("25")
                compose.onNodeWithText(label(R.string.save)).performClick()
                compose.waitUntil(15_000){runBlocking{dao.night(owner,id)}?.let{!it.dirty&&it.verseCount==25}==true}
                val remote=runBlocking{graph.session.request("GET","night-prayer/sessions/$id",expectedUser=owner).asJsonObject}
                assertEquals(25,remote["verse_count"].asInt);assertTrue(runBlocking{dao.sessionMutations(owner,id)}.isEmpty())
                capture("repaired")
                scenario.onActivity{it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
                compose.waitUntil(15_000){app.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
            }
        }finally{
            runBlocking{
                for(record in ids){
                    runCatching{val current=graph.session.request("GET","night-prayer/sessions/$record",expectedUser=owner).asJsonObject
                        if(!current["deleted"].asBoolean)graph.session.request("DELETE","night-prayer/sessions/$record",mapOf("expected_revision" to current["revision"].asInt,"mutation_id" to UUID.randomUUID().toString()),expectedUser=owner)}
                    dao.removeSessionMutations(owner,record)
                    graph.database.openHelper.writableDatabase.execSQL("DELETE FROM night_sessions WHERE userId=? AND id=?",arrayOf(owner,record))
                }
                graph.session.logout()
            }
        }
    }
}
