package org.athar.app

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.athar.app.core.network.ApiException
import java.util.Locale

/** Presentation-state injection only: no authentication attempt, mail or password mutation. */
class AuthFeedbackJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun accountErrorsRenderLocalizedMessagesAndHideUnknownTransportDetails() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke") == "true")
        val app = ApplicationProvider.getApplicationContext<AtharApplication>()
        val graph = app.graph
        Assert.assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
        fun label(id: Int) = resources.getString(id)
        runBlocking { graph.session.restore(); graph.session.logout(); graph.settings.completeOnboarding(); graph.settings.theme("light") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodesWithText(label(R.string.forgot_password)).fetchSemanticsNodes().isNotEmpty() }
            listOf("invalid_credentials" to R.string.auth_invalid_credentials, "invalid_or_expired_code" to R.string.auth_invalid_code, "unexpected_internal_details" to R.string.network_error).forEach { (code, resource) ->
                val remoteMessage = "Untrusted remote details for $code"
                scenario.onActivity { ViewModelProvider(it)[AppViewModel::class.java].perform { throw ApiException(422, code, remoteMessage) } }
                compose.waitUntil(10_000) { compose.onAllNodesWithText(label(resource)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(label(resource)).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(code).assertDoesNotExist()
                compose.onNodeWithText(remoteMessage).assertDoesNotExist()
            }
        }
    }
}
