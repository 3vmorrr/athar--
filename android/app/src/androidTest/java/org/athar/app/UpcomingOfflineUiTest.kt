package org.athar.app

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.athar.app.core.ui.AtharTheme
import org.athar.app.learning.*
import org.junit.*
import org.junit.Assert.*
import java.util.Locale

/** Presentation acceptance with explicit cached/removed states; no global network toggle or real broadcast. */
class UpcomingOfflineUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun cachedLiveCannotPlayAndRemovedOrUnreleasedLessonsExplainTheirState() {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val configuration=Configuration(context.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}
        val localized=context.createConfigurationContext(configuration)
        val resources=localized.resources
        fun label(id:Int)=resources.getString(id)
        val event=UpcomingLesson(5,"Interface fixture","",EventStatus.LIVE,null,null,"",null,"",null,1,true)
        var state by mutableStateOf(UpcomingState(loading=false,offline=true,items=listOf(event)))
        var opened="";var refreshes=0
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides configuration,LocalLayoutDirection provides if(language=="ar")LayoutDirection.Rtl else LayoutDirection.Ltr){
                AtharTheme("light"){UpcomingContent(state,true,{refreshes++},{},{opened=it})}
            }
        }
        compose.onNodeWithText(label(R.string.offline_notice)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.upcoming_last_live)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.upcoming_live)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.play)).assertDoesNotExist()
        compose.runOnIdle{state=state.copy(offline=false)}
        compose.onNodeWithText(label(R.string.play)).performScrollTo().performClick()
        assertEquals("lessons/5",opened)
        compose.runOnIdle{state=UpcomingState(loading=false,items=listOf(event.copy(status=EventStatus.PUBLISHED,canWatch=false)))}
        compose.onNodeWithText(label(R.string.upcoming_replay_pending)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.play)).assertDoesNotExist()
        compose.runOnIdle{state=UpcomingState(loading=false,unavailable=true)}
        compose.onNodeWithText(label(R.string.upcoming_unavailable)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("upcoming-event-5").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.network_error)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.upcoming_refresh)).performScrollTo().performClick()
        assertEquals(1,refreshes)
    }
}
