package org.athar.app
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Run only after installing onto a clean dedicated emulator app sandbox. */
class FreshInstallTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun englishDeviceStillStartsArabicRtlAndLight() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("freshInstallSmoke")=="true")
        compose.waitUntil(20_000){compose.onAllNodesWithText("القرآن معك أينما كنت").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("القرآن معك أينما كنت").assertIsDisplayed()
        val activity=compose.activity
        assertEquals("ar",activity.resources.configuration.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_RTL,activity.resources.configuration.layoutDirection)
        val settings=runBlocking{(activity.application as AtharApplication).graph.settings.settings.first()}
        assertEquals("light",settings.theme);assertFalse(settings.onboardingComplete)
    }
}
