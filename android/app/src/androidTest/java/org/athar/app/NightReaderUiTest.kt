package org.athar.app

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.ui.AtharTheme
import org.athar.app.nightprayer.*
import org.athar.app.quran.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.Locale

class NightReaderUiTest {
    @get:Rule val compose = createComposeRule()
    private val scope = NightReadingScope(1, "123456abcdef", "plan", "11111111-1111-4111-8111-111111111111", 7, 8)
    private fun ready(): NightReaderUiState {
        val data = runBlocking { QuranRepository(ApplicationProvider.getApplicationContext()).data() }
        val range = scope.range(data.index)
        return NightReaderUiState(scope, loading = false, range = range, verses = data.range(range),
            snapshot = NightReadingSnapshot(scope, NightReadingPosition(scope), 7))
    }
    private fun render(state: NightReaderUiState, events: MutableList<NightReaderEvent>): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val configuration = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = app.createConfigurationContext(configuration)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr, LocalDensity provides Density(density.density, 1.5f)) {
                AtharTheme("dark") { NightReaderContent(state) { events.add(it) } }
            }
        }
        return context
    }
    @Test fun crossSurahReaderRendersExactTextAndOnlyEmitsIndependentMarkerAction() {
        val state = ready(); val events = mutableListOf<NightReaderEvent>(); render(state, events)
        compose.onNodeWithTag("night-reader").performScrollToNode(hasTestTag("night-reader-save-7"))
        compose.onNodeWithText(state.verses[0].text).assertExists()
        compose.onNodeWithTag("night-reader-save-7").performClick()
        assertEquals(listOf(NightReaderEvent.Save(7, 0)), events)
        compose.onNodeWithTag("night-reader").performScrollToNode(hasTestTag("night-reader-save-8"))
        compose.onNodeWithText(state.verses[1].text).assertExists()
        compose.onNodeWithText(requireNotNull(state.verses[1].bismillah)).assertExists()
        compose.onNodeWithTag("night-reader-ayah-9").assertDoesNotExist()
        compose.onNodeWithTag("night-reader-marker").assertDoesNotExist()
    }
    @Test fun sourceFailureShowsNoVersesOrFakeMarker() {
        val events = mutableListOf<NightReaderEvent>()
        val context = render(NightReaderUiState(scope, loading = false, error = NightReaderError.SOURCE_CHANGED), events)
        compose.onNodeWithTag("night-reader").performScrollToNode(hasText(context.getString(R.string.night_reader_source_changed)))
        compose.onNodeWithText(context.getString(R.string.night_reader_source_changed)).assertIsDisplayed()
        compose.onNodeWithTag("night-reader-save-7").assertDoesNotExist()
        compose.onNodeWithTag("night-reader-marker").assertDoesNotExist(); assertTrue(events.isEmpty())
    }
    @Test fun changedRangeRequiresConfirmationOfTheReviewedMarkerVersion() {
        val events = mutableListOf<NightReaderEvent>(); val ready = ready()
        val oldScope = scope.copy(end = 9)
        val review = NightReadingPosition(oldScope, 4, 9, Instant.now().toString())
        val data = runBlocking { QuranRepository(ApplicationProvider.getApplicationContext()).data() }
        render(ready.copy(snapshot = null, verses = emptyList(), error = NightReaderError.RANGE_CHANGED, review = review, reviewRange = oldScope.range(data.index)), events)
        compose.onNodeWithTag("night-reader").performScrollToNode(hasTestTag("night-reader-reset"))
        compose.onNodeWithTag("night-reader-reset").performClick(); assertTrue(events.isEmpty())
        compose.onNodeWithTag("night-reader-confirm").performClick()
        assertEquals(listOf(NightReaderEvent.Reset(4)), events)
    }
}
