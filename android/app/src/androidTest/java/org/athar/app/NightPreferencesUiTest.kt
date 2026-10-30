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

class NightPreferencesUiTest {
    @get:Rule val compose = createComposeRule()
    private val namespace = "123456abcdef"
    private fun account() = NightPreferencesEnvelope(1, NightPreferenceValues("Riyadh", "Asia/Riyadh"))
    private fun ready(account: NightPreferencesEnvelope = account(), proof: NightPreferenceRepair? = null) = NightPreferencesUiState(
        load = NightPreferenceLoad(NightPreferencesSnapshot(1, namespace, account, "2026-10-30T04:00:00Z"), false, proof), loading = false,
        draft = NightPreferenceDraft.from(account.values), expected = account)
    private fun render(state: NightPreferencesUiState, events: MutableList<NightPreferencesEvent>): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val configuration = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = app.createConfigurationContext(configuration)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides context, LocalResources provides context.resources, LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr, LocalDensity provides Density(density.density, 1.5f)) {
                AtharTheme("dark") { NightPreferencesContent(state) { events.add(it) } }
            }
        }
        return context
    }
    @Test fun unreadablePreferencesCannotBecomeAnUnsetWritableAccount() {
        val events = mutableListOf<NightPreferencesEvent>()
        val context = render(NightPreferencesUiState(loading = false, error = NightPreferenceError.UNREADABLE), events)
        compose.onNodeWithTag("night-preferences").performScrollToNode(hasText(context.getString(R.string.night_preferences_unreadable)))
        compose.onNodeWithText(context.getString(R.string.night_preferences_unreadable)).assertIsDisplayed()
        compose.onNodeWithTag("preferences-unset").assertDoesNotExist()
        compose.onNodeWithTag("night-preferences").performScrollToNode(hasTestTag("preferences-city"))
        compose.onNodeWithTag("preferences-city").assertIsNotEnabled()
        compose.onNodeWithTag("night-preferences").performScrollToNode(hasTestTag("preferences-save"))
        compose.onNodeWithTag("preferences-save").assertIsNotEnabled(); assertTrue(events.isEmpty())
    }
    @Test fun publicChoicesAreLoadedAndAppliedBySeparateExplicitActions() {
        val events = mutableListOf<NightPreferencesEvent>()
        val defaults = NightPublicDefaults(1, true, "MUSLIM_WORLD_LEAGUE", "SHAFI", "SEVENTH_OF_THE_NIGHT", "مصدر", "Source")
        render(ready().copy(publicDefaults = defaults), events)
        compose.onNodeWithTag("night-preferences").performScrollToNode(hasTestTag("preferences-load-defaults"))
        compose.onNodeWithTag("preferences-load-defaults").performClick()
        assertEquals(listOf(NightPreferencesEvent.LoadDefaults), events)
        compose.onNodeWithTag("night-preferences").performScrollToNode(hasTestTag("preferences-apply-defaults"))
        compose.onNodeWithTag("preferences-apply-defaults").performClick()
        assertEquals(listOf(NightPreferencesEvent.LoadDefaults, NightPreferencesEvent.ApplyDefaults), events)
    }
    @Test fun accountCopyRepairRequiresConfirmationOfTheReviewedProof() {
        val events = mutableListOf<NightPreferencesEvent>()
        val proof = NightPreferenceRepair(1, namespace, "a".repeat(64), account())
        render(ready(proof = proof), events)
        compose.onNodeWithTag("night-preferences").performScrollToNode(hasTestTag("preferences-review-copy"))
        compose.onNodeWithTag("preferences-review-copy").performClick(); assertTrue(events.isEmpty())
        compose.onNodeWithTag("preferences-copy-confirm").performClick()
        assertEquals(listOf(NightPreferencesEvent.UseAccountCopy(proof)), events)
    }
    @Test fun reloadRequiresConfirmationOfTheCapturedFormVersion() {
        val events = mutableListOf<NightPreferencesEvent>()
        render(ready().copy(dirty = true, formVersion = 42, draft = NightPreferenceDraft.from(account().values).copy(city = "Unsaved")), events)
        compose.onNodeWithTag("night-preferences").performScrollToNode(hasTestTag("preferences-reload"))
        compose.onNodeWithTag("preferences-reload").performClick(); assertTrue(events.isEmpty())
        compose.onNodeWithTag("preferences-reload-confirm").performClick()
        assertEquals(listOf(NightPreferencesEvent.Reload(42)), events)
    }
    @Test fun typingEmitsAFieldChangeWithoutSavingOrReplacingOtherFields() {
        val events = mutableListOf<NightPreferencesEvent>(); render(ready(), events)
        compose.onNodeWithTag("night-preferences").performScrollToNode(hasTestTag("preferences-city"))
        compose.onNodeWithTag("preferences-city").performTextReplacement("مدينة جديدة")
        assertEquals(listOf(NightPreferencesEvent.Edit(NightPreferenceField.CITY, "مدينة جديدة")), events)
    }
}
