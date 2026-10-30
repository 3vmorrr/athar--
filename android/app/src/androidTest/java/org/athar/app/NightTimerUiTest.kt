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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.Locale

class NightTimerUiTest {
    @get:Rule val compose = createComposeRule()
    private val start = Instant.parse("2026-10-29T23:59:50Z")
    private fun measured() = NightMeasuredTimer.start(
        NightIdentity(java.time.LocalDate.parse("2026-10-29"), true, NightContext(java.time.LocalDate.parse("2026-10-29"), java.time.ZoneOffset.UTC,
            Instant.parse("2026-10-29T18:00:00Z"), Instant.parse("2026-10-30T05:00:00Z"))), "EGYPTIAN", start).checkpoint(5000, start.plusSeconds(5))
    private fun state(timer: NightMeasuredTimer) = NightTimerUiState(ProtectedRead.Ready(NightTimerFrame(1, "123456abcdef", 3, timer)), displayedMillis = timer.millis)
    private fun render(state: NightTimerUiState, events: MutableList<NightTimerEvent>, resumeAllowed: Boolean = false): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val configuration = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = app.createConfigurationContext(configuration)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr, LocalDensity provides Density(density.density, 1.5f)) {
                AtharTheme("dark") { LazyColumn(Modifier.testTag("timer-test-list")) {
                    item { NightTimerCardContent(state, null, null, resumeAllowed) { events.add(it) } }
                } }
            }
        }
        return context
    }
    @Test fun unreadableTimerDoesNotOfferAFreshStartOrDestructiveClear() {
        val events = mutableListOf<NightTimerEvent>(); val context = render(NightTimerUiState(read = ProtectedRead.Unreadable), events)
        compose.onNodeWithText(context.getString(R.string.local_data_unreadable)).assertIsDisplayed()
        compose.onNodeWithTag("night-timer-start").assertDoesNotExist(); compose.onNodeWithTag("night-timer-clear").assertDoesNotExist()
        assertTrue(events.isEmpty())
    }
    @Test fun finishEmitsOnlyTheReviewedTimerVersionAndNeverRecordsPrayer() {
        val events = mutableListOf<NightTimerEvent>(); render(state(measured()), events)
        compose.onNodeWithTag("timer-test-list").performScrollToNode(hasTestTag("night-timer-finish"))
        compose.onNodeWithTag("night-timer-finish").performClick()
        assertEquals(listOf(NightTimerEvent.Finish(3)), events)
        compose.onNodeWithTag("night-timer-record").assertDoesNotExist()
    }
    @Test fun finishedTimerOpensASeparateRecordingReview() {
        val events = mutableListOf<NightTimerEvent>(); render(state(measured().finish()), events)
        assertTrue(events.isEmpty())
        compose.onNodeWithTag("timer-test-list").performScrollToNode(hasTestTag("night-timer-record"))
        compose.onNodeWithTag("night-timer-record").performClick()
        assertEquals(listOf(NightTimerEvent.Record(3)), events)
    }
    @Test fun discardNeedsConfirmationOfTheCapturedTimerVersion() {
        val events = mutableListOf<NightTimerEvent>(); render(state(measured()), events)
        compose.onNodeWithTag("timer-test-list").performScrollToNode(hasTestTag("night-timer-clear"))
        compose.onNodeWithTag("night-timer-clear").performClick(); assertTrue(events.isEmpty())
        compose.onNodeWithTag("night-timer-clear-confirm").performClick()
        assertEquals(listOf(NightTimerEvent.Clear(3)), events)
    }
    @Test fun expiredPausedTimerCannotResumeAndClockFeedbackKeepsMeasuredDataVisible() {
        val events = mutableListOf<NightTimerEvent>()
        val context = render(state(measured().copy(phase = "paused", reason = "boundary")).copy(feedback = NightTimerFeedback.CLOCK), events)
        compose.onNodeWithTag("timer-test-list").performScrollToNode(hasTestTag("night-timer-resume"))
        compose.onNodeWithTag("night-timer-resume").assertIsNotEnabled()
        compose.onNodeWithTag("night-timer-duration").assertExists()
        compose.onNodeWithTag("timer-test-list").performScrollToNode(hasText(context.getString(R.string.night_timer_check_clock)))
        compose.onNodeWithText(context.getString(R.string.night_timer_check_clock)).assertIsDisplayed(); assertTrue(events.isEmpty())
    }
}
