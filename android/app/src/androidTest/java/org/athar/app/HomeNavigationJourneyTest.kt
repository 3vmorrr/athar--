package org.athar.app

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.activity.compose.setContent
import android.content.pm.ActivityInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.athar.app.auth.AuthState
import org.athar.app.core.ui.*
import org.junit.*
import org.junit.Assert.*
import java.util.Locale

/** Actual guarded HTTP fixture account; no real API partition or private row is cleared. */
class HomeNavigationJourneyTest {
    @get:Rule val compose=createEmptyComposeRule()
    @Test fun actualHomeKeepsBilingualNavigationAndOwnedCounts() {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("nativeApiSmoke")=="true")
        val email=requireNotNull(args.getString("homeFixtureEmail"))
        require(email.endsWith("@example.test"))
        val app=ApplicationProvider.getApplicationContext<AtharApplication>()
        val graph=app.graph;assertTrue(graph.apiBaseUrl=="http://127.0.0.1:8004/api/v1/")
        lateinit var vm: AppViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync{vm=AppViewModel(graph)}
        runBlocking{withTimeout(15000){graph.session.state.first{it !is AuthState.Restoring}};graph.session.login(email,"HomeFixture123!")}
        val auth=graph.session.state.value as AuthState.SignedIn
        var language by mutableStateOf("ar");var dark by mutableStateOf(false)
        try {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        var originalOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        scenario.onActivity { activity -> originalOrientation=activity.requestedOrientation;activity.setContent {
            val view=LocalView.current
            DisposableEffect(view){val prior=view.keepScreenOn;view.keepScreenOn=true;onDispose{view.keepScreenOn=prior}}
            val config=Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(language))}
            val context=app.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides config,
                LocalLayoutDirection provides if(language=="ar")LayoutDirection.Rtl else LayoutDirection.Ltr) {
                AtharTheme(if(dark)"dark" else "light"){Surface(Modifier.fillMaxSize()){MainShell(vm,auth)}}
            }
        }}
            for(lang in listOf("ar","en")) {
                compose.runOnIdle{language=lang;dark=lang=="en"}
                val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.forLanguageTag(lang))}).resources
                compose.waitUntil(20000){compose.onAllNodesWithText(resources.getString(R.string.your_journey)).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText("7").performScrollTo().assertIsDisplayed()
                compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToIndex(0)
                compose.onNodeWithText(resources.getString(R.string.your_journey)).performScrollTo().assertIsDisplayed()
                val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
                app.openFileOutput("home-native-$lang.png",android.content.Context.MODE_PRIVATE).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                compose.onNodeWithText(resources.getString(R.string.account),useUnmergedTree=true).performClick()
                compose.waitUntil(15000){compose.onAllNodesWithText(email,substring=true).fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithText(resources.getString(R.string.home),useUnmergedTree=true).performClick()
            }
            try {
                scenario.onActivity{it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
                compose.waitUntil(15000){val bounds=compose.onRoot().fetchSemanticsNode().boundsInRoot;bounds.width>bounds.height}
                val resources=app.createConfigurationContext(Configuration(app.resources.configuration).apply{setLocale(Locale.ENGLISH)}).resources
                compose.onNodeWithText(resources.getString(R.string.account),useUnmergedTree=true).performScrollTo().performClick()
                compose.waitUntil(15000){compose.onAllNodesWithText(email,substring=true).fetchSemanticsNodes().isNotEmpty()}
                val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
                app.openFileOutput("home-native-en-landscape.png",android.content.Context.MODE_PRIVATE).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                compose.onNodeWithText(resources.getString(R.string.home),useUnmergedTree=true).performScrollTo().performClick()
            } finally {scenario.onActivity{it.requestedOrientation=originalOrientation}}
        }
        } finally {runBlocking{graph.session.logout()}}
    }
}
