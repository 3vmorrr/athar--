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

class NightPaceUiTest {
    @get:Rule val compose = createComposeRule()
    private fun render(pace: NightMeasuredPace?): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val configuration = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = app.createConfigurationContext(configuration)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr, LocalDensity provides Density(density.density, 1.5f)) {
                AtharTheme("dark") { LazyColumn(Modifier.testTag("pace-test-list")) { item { NightPaceContent(pace) } } }
            }
        }
        return context
    }
    @Test fun noMeasuredSamplesExplainsInsufficientDataWithoutAnInventedAverage() {
        val context = render(null)
        compose.onNodeWithText(context.getString(R.string.night_pace_unavailable)).assertIsDisplayed()
        compose.onNodeWithTag("night-pace-average").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.night_pace_notice)).assertExists()
    }
    @Test fun measuredAverageShowsItsActualSampleCountsAndNeutralContext() {
        val pace = NightRules.measuredPace(listOf(10 to 5, 20 to 20))!!
        val context = render(pace)
        compose.onNodeWithText(context.getString(R.string.night_measured_pace, pace.secondsPerVerse)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.night_pace_samples, 2, 30L)).assertExists()
        compose.onNodeWithTag("night-pace-unavailable").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.night_pace_notice)).assertExists()
    }
}
