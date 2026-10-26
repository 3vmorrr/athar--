package org.athar.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.athar.app.auth.SessionRepository
import org.athar.app.core.network.RetrofitTransport
import org.athar.app.core.security.StoredSession
import org.athar.app.core.security.TokenStore
import org.junit.*
import org.junit.Assert.*
import java.time.Instant
import java.util.Locale
import java.io.File

/** Admin API -> generated DB -> physical UI. Does not substitute for the future independent Admin UI case. */
class UpcomingJourneyTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun authoritativeScheduleChangesAndCancellationReachNativeDetails() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke")=="true")
        val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}).resources
        fun label(id:Int)=resources.getString(id)
        fun refresh(){
            compose.onNodeWithTag("upcoming-list").performScrollToIndex(0)
            compose.onNodeWithText(label(R.string.upcoming_refresh)).performClick()
        }
        fun capture(stage:String) {
            android.os.SystemClock.sleep(350)
            val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            File(app.filesDir,"upcoming-$language-$stage.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        val store=object:TokenStore{var session:StoredSession?=null;override suspend fun load()=session;override suspend fun save(session:StoredSession){this.session=session};override suspend fun clear(){session=null}}
        val admin=SessionRepository(RetrofitTransport(graph.apiBaseUrl),store)
        fun change(values:Map<String,Any?>)=runBlocking {
            val current=admin.request("GET","admin/lessons/5/event").asJsonObject
            admin.request("PUT","admin/lessons/5/event",values+mapOf("expected_revision" to current["revision"].asInt))
        }
        runBlocking {
            graph.session.restore();graph.settings.completeOnboarding();graph.settings.theme("light")
            graph.session.login("native.reader@example.test","NativeFixture123!")
            admin.login("native.admin@example.test","NativeFixture123!")
        }
        try {
            val current=runBlocking{admin.request("GET","admin/lessons/5/event").asJsonObject["status"].asString}
            if(current=="published")change(mapOf("status" to "draft","replay_available" to false))
            if(current=="live")change(mapOf("status" to "cancelled","replay_available" to false))
            change(mapOf("status" to "scheduled","scheduled_at" to Instant.now().plusSeconds(900).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(),"is_live_event" to true,"replay_available" to false))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
                compose.waitUntil(25_000){compose.onAllNodesWithText(label(R.string.your_journey)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("home-upcoming").performScrollTo()
                compose.waitUntil(20_000){compose.onAllNodesWithTag("home-event-5").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("home-event-5").performScrollTo()
                capture("home")
                compose.onNodeWithTag("home-event-5").performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithTag("upcoming-event-5").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.upcoming_scheduled)).assertIsDisplayed()
                compose.onNodeWithContentDescription(label(R.string.back)).performClick()
                compose.onNodeWithTag("home-upcoming-all").performScrollTo().performClick()
                compose.waitUntil(20_000){compose.onAllNodesWithTag("upcoming-event-5").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.upcoming_scheduled)).assertIsDisplayed()
                compose.onNodeWithText(if(language=="en")"Upcoming interface fixture" else "درس اختباري قادم").assertIsDisplayed()
                compose.onNodeWithText(label(R.string.upcoming_details)).performScrollTo().performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.upcoming_details)).fetchSemanticsNodes().isEmpty()}
                compose.onNodeWithText(label(R.string.play)).assertDoesNotExist()
                capture("scheduled")
                compose.onNodeWithText(resources.getString(R.string.upcoming_course,if(language=="en")"Interface fixture course" else "دورة واجهة تجريبية")).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(if(language=="en")"Fixture topic" else "موضوع اختباري").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(resources.getString(R.string.upcoming_book_pages,10,20)).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Interface test book").performScrollTo().performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithTag("event-related-book").fetchSemanticsNodes().isEmpty()&&compose.onAllNodesWithText("Interface test book").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithContentDescription(label(R.string.back)).performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithTag("upcoming-event-5").fetchSemanticsNodes().isNotEmpty()}
                change(mapOf("scheduled_at" to "2020-01-01T00:00:00Z"))
                refresh()
                compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.upcoming_awaiting)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.upcoming_live)).assertDoesNotExist()
                compose.onNodeWithText(label(R.string.play)).assertDoesNotExist()
                change(mapOf("status" to "live"))
                refresh()
                compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.upcoming_live)).fetchSemanticsNodes().isNotEmpty()}
                capture("live")
                compose.onNodeWithText(label(R.string.play)).performScrollTo().assertIsEnabled()
                change(mapOf("status" to "published","replay_available" to true))
                refresh()
                compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.upcoming_published)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.play)).performScrollTo().assertIsEnabled()
                change(mapOf("status" to "draft","replay_available" to false))
                change(mapOf("status" to "scheduled","scheduled_at" to Instant.now().plusSeconds(900).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()))
                change(mapOf("status" to "cancelled"))
                refresh()
                compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.upcoming_cancelled)).fetchSemanticsNodes().isNotEmpty()}
                capture("cancelled")
                compose.onNodeWithText(label(R.string.play)).assertDoesNotExist()
                compose.onNodeWithContentDescription(label(R.string.back)).performClick()
                refresh()
                compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.upcoming_empty)).fetchSemanticsNodes().isNotEmpty()}
                scenario.onActivity{assertEquals(language,it.resources.configuration.locales[0].language)}
            }
        } finally {
            try { change(mapOf("status" to "scheduled","scheduled_at" to Instant.now().plusSeconds(900).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString(),"replay_available" to false)) } catch(_:Exception) { /* Retain failing fixture state for diagnosis if restoration is not applicable. */ }
            runBlocking{graph.session.logout();admin.logout()}
        }
    }
}
