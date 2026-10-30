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
import com.google.gson.JsonObject
import org.athar.app.core.data.*
import org.athar.app.core.ui.AtharTheme
import org.athar.app.nightprayer.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.*
import java.util.Locale

class NightTonightUiTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 10, 29)
    private fun identity() = NightIdentity(date, true, NightContext(date, ZoneOffset.UTC, date.atTime(18, 0).toInstant(ZoneOffset.UTC), date.plusDays(1).atTime(5, 0).toInstant(ZoneOffset.UTC)))
    private fun render(identity: NightIdentity?, records: ProtectedRead<List<Pair<LocalNightSession, JsonObject>>>, retry: () -> Unit = {}): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val configuration = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = app.createConfigurationContext(configuration)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr, LocalDensity provides Density(density.density, 1.5f)) {
                AtharTheme("dark") { NightTonightContent(identity, records, false, false, retry, {}, {}) }
            }
        }
        return context
    }
    @Test fun unreadableRecordsHaveRetryWithoutInventedEmptyOrMetrics() {
        var retries = 0
        val context = render(identity(), ProtectedRead.Unreadable) { retries++ }
        compose.onNodeWithText(context.getString(R.string.local_data_unreadable)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.night_empty)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.night_tonight_verses)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.retry)).performClick(); assertEquals(1, retries)
    }
    @Test fun missingPrayerSettingsShowUnavailableInsteadOfZeroTonightMetrics() {
        val context = render(null, ProtectedRead.Ready(emptyList()))
        compose.onNodeWithTag("night-tonight").performScrollToNode(hasText(context.getString(R.string.night_identity_unavailable)))
        compose.onNodeWithText(context.getString(R.string.night_identity_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.night_tonight_verses)).assertDoesNotExist()
    }
    @Test fun dashboardUsesOnlyExplicitRecordsForTheMaghribDate() {
        fun row(day: LocalDate, count: Int) = LocalNightSession(1, day.toString(), day.toString(), count, null, "fixture", 1, 0, false, true, 1) to JsonObject()
        val context = render(identity(), ProtectedRead.Ready(listOf(row(date, 100), row(date.minusDays(1), 1000))))
        compose.onNodeWithTag("night-tonight").performScrollToNode(hasText(context.getString(R.string.night_tonight_verses)))
        compose.onNodeWithText(context.getString(R.string.night_tonight_verses)).assertIsDisplayed()
        compose.onNodeWithTag("night-tonight").performScrollToNode(hasText(context.getString(R.string.night_reached_milestone, 100)))
        compose.onNodeWithText(context.getString(R.string.night_reached_milestone, 100)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.night_reached_milestone, 1000)).assertDoesNotExist()
        compose.onNodeWithTag("night-tonight").performScrollToNode(hasText(context.getString(R.string.night_pending)))
        compose.onNodeWithText(context.getString(R.string.night_pending)).assertIsDisplayed()
    }
}
