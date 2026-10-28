package org.athar.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import org.athar.app.core.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Presentation host only: no account override, private data reset or global font/theme change. */
class ImmersiveHeroUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bilingualLargeTextActionsAndDraftSurviveReducedMotionChanges() {
        val app=ApplicationProvider.getApplicationContext<android.content.Context>()
        var language by mutableStateOf("ar");var dark by mutableStateOf(false)
        var font by mutableFloatStateOf(1f);var reduced by mutableStateOf(false)
        var draft by mutableStateOf("");var taps=0;var secondary=0
        var clearDraftFocus: (() -> Unit)?=null
        compose.setContent {
            val view=LocalView.current
            DisposableEffect(view){val prior=view.keepScreenOn;view.keepScreenOn=true;onDispose{view.keepScreenOn=prior}}
            val density=LocalDensity.current
            val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
            SideEffect{clearDraftFocus={focus.clearFocus();keyboard?.hide()}}
            val config=Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}
            val context=app.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides config,
                LocalLayoutDirection provides if(language=="ar")LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density,font)) {
                AtharTheme(if(dark)"dark" else "light") {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())) {
                            AtharImmersiveHero(
                                AtharHeroPresentation(AtharHeroArtwork.Home,
                                    context.getString(R.string.your_journey),context.getString(R.string.home_invitation),
                                    primaryActionLabel=context.getString(R.string.read),
                                    secondaryActionLabel=context.getString(R.string.night_prayer),transitionKey="fixture-home"),
                                reduceMotion=reduced,onPrimaryAction={taps++},onSecondaryAction={secondary++},
                                trailing={OutlinedTextField(draft,{draft=it},Modifier.fillMaxWidth().testTag("retained-hero-draft"),label={Text("Fixture draft")},colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedLabelColor=Color.White,unfocusedLabelColor=Color.White,focusedBorderColor=Color.White,unfocusedBorderColor=Color.White))},
                            )
                        }
                    }
                }
            }
        }
        for ((lang,isDark,scale) in listOf(Triple("ar",false,1f),Triple("en",true,1.5f),Triple("ar",false,2f))) {
            compose.runOnIdle{language=lang;dark=isDark;font=scale;reduced=scale>1f}
            val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(lang))}).resources
            compose.onNodeWithText(resources.getString(R.string.your_journey)).performScrollTo().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            compose.onNodeWithText(resources.getString(R.string.read)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithText(resources.getString(R.string.night_prayer)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            val field=compose.onNodeWithTag("retained-hero-draft").performScrollTo()
            field.performTextReplacement("Retained fixture draft")
            compose.runOnIdle{reduced=!reduced}
            field.assertTextContains("Retained fixture draft")
            compose.runOnIdle{clearDraftFocus?.invoke()}
            compose.onNodeWithText(resources.getString(R.string.your_journey)).performScrollTo()
            val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
            app.openFileOutput("immersive-hero-$lang-$isDark-$scale.png",android.content.Context.MODE_PRIVATE).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        assertEquals(3,taps);assertEquals(3,secondary)
    }

    @Test fun absentHandlerNeverCreatesAnInertAction() {
        compose.setContent {AtharTheme("light") {AtharImmersiveHero(AtharHeroPresentation(AtharHeroArtwork.Quran,"Fixture heading",primaryActionLabel="Unavailable action"),reduceMotion=true)}}
        compose.onNodeWithText("Fixture heading").assertIsDisplayed()
        compose.onNodeWithText("Unavailable action").assertDoesNotExist()
    }

    @Test fun fallbackStatesKeepNativeContextAndWorkingActions() {
        var state by mutableStateOf(AtharHeroVisualState.Ready);var taps=0
        compose.setContent {AtharTheme("dark"){AtharImmersiveHero(AtharHeroPresentation(AtharHeroArtwork.Quran,"Fixture context",supportingText="Generated fixture context",primaryActionLabel="Fixture action",visualState=state),reduceMotion=true,onPrimaryAction={taps++})}}
        for(value in AtharHeroVisualState.entries){
            compose.runOnIdle{state=value}
            compose.onNodeWithText("Fixture context").assertIsDisplayed().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            compose.onNodeWithText("Fixture action").assertIsEnabled().performClick()
        }
        assertEquals(AtharHeroVisualState.entries.size,taps)
    }
}
