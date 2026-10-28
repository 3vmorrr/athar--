package org.athar.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.athar.app.auth.AuthState
import org.athar.app.core.ui.*
import org.athar.app.media.*
import org.junit.*
import org.junit.Assert.*
import java.util.Locale

/** Real application composables and HTTP fixtures; no production credentials, row clearing, or mail. */
class PremiumUiJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val args get() = InstrumentationRegistry.getArguments()
    private val app get() = ApplicationProvider.getApplicationContext<AtharApplication>()
    private val language get() = args.getString("reviewLanguage") ?: "ar"
    private val prefix get() = "premium-$language-${args.getString("reviewRun") ?: "manual"}"
    private val resources get() = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
    private fun label(id: Int) = resources.getString(id)
    private fun awaitText(text: String) = compose.waitUntil(25_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitTag(tag: String) {
        try { compose.waitUntil(25_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Exception) { capture("missing-target"); throw AssertionError("Missing target: $tag", failure) }
    }
    private fun settleTransition() { compose.waitForIdle(); android.os.SystemClock.sleep(AtharMotion.Slow.toLong()) }
    private fun tap(tag: String) {
        awaitTag(tag)
        val node = compose.onNodeWithTag(tag)
        if (runCatching { node.assertIsDisplayed() }.isFailure) node.performScrollTo()
        node.performClick(); settleTransition()
    }
    private fun action(id: Int) {
        val node = compose.onAllNodes(hasText(label(id)) and hasClickAction()).onFirst()
        if (runCatching { node.assertIsDisplayed() }.isFailure) node.performScrollTo()
        node.performClick()
        settleTransition()
    }
    private fun back() { compose.onNodeWithContentDescription(label(R.string.back)).performClick(); settleTransition() }
    private fun capture(name: String) {
        compose.waitForIdle()
        // Capture the display, including shell/insets. Root captures can crop shared overlays.
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file = "$prefix-$name.png"
        app.openFileOutput(file, android.content.Context.MODE_PRIVATE).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        app.openFileOutput("$prefix-manifest.txt", android.content.Context.MODE_APPEND).use { it.write((file + "\n").toByteArray()) }
    }
    private fun login(): AuthState.SignedIn {
        Assume.assumeTrue(args.getString("nativeApiSmoke") == "true")
        assertEquals("http://127.0.0.1:8004/api/v1/", app.graph.apiBaseUrl)
        runBlocking { app.graph.session.restore(); app.graph.session.login("native.reader@example.test", "NativeFixture123!") }
        return app.graph.session.state.value as AuthState.SignedIn
    }
    private fun host(scenario: ActivityScenario<MainActivity>, auth: AuthState.SignedIn, audioReady: (AudioViewModel) -> Unit = {}) {
        lateinit var model: AppViewModel
        scenario.onActivity { activity ->
            model = AppViewModel(app.graph)
            activity.setContent {
                val view = LocalView.current
                DisposableEffect(view) { val prior = view.keepScreenOn; view.keepScreenOn = true; onDispose { view.keepScreenOn = prior } }
                val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
                val context = app.createConfigurationContext(config)
                val audio = audioViewModel(app.graph)
                SideEffect { audioReady(audio) }
                CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config,
                    LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalAtharAudio provides audio) {
                    AtharTheme(if (language == "en") "dark" else "light") { Surface(Modifier.fillMaxSize()) { MainShell(model, auth) } }
                }
            }
        }
    }

    @Test fun realScreensKeepNavigationAndContentInBothThemes() {
        val auth = login()
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            host(scenario, auth)
            awaitText(label(R.string.your_journey)); capture("home")
            app.openFileOutput("$prefix-ready.txt", android.content.Context.MODE_PRIVATE).use { it.write("ready".toByteArray()) }
            tap("nav-knowledge"); awaitText("Interface test lesson 3"); capture("knowledge")
            tap("catalog-category-books"); awaitText("Interface test book"); capture("books")
            tap("catalog-books-1"); awaitText("Automated interface fixture, not an actual published book."); capture("book-detail")
            back(); tap("catalog-category-series"); tap("catalog-series-1"); awaitTag("series-identity"); capture("series")
            back(); tap("catalog-category-scholars"); tap("catalog-scholars-1"); awaitTag("scholar-identity"); capture("scholar")
            back(); tap("catalog-category-lessons"); tap("catalog-lessons-3"); awaitTag("watch-title"); capture("watch-provider-state")
            back(); tap("nav-quran"); capture("quran-tools")
            tap("quran-action-read"); awaitTag("quran-reader-list"); capture("quran-reader")
            action(R.string.quran_tools); tap("quran-action-listen"); awaitTag("audio-reciter"); capture("audio-selection")
            action(R.string.quran_tools); tap("quran-action-tafsir"); awaitText(label(R.string.choose_tafsir)); capture("tafsir-selection")
            action(R.string.quran_tools); tap("quran-action-radio"); capture("radio-state")
            tap("nav-adhkar"); capture("adhkar-state")
            tap("nav-home"); awaitText(label(R.string.your_journey)); action(R.string.night_prayer)
            awaitText(label(R.string.night_calendar)); capture("night-history")
            action(R.string.night_calendar); awaitText(label(R.string.calendar_private)); capture("calendar")
            action(R.string.night_tools); capture("night-tools")
            back(); tap("nav-knowledge"); action(R.string.upcoming_lessons); awaitTag("upcoming-list"); capture("upcoming")
            back(); tap("nav-account"); awaitText("native.reader@example.test"); capture("account-fixture")
            action(R.string.khatmah); capture("khatmah")
            action(R.string.bookmarks); capture("bookmarks")
            action(R.string.favorites); capture("favorites")
            action(R.string.export_data); capture("export-form")
        } } finally { runBlocking { app.graph.session.logout() } }
    }

    @Test fun actualMedia3ControlsSurviveNavigationAndBackground() {
        Assume.assumeTrue(args.getString("liveAudioSmoke") == "true")
        val auth = login()
        lateinit var audio: AudioViewModel
        var audioForCleanup: AudioViewModel? = null
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            host(scenario, auth) { audio = it; audioForCleanup = it }
            awaitText(label(R.string.your_journey))
            val reader = runBlocking { app.graph.content.get("quran/reciters", mapOf("surah" to "1", "per_page" to "50")).data.array("items").first { it.text("id") == "1" } }
            scenario.onActivity { audio.select(audio.selection.value.withReader(reader)); audio.startSelection() }
            compose.waitUntil(90_000) { audio.state.value.playing && audio.state.value.durationMs > 0 }
            scenario.onActivity { audio.toggle() }
            compose.waitUntil(10_000) { !audio.state.value.playing }
            awaitTag("athar-mini-player"); capture("mini-player")
            compose.onNodeWithContentDescription(label(R.string.expand_player)).performClick()
            awaitTag("athar-expanded-player")
            tap("player-speed"); tap("player-speed-1.25")
            compose.waitUntil(10_000) { audio.state.value.speed == 1.25f }
            tap("player-repeat"); compose.waitUntil(10_000) { audio.state.value.repeatOne }
            compose.onNodeWithTag("player-seek").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(audio.state.value.durationMs / 3f) }
            compose.waitUntil(10_000) { audio.elapsed() > audio.state.value.durationMs / 4 }
            capture("expanded-player")
            tap("player-toggle"); compose.waitUntil(10_000) { audio.state.value.playing }
            back(); tap("nav-knowledge"); compose.onNodeWithTag("athar-mini-player").assertIsDisplayed()
            val position = audio.elapsed()
            scenario.moveToState(Lifecycle.State.CREATED)
            runBlocking { delay(2000) }
            assertTrue(audio.elapsed() > position)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("athar-mini-player").assertIsDisplayed()
            scenario.onActivity { audio.stop() }
            compose.waitUntil(10_000) { !audio.state.value.hasMedia }
            compose.onNodeWithTag("athar-mini-player").assertDoesNotExist()
            app.openFileOutput("premium-audio-outcome.txt", android.content.Context.MODE_PRIVATE).use {
                it.write("Actual provider streaming, mini/expanded continuity, UI speed/repeat/seek/play/pause, navigation, background position advance, stop removal verified.".toByteArray())
            }
        } } finally { audioForCleanup?.let { player -> InstrumentationRegistry.getInstrumentation().runOnMainSync { player.stop() } }; runBlocking { app.graph.session.logout() } }
    }
}
