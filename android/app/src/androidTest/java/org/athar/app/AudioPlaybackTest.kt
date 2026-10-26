package org.athar.app

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.athar.app.core.ui.array
import org.athar.app.core.ui.text
import org.athar.app.media.AudioViewModel
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Opt-in public-provider probe, with a generated local account and no fabricated timing. */
class AudioPlaybackTest {
    @Test fun actualWholeSurahPlaysAndChangingSourceWhilePausedDoesNotAutoplay() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveAudioSmoke") == "true")
        val app = ApplicationProvider.getApplicationContext<AtharApplication>()
        val graph = app.graph
        assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        runBlocking {
            graph.session.restore(); graph.settings.completeOnboarding()
            graph.session.login("native.reader@example.test", "NativeFixture123!")
        }
        fun await(timeout: Long = 60_000, predicate: () -> Boolean) = runBlocking {
            withTimeout(timeout) { while (!predicate()) delay(100) }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var vm: AudioViewModel
                scenario.onActivity { activity ->
                    vm = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = AudioViewModel(graph, app) as T
                    })[AudioViewModel::class.java]
                }
                // Provider IDs checked against the live public API; absence fails rather than inventing content.
                val reader = runBlocking { graph.content.get("quran/reciters", mapOf("surah" to "1", "per_page" to "50")).data.array("items").first { it.text("id") == "1" } }
                scenario.onActivity { vm.select(vm.selection.value.withReader(reader)); vm.startSelection() }
                await { vm.state.value.playing && vm.state.value.timings.isNotEmpty() }
                assertFalse(vm.state.value.error)
                assertEquals("1:1:1:0", vm.state.value.mediaKey)
                assertTrue(vm.state.value.timings.all { it.end > it.start && it.ayah in 1..7 })
                scenario.onActivity { vm.toggle() }
                await(10_000) { !vm.state.value.playing }
                scenario.onActivity { vm.select(vm.selection.value.withSurah(2)) }
                android.os.SystemClock.sleep(1000)
                assertFalse(vm.state.value.playing)
                assertEquals("1:1:1:0", vm.state.value.mediaKey)
                assertEquals("1:1:2:0", vm.selection.value.mediaKey)
                // Explicit Play loads the changed selection, then a playing source change refreshes timing.
                scenario.onActivity { vm.playSelection() }
                await { vm.state.value.playing && vm.state.value.mediaKey == "1:1:2:0" }
                scenario.onActivity { vm.select(vm.selection.value.withSurah(1)) }
                await { vm.state.value.playing && vm.state.value.mediaKey == "1:1:1:0" && vm.state.value.timings.isNotEmpty() }
                val verseSelection = vm.selection.value.copy(verseMode = true, ayah = 2)
                scenario.onActivity { vm.select(vm.selection.value.copy(edition = null)) }
                await(10_000) { !vm.state.value.playing && vm.state.value.recording == null }
                scenario.onActivity { vm.select(verseSelection); vm.startSelection() }
                await { vm.state.value.playing && vm.state.value.mediaKey == "1:1:1:2" }
                val timing = vm.state.value.timings.first { it.ayah == 2 }
                assertEquals(timing.start, vm.state.value.clipStart)
                scenario.onActivity { assertTrue(vm.position() in timing.start..timing.end); vm.stop() }
                File(app.filesDir, "official-audio-outcome.txt").writeText("playing; real timing; paused selection retained; explicit play; active source refresh; unavailable selection stops previous audio; timed ayah clip plays")
            }
        } finally { runBlocking { graph.session.logout() } }
    }
}
