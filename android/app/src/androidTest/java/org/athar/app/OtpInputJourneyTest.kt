package org.athar.app

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.util.Locale

/** Input-only case: never requests/sends mail or changes a password. */
class OtpInputJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun resetCodeAcceptsArabicKeyboardAndPastedEasternDigitsWithoutSendingMail() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke") == "true")
        val app = ApplicationProvider.getApplicationContext<AtharApplication>()
        val graph = app.graph
        Assert.assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
        fun label(id: Int) = resources.getString(id)
        runBlocking { graph.session.restore(); graph.session.logout(); graph.settings.completeOnboarding(); graph.settings.theme("light") }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) { compose.onAllNodesWithText(label(R.string.forgot_password)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(label(R.string.forgot_password)).performScrollTo().performClick()
            compose.onNodeWithText(label(R.string.enter_reset_code)).performScrollTo().performClick()
            val field = compose.onNode(hasSetTextAction() and hasText(label(R.string.verification_code)))
            field.performScrollTo().performTextInput("٠١٢٣٤٥")
            field.assertTextContains("012345")
            field.performTextReplacement("\u200f۰۰۱ ۲-۳۴\u200e")
            field.assertTextContains("001234")
            Assert.assertNull(graph.session.userId)
        }
    }
}
