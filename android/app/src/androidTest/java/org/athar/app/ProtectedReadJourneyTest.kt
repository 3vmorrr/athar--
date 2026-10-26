package org.athar.app

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.athar.app.core.data.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.io.File

/** Temporary corruption is guarded to the generated fixture partition and restored in finally. */
class ProtectedReadJourneyTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun unreadableProgressIsExplainedWithoutResettingCountersMarkersOrPrayerHistory() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke")=="true")
        val app=ApplicationProvider.getApplicationContext<AtharApplication>();val graph=app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}).resources
        fun label(id:Int)=resources.getString(id)
        fun capture(stage:String){
            compose.waitForIdle()
            val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            File(app.filesDir,"local-recovery-$language-$stage.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
        runBlocking{graph.session.restore();graph.settings.completeOnboarding();graph.settings.theme("light");graph.session.login("native.reader@example.test","NativeFixture123!")}
        val owner=requireNotNull(graph.session.userId);val dao=graph.database.dao()
        val beforeCounter=runBlocking{dao.cache(owner,"adhkar:1")}
        val beforeReading=runBlocking{dao.cache(owner,"quran-reading-position")}
        val beforeExemptions=runBlocking{dao.exemptions(owner)}
        val id=UUID.randomUUID().toString()
        val badCounter=CachedDocument(owner,"adhkar:1","damaged fixture",1)
        val badReading=CachedDocument(owner,"quran-reading-position","damaged fixture",1)
        val badExemptions=PrivateExemptions(owner,"damaged fixture")
        runBlocking{
            dao.cache(badCounter);dao.cache(badReading);dao.exemptions(badExemptions)
            dao.night(LocalNightSession(owner,id,LocalDate.now().minusDays(1).toString(),10,null,"damaged fixture",1,0,false,true,1))
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use{scenario->
                scenario.onActivity{it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
                compose.waitUntil(25_000){compose.onAllNodesWithText(label(R.string.your_journey)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.adhkar)).performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithText("Interface counter fixture").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("adhkar-entries").performScrollToNode(hasText(label(R.string.local_data_unreadable)))
                compose.onNodeWithText(label(R.string.local_data_unreadable)).assertIsDisplayed()
                capture("counter")
                compose.onNodeWithText(label(R.string.count_action)).assertDoesNotExist()
                compose.onNodeWithText(label(R.string.reset_counter)).assertDoesNotExist()
                compose.onNodeWithText(label(R.string.retry)).performScrollTo().performClick()
                assertEquals(badCounter,runBlocking{dao.cache(owner,"adhkar:1")})
                compose.onNodeWithText(label(R.string.quran)).performClick()
                compose.onNodeWithText(label(R.string.read)).performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithTag("quran-reader-list").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("quran-reader-list").performScrollToIndex(0)
                compose.waitUntil(10_000){compose.onAllNodesWithText(label(R.string.local_data_unreadable)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.local_data_unreadable)).performScrollTo().assertIsDisplayed()
                capture("reader")
                compose.onNodeWithTag("quran-reader-list").performScrollToIndex(1)
                compose.onAllNodesWithText(label(R.string.set_reading_position)).onFirst().assertIsNotEnabled()
                assertEquals(badReading,runBlocking{dao.cache(owner,"quran-reading-position")})
                compose.onNodeWithText(label(R.string.home)).performClick()
                compose.onNodeWithText(label(R.string.night_prayer)).performScrollTo().performClick()
                compose.waitUntil(15_000){compose.onAllNodesWithText(label(R.string.local_data_unreadable)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.night_add)).assertDoesNotExist()
                compose.onNodeWithText(label(R.string.night_calendar)).performClick()
                compose.onNodeWithText(label(R.string.local_data_unreadable)).assertIsDisplayed()
                capture("calendar")
                compose.onNodeWithText(label(R.string.night_tools)).performClick()
                compose.onNodeWithTag("night-tools-list").performScrollToIndex(5)
                compose.waitUntil(10_000){compose.onAllNodesWithText(label(R.string.local_data_unreadable)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(label(R.string.clear_local_exemptions)).performScrollTo().assertIsNotEnabled()
                capture("exemptions")
                assertEquals(badExemptions,runBlocking{dao.exemptions(owner)})
                assertEquals("damaged fixture",runBlocking{dao.night(owner,id)}?.payloadCipher)
            }
        }finally{
            runBlocking{
                if(beforeCounter==null)dao.removeCache(owner,"adhkar:1")else dao.cache(beforeCounter)
                if(beforeReading==null)dao.removeCache(owner,"quran-reading-position")else dao.cache(beforeReading)
                if(beforeExemptions==null)dao.deleteExemptions(owner)else dao.exemptions(beforeExemptions)
                graph.database.openHelper.writableDatabase.execSQL("DELETE FROM night_sessions WHERE userId=? AND id=?",arrayOf(owner,id))
                graph.session.logout()
            }
        }
    }
}
