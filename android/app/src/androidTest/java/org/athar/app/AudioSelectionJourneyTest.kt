package org.athar.app

import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.util.Locale
import java.io.File

class AudioSelectionJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun listeningSelectionSurvivesRecreationAndRadioDoesNotShowReciterControls() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke") == "true")
        val app = ApplicationProvider.getApplicationContext<AtharApplication>()
        val graph = app.graph
        Assert.assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
        fun label(id: Int) = resources.getString(id)
        val surahName = runBlocking { graph.quran.data().index.surahs[1].name }
        runBlocking {
            graph.session.restore(); graph.settings.completeOnboarding(); graph.settings.theme("light")
            graph.session.login("native.reader@example.test", "NativeFixture123!")
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(25_000) { compose.onAllNodesWithText(label(R.string.your_journey)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(label(R.string.quran)).performClick()
                compose.onNodeWithText(label(R.string.listen)).performClick()
                compose.onNodeWithTag("audio-surah").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("2. $surahName").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("2. $surahName").performClick()
                compose.onNodeWithText(label(R.string.one_ayah)).performClick()
                compose.onNodeWithText("+").performClick()
                repeat(3) {
                    scenario.recreate()
                    compose.waitUntil(15_000) { runCatching { compose.onNodeWithTag("audio-surah").assertTextContains(surahName) }.isSuccess }
                    compose.onNodeWithText(label(R.string.ayah) + " 2").assertIsDisplayed()
                    compose.onNodeWithText(label(R.string.play)).assertIsNotEnabled()
                }
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                compose.waitUntil(10_000) {
                    var landscape = false
                    scenario.onActivity { landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                    landscape
                }
                compose.onNodeWithText("+").performScrollTo().performClick()
                compose.onNodeWithText(label(R.string.ayah) + " 3").assertIsDisplayed()
                compose.onNodeWithText(label(R.string.play)).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
                android.os.SystemClock.sleep(350)
                val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                File(app.filesDir, "audio-$language-landscape.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                compose.waitUntil(10_000) {
                    var portrait = false
                    scenario.onActivity { portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
                    portrait
                }
                compose.onNodeWithText(label(R.string.quran_tools)).performClick()
                compose.onNodeWithText(label(R.string.radio)).performClick()
                compose.onNodeWithTag("audio-reciter").assertDoesNotExist()
                compose.onNodeWithTag("audio-surah").assertDoesNotExist()
            }
        } finally { runBlocking { graph.session.logout() } }
    }
}
