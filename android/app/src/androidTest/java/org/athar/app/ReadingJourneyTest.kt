package org.athar.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Locale
import kotlin.math.abs

class ReadingJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun explicitResumeMarkerSurvivesRecreationWithoutInferringReadingActivity() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke") == "true")
        val app = ApplicationProvider.getApplicationContext<AtharApplication>()
        val graph = app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
        fun label(id: Int) = resources.getString(id)
        runBlocking {
            graph.session.restore(); graph.settings.completeOnboarding(); graph.settings.theme("light")
            graph.session.login("native.reader@example.test", "NativeFixture123!")
            graph.reading.refresh()
        }
        val before = runBlocking { graph.session.request("GET", "home").asJsonObject["activity"].asJsonObject["quran_sessions"].asString }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(25_000) { compose.onAllNodesWithText(label(R.string.your_journey)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(label(R.string.quran)).performClick()
                compose.onNodeWithText(label(R.string.read)).performClick()
                compose.waitUntil(20_000) { compose.onAllNodesWithTag("quran-reader-list").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("quran-reader-list").performScrollToNode(hasTestTag("quran-ayah-4"))
                compose.onNode(hasText(label(R.string.set_reading_position)) and hasAnyAncestor(hasTestTag("quran-ayah-4"))).performClick()
                compose.waitUntil(15_000) { runBlocking { graph.session.request("GET", "quran/position").let { !it.isJsonNull && it.asJsonObject["surah"].asInt == 1 && it.asJsonObject["ayah"].asInt == 4 } } }
                compose.onNodeWithTag("quran-reader-list").performScrollToNode(hasTestTag("quran-ayah-1"))
                compose.onNodeWithContentDescription(label(R.string.resume_reading)).performClick()
                compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("quran-ayah-4").assertIsDisplayed() }.isSuccess }
                val top = compose.onNodeWithTag("quran-reader-list").fetchSemanticsNode().boundsInRoot.top
                assertTrue("Resume must scroll to the saved ayah", abs(compose.onNodeWithTag("quran-ayah-4").fetchSemanticsNode().boundsInRoot.top - top) < 64f)
                repeat(3) {
                    scenario.recreate()
                    compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("quran-ayah-4").assertIsDisplayed() }.isSuccess }
                }
                assertEquals(4, runBlocking { graph.reading.saved(requireNotNull(graph.session.userId))!!.ayah })
                android.os.SystemClock.sleep(350) // Let the platform window transition finish before display capture.
                val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                File(app.filesDir, "reader-$language-resume.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertEquals(before, runBlocking { graph.session.request("GET", "home").asJsonObject["activity"].asJsonObject["quran_sessions"].asString })
            }
        } finally { runBlocking { graph.session.logout() } }
    }
}
