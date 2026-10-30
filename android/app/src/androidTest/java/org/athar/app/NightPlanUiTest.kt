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
import org.athar.app.quran.QuranIndex
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.Locale

class NightPlanUiTest {
    @get:Rule val compose = createComposeRule()
    private fun index(): QuranIndex = ApplicationProvider.getApplicationContext<Context>().assets.open("quran/index.json").bufferedReader().use { QuranIndex.fromJson(it.readText()) }
    private fun render(state: NightPlanUiState, events: MutableList<NightPlanEvent>): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val configuration = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = app.createConfigurationContext(configuration)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr, LocalDensity provides Density(density.density, 1.5f)) {
                AtharTheme("dark") { NightPlanContent(state) { events.add(it) } }
            }
        }
        return context
    }
    @Test fun unreadablePlanCannotBeReplacedOrMisrepresentedAsAnEmptyPlan() {
        val events = mutableListOf<NightPlanEvent>()
        val context = render(NightPlanUiState(read = ProtectedRead.Unreadable, indexReady = true, preview = index().target(1, 1, 100)), events)
        compose.onNodeWithTag("night-plan-screen").performScrollToNode(hasText(context.getString(R.string.local_data_unreadable)))
        compose.onNodeWithText(context.getString(R.string.local_data_unreadable)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.night_plan_empty)).assertDoesNotExist()
        compose.onNodeWithTag("night-plan-screen").performScrollToNode(hasTestTag("plan-save"))
        compose.onNodeWithTag("plan-save").assertIsNotEnabled()
        compose.onNodeWithTag("plan-record").assertDoesNotExist(); assertTrue(events.isEmpty())
    }
    @Test fun savingAPreviewDoesNotEmitARecordCompletedAction() {
        val events = mutableListOf<NightPlanEvent>()
        render(NightPlanUiState(read = ProtectedRead.Ready(NightPlanState(1, "123456abcdef")), indexReady = true, preview = index().target(1, 1, 100)), events)
        compose.onNodeWithTag("night-plan-screen").performScrollToNode(hasTestTag("plan-save"))
        compose.onNodeWithTag("plan-save").performClick()
        assertEquals(listOf(NightPlanEvent.Save), events)
        compose.onNodeWithTag("plan-record").assertDoesNotExist()
    }
    @Test fun recordingShowsTheReviewedRangeAndRequiresItsOwnConfirmation() {
        val events = mutableListOf<NightPlanEvent>()
        val saved = NightPlanState(1, "123456abcdef").save(7, 8, 0, Instant.now())
        val range = saved.plan!!.range(index())
        val context = render(NightPlanUiState(read = ProtectedRead.Ready(saved), indexReady = true, savedRange = range,
            record = NightRecordDialog(saved, range, date = "2026-10-29", zone = "UTC")), events)
        compose.onNodeWithText(context.getString(R.string.night_plan_preview, "1:7 — 2:1", 2)).assertIsDisplayed()
        assertTrue(events.isEmpty())
        compose.onNodeWithTag("plan-record-confirm").performClick()
        assertEquals(listOf(NightPlanEvent.Record), events)
    }
}
