package org.athar.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.athar.app.core.ui.*
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import java.util.Locale

/** Isolated presentation host: no authentication override, network, private fixtures or global settings. */
class PremiumComponentsTest {
    @get:Rule val compose=createComposeRule()
    @Test fun sharedSurfacesStayAccessibleAcrossThemesLargeTextAndDisabledActions(){
        val app=ApplicationProvider.getApplicationContext<android.content.Context>()
        val language=InstrumentationRegistry.getArguments().getString("nativeLanguage")?:"ar"
        val config=Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}
        val localized=app.createConfigurationContext(config)
        val resources=localized.resources
        var dark by mutableStateOf(false);var font by mutableFloatStateOf(1f);var enabled by mutableStateOf(true)
        var taps=0;var actions=0
        compose.setContent {
            val density=LocalDensity.current;val view=LocalView.current
            DisposableEffect(view){val before=view.keepScreenOn;view.keepScreenOn=true;onDispose{view.keepScreenOn=before}}
            CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides config,
                LocalLayoutDirection provides if(language=="ar")LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density.density,font)){
                AtharTheme(if(dark)"dark" else "light"){
                    Surface(Modifier.fillMaxSize()){
                        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                            AtharHeroSurface(Modifier.fillMaxWidth()){
                                Text(resources.getString(R.string.welcome_back),style=MaterialTheme.typography.headlineMedium)
                                Text(resources.getString(R.string.auth_description),style=MaterialTheme.typography.bodyLarge)
                            }
                            AtharSectionHeader(resources.getString(R.string.quran),actionLabel=resources.getString(R.string.view_all),onAction={actions++})
                            AtharQuickAction(AtharIcons.MenuBook,resources.getString(R.string.open_reader),{taps++},Modifier.fillMaxWidth().testTag("quick"),emphasized=true,enabled=enabled,reduceMotion=font>1f)
                            AtharStatusPill(resources.getString(R.string.completed),emphasized=true)
                            AtharMetric("12",resources.getString(R.string.lessons),Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        for(variant in listOf(false to 1f,true to 1.5f,false to 2f)){
            compose.runOnIdle{dark=variant.first;font=variant.second;enabled=true}
            compose.onNodeWithText(resources.getString(R.string.quran)).performScrollTo().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            compose.onNodeWithText(resources.getString(R.string.view_all)).performScrollTo().performClick()
            val quick=compose.onNodeWithTag("quick").performScrollTo().assertIsEnabled().assertHasClickAction()
            quick.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Button))
            quick.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            quick.performClick();val before=taps
            compose.runOnIdle{enabled=false}
            quick.assertIsNotEnabled().performTouchInput{click()}
            compose.runOnIdle{assertEquals(before,taps);enabled=true}
            compose.onNodeWithText(resources.getString(R.string.welcome_back)).performScrollTo()
            val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
            app.openFileOutput("premium-shared-$language-${variant.first}-${variant.second}.png",android.content.Context.MODE_PRIVATE).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        assertEquals(3,taps);assertEquals(3,actions)
    }

    @Test fun reducingMotionFinishesTheInFlightTransitionWithinTwoFrames(){
        var page by mutableIntStateOf(1);var reduce by mutableStateOf(false)
        compose.mainClock.autoAdvance=false
        compose.setContent {AtharTheme("light"){AtharPageTransition(page,reduceMotion=reduce){current->val remembered=remember{current};Text("Page $current/$remembered")}}}
        compose.runOnIdle{page=2};compose.mainClock.advanceTimeByFrame()
        // One frame applies the effect; the next applies the snapped transition's composition.
        compose.runOnIdle{reduce=true};compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Page 2/2").assertIsDisplayed()
        compose.onNodeWithText("Page 1/1").assertDoesNotExist()
        compose.runOnIdle{page=3};compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Page 3/3").assertIsDisplayed()
        compose.onNodeWithText("Page 2/2").assertDoesNotExist()
    }

    @Test fun sharedTextColorsMeetContrastAtBothHeroGradientEndpoints(){
        var dark by mutableStateOf(false);var captured:ColorScheme?=null
        compose.setContent{AtharTheme(if(dark)"dark" else "light"){val colors=MaterialTheme.colorScheme;SideEffect{captured=colors};Text("Contrast fixture")}}
        fun contrast(a:Color,b:Color):Float {val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+0.05f)/(minOf(x,y)+0.05f)}
        for(theme in listOf(false,true)){
            compose.runOnIdle{dark=theme}
            compose.runOnIdle{
                val c=requireNotNull(captured)
                for(background in listOf(c.primaryContainer,c.surfaceContainerLow)){
                    assertTrue(contrast(c.onPrimaryContainer,background)>=4.5f)
                    assertTrue(contrast(c.onSurfaceVariant,background)>=4.5f)
                }
                assertTrue(contrast(c.primary,c.surfaceContainerLow)>=4.5f)
                assertTrue(contrast(c.onTertiaryContainer,c.tertiaryContainer)>=4.5f)
            }
        }
    }
    @Test fun reducingMotionDoesNotDiscardTheCurrentPageDraft(){
        var reduce by mutableStateOf(false)
        compose.setContent{AtharTheme("light"){
            AtharPageTransition(1,reduceMotion=reduce){
                var draft by remember{mutableStateOf("")}
                OutlinedTextField(draft,{draft=it},Modifier.testTag("retained-draft"))
            }
        }}
        compose.onNodeWithTag("retained-draft").performTextReplacement("Retained fixture draft")
        compose.runOnIdle{reduce=true}
        compose.onNodeWithTag("retained-draft").assertTextContains("Retained fixture draft")
        compose.runOnIdle{reduce=false}
        compose.onNodeWithTag("retained-draft").assertTextContains("Retained fixture draft")
    }
}
