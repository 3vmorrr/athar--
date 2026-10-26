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
import org.athar.app.core.data.OutboxMutation
import org.athar.app.core.ui.AtharTheme
import org.athar.app.nightprayer.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Stateless bilingual/large-text rejection review; no private account or network mutation. */
class NightRejectionDialogTest {
    @get:Rule val compose=createComposeRule()

    @Test fun rejectedReviewKeepsChoicesDisabledUntilLoadedAndSupportsLargeText(){
        val app=ApplicationProvider.getApplicationContext<android.content.Context>()
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val config=Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}
        val localized=app.createConfigurationContext(config)
        fun label(id:Int)=localized.resources.getString(id)
        val id="11111111-1111-4111-8111-111111111111"
        val body=JsonParser.parseString("""{"night_date":"2026-10-24","entry_type":"manual","verse_count":12,"note":"Interface fixture"}""").asJsonObject
        val local=LocalNightSession(1,id,"2026-10-24",12,null,"opaque fixture",1,0,false,true,1)
        val mutation=OutboxMutation(userId=1,mutationId="22222222-2222-4222-8222-222222222222",sessionId=id,operation="put",expectedRevision=0,payloadCipher="opaque mutation")
        val rejected=NightRejection(local,body,mutation,422,"invalid_fields")
        var state by mutableStateOf(NightUiState(loading=false,rejectionReviewId=id,rejectionReviewLoading=true))
        var scale by mutableFloatStateOf(1.5f)
        var reloads=0;var retries=0;var repairs=0;var accounts=0
        compose.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides config,
                LocalResources provides localized.resources,
                LocalLayoutDirection provides if(language=="ar")LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density,scale),
            ){
                AtharTheme("dark"){
                    NightRejectionDialog(state,{}, {reloads++},{retries++},{accounts++},{repairs++})
                }
            }
        }
        compose.onNodeWithTag("night-rejection-retry").assertIsNotEnabled()
        compose.onNodeWithTag("night-rejection-repair").assertIsNotEnabled()
        compose.onNodeWithTag("night-rejection-account").assertIsNotEnabled()

        compose.runOnIdle{state=state.copy(rejectionReviewLoading=false,rejectionReviewError=true)}
        compose.onNodeWithText(label(R.string.network_error)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("night-rejection-reload").performScrollTo().performClick()
        assertEquals(1,reloads)

        compose.runOnIdle{
            state=state.copy(
                rejectionReviewError=false,
                rejectionReview=NightRejectionReview(1,rejected,null),
            )
        }
        compose.onNodeWithText(label(R.string.night_rejection_invalid)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.night_account_missing)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("night-rejection-retry").assertIsEnabled()
        compose.onNodeWithTag("night-rejection-repair").assertIsEnabled()
        compose.onNodeWithTag("night-rejection-account").assertIsEnabled()

        compose.runOnIdle{scale=2f}
        compose.onNodeWithTag("night-rejection-repair").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1,repairs)
        assertEquals(0,retries)
        assertEquals(0,accounts)
    }
}
