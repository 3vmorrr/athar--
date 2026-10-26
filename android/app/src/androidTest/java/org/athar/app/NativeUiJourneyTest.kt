package org.athar.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.athar.app.auth.AuthState
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/** Real HTTP against an explicitly selected, disposable local fixture schema. */
class NativeUiJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun localizedHomeCatalogBackAndBookmarkPersistThroughRealApi() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke") == "true")
        val app = ApplicationProvider.getApplicationContext<AtharApplication>()
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        InstrumentationRegistry.getInstrumentation().runOnMainSync { androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(language)) }
        val configuration = android.content.res.Configuration(app.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(language)) }
        val resources = app.createConfigurationContext(configuration).resources
        fun label(id: Int) = resources.getString(id)
        val graph = app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        runBlocking {
            graph.session.restore()
            graph.settings.completeOnboarding()
            graph.settings.theme(InstrumentationRegistry.getArguments().getString("nativeTheme") ?: "light")
            graph.session.login("native.reader@example.test", "NativeFixture123!")
            assertTrue(graph.session.state.value is AuthState.SignedIn)
            graph.session.request("DELETE", "quran/bookmarks", mapOf("surah" to 1, "ayah" to 1))
            graph.session.request("DELETE", "me/favorites", mapOf("target_type" to "lesson", "target_id" to 3))
            graph.content.invalidate("quran/bookmarks")
            graph.content.invalidate("me/favorites")
        }
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(30_000) { compose.onAllNodesWithText(label(R.string.your_journey)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(label(R.string.home)).assertIsDisplayed()
            screenshot(app, "native-home")
            compose.onNodeWithText(label(R.string.night_prayer)).performScrollTo().performClick()
            compose.onNodeWithText(label(R.string.night_calendar)).performClick()
            compose.onNodeWithText(label(R.string.calendar_private)).assertIsDisplayed()
            screenshot(app, "native-calendar")
            compose.onNodeWithContentDescription(label(R.string.back)).performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText(label(R.string.your_journey)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Interface test lesson 3"))
            compose.onNodeWithText("Interface test lesson 3").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription(label(R.string.add_favorite)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(label(R.string.add_favorite)).performScrollTo().performClick()
            compose.waitUntil(15_000) { runBlocking { graph.session.request("GET", "me/favorites").asJsonArray.size() == 1 } }
            compose.onNodeWithContentDescription(label(R.string.back)).performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText(label(R.string.home)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(label(R.string.quran)).performClick()
            compose.onNodeWithText(label(R.string.read)).performClick()
            compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription(label(R.string.bookmark)).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithContentDescription(label(R.string.bookmark))[0].performClick()
            compose.waitUntil(15_000) { runBlocking { graph.session.request("GET", "quran/bookmarks").asJsonArray.size() == 1 } }
            screenshot(app, "native-quran")
            scenario.recreate()
            compose.waitForIdle()
            assertEquals(1, runBlocking { graph.session.request("GET", "quran/bookmarks").asJsonArray.size() })
        } } finally {
            runBlocking {
                runCatching { graph.session.request("DELETE", "quran/bookmarks", mapOf("surah" to 1, "ayah" to 1)) }
                runCatching { graph.session.request("DELETE", "me/favorites", mapOf("target_type" to "lesson", "target_id" to 3)) }
                graph.session.logout()
            }
        }
    }
    private fun screenshot(app: AtharApplication, name: String) {
        compose.waitForIdle()
        val variant = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "phone-light"
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.filesDir, "$variant-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
