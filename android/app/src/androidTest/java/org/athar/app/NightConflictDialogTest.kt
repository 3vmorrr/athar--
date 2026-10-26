package org.athar.app

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import org.athar.app.core.data.LocalNightSession
import org.athar.app.core.ui.AtharTheme
import org.athar.app.nightprayer.*
import org.junit.*
import org.junit.Assert.*
import java.util.Locale

/** Stateless error/large-text review; fake private content, no network or settings mutation. */
class NightConflictDialogTest {
    @get:Rule val compose=createComposeRule()
    @Test fun conflictArrivingDuringEditingDisablesSaveAndKeepsTheDraft(){
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke")=="true")
        val app=ApplicationProvider.getApplicationContext<AtharApplication>()
        assertTrue(app.graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val config=Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}
        val localized=app.createConfigurationContext(config)
        fun label(id:Int)=localized.resources.getString(id)
        val body=JsonParser.parseString("""{"night_date":"2026-10-24","timezone":"UTC","entry_type":"manual","verse_count":12,"note":"Initial fixture"}""").asJsonObject
        val row=LocalNightSession(1,"11111111-1111-4111-8111-111111111111","2026-10-24",12,null,"opaque fixture",1,0,false,true,1)
        var conflicted by mutableStateOf(false)
        compose.setContent{
            val view=LocalView.current
            DisposableEffect(view){val before=view.keepScreenOn;view.keepScreenOn=true;onDispose{view.keepScreenOn=before}}
            CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides config,LocalResources provides localized.resources,LocalLayoutDirection provides if(language=="ar")LayoutDirection.Rtl else LayoutDirection.Ltr){
                AtharTheme("light"){NightEntryDialog(app.graph,row to body,false,conflicted,{}, {})}
            }
        }
        compose.onNodeWithText(label(R.string.private_note)).performScrollTo().performTextReplacement("Retained editor fixture")
        compose.runOnIdle{conflicted=true}
        compose.onNodeWithText(label(R.string.save)).assertIsNotEnabled()
        // The inserted warning can move this lazy item outside the viewport.
        compose.onNodeWithTag("night-entry-fields").performScrollToNode(hasText(label(R.string.private_note)))
        compose.onNodeWithText("Retained editor fixture").assertExists()
        compose.onNodeWithText(label(R.string.night_edit_conflict)).performScrollTo().assertIsDisplayed()
    }
    @Test fun reviewHandlesErrorsAndLargeTextWithoutEnablingAnUnloadedChoice(){
        val app=ApplicationProvider.getApplicationContext<android.content.Context>()
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val config=Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}
        val localized=app.createConfigurationContext(config)
        fun label(id:Int)=localized.resources.getString(id)
        val id="11111111-1111-4111-8111-111111111111"
        val body=JsonParser.parseString("""{"night_date":"2026-10-24","entry_type":"manual","verse_count":12,"note":"Interface fixture note"}""").asJsonObject
        val account=JsonParser.parseString("""{"id":"$id","revision":2,"deleted":true}""").asJsonObject
        val local=LocalNightSession(1,id,"2026-10-24",12,null,"opaque fixture",1,0,false,true,1)
        var state by mutableStateOf(NightUiState(loading=false,reviewId=id,reviewLoading=true))
        var scale by mutableFloatStateOf(1.5f);var retry=0;var choices=0
        compose.setContent{
            val density=LocalDensity.current;val view=LocalView.current
            DisposableEffect(view){val before=view.keepScreenOn;view.keepScreenOn=true;onDispose{view.keepScreenOn=before}}
            CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides config,LocalResources provides localized.resources,LocalLayoutDirection provides if(language=="ar")LayoutDirection.Rtl else LayoutDirection.Ltr,LocalDensity provides Density(density.density,scale)){
                AtharTheme("dark"){NightConflictDialog(state,{}, {retry++},{choices++})}
            }
        }
        compose.onNodeWithTag("night-keep-device").assertIsNotEnabled()
        compose.onNodeWithTag("night-keep-account").assertIsNotEnabled()
        compose.runOnIdle{state=state.copy(reviewLoading=false,reviewError=true)}
        compose.onNodeWithText(label(R.string.network_error)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.retry)).performScrollTo().performClick();assertEquals(1,retry)
        compose.runOnIdle{state=state.copy(reviewLocalError=true)}
        compose.onNodeWithText(label(R.string.local_data_unreadable)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle{state=state.copy(reviewError=false,reviewLocalError=false,review=NightConflictReview(1,local,body,account))}
        compose.onNodeWithTag("night-keep-device").assertIsEnabled().assertIsDisplayed()
        compose.onNodeWithTag("night-keep-account").assertIsEnabled().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.night_copy_explanation)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle{scale=2f}
        compose.onNodeWithTag("night-keep-device").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1,choices)
        compose.onNodeWithText(label(R.string.cancel)).performScrollTo().assertIsDisplayed()
    }
}
