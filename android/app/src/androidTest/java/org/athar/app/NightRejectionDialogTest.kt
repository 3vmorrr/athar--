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
import org.junit.*
import org.junit.Assert.*
import java.util.Locale

class NightRejectionDialogTest {
    @get:Rule val compose=createComposeRule()
    @Test fun retainedRecoveryActionsRemainReachableInDarkLargeTextAndDeletionIsRetryOnly(){
        val app=ApplicationProvider.getApplicationContext<android.content.Context>()
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val config=Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}
        val localized=app.createConfigurationContext(config)
        fun label(id:Int)=localized.resources.getString(id)
        val id="11111111-1111-4111-8111-111111111111"
        val body=JsonParser.parseString("""{"night_date":"2026-10-24","timezone":"UTC","boundary_source":"manual","entry_type":"manual","verse_count":12,"note":"Interface rejection fixture"}""").asJsonObject
        val row=LocalNightSession(1,id,"2026-10-24",12,null,"opaque fixture",1,0,false,true,1)
        var review by mutableStateOf(NightRejectionReview(1,row,body,listOf(OutboxMutation(userId=1,mutationId="fixture",sessionId=id,operation="put",expectedRevision=0,payloadCipher="opaque fixture",rejectedStatus=422))))
        var busy by mutableStateOf(false);var retries=0;var repairs=0
        compose.setContent{
            val density=LocalDensity.current;val view=LocalView.current
            DisposableEffect(view){val before=view.keepScreenOn;view.keepScreenOn=true;onDispose{view.keepScreenOn=before}}
            CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides config,LocalResources provides localized.resources,LocalLayoutDirection provides if(language=="ar")LayoutDirection.Rtl else LayoutDirection.Ltr,LocalDensity provides Density(density.density,2f)){
                AtharTheme("dark"){NightRejectionDialog(review,busy,{}, {retries++},{repairs++})}
            }
        }
        compose.onNodeWithText(label(R.string.night_rejection_invalid)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("night-rejection-repair").performScrollTo().assertIsDisplayed().performClick();assertEquals(1,repairs)
        compose.onNodeWithTag("night-rejection-retry").performScrollTo().performClick();assertEquals(1,retries)
        compose.runOnIdle{busy=true}
        compose.onNodeWithTag("night-rejection-retry").assertIsNotEnabled();compose.onNodeWithTag("night-rejection-repair").assertIsNotEnabled()
        compose.runOnIdle{busy=false;review=review.copy(local=row.copy(deleted=true),body=null,mutations=review.mutations.map{it.copy(rejectedStatus=404)})}
        compose.onNodeWithTag("night-rejection-repair").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.night_rejection_missing)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.cancel)).performScrollTo().assertIsDisplayed()
        val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(app.filesDir,"night-rejection-$language-large.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
