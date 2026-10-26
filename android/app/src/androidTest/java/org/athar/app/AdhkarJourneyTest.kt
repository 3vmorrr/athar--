package org.athar.app

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import org.junit.*
import java.util.Locale

class AdhkarJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun categoriesAdvanceOnlyAfterRealCountAndPreserveUnspecifiedCounts() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("nativeApiSmoke") == "true")
        val app = ApplicationProvider.getApplicationContext<AtharApplication>()
        val graph = app.graph
        Assert.assertTrue(graph.apiBaseUrl.endsWith(":8004/api/v1/"))
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage") ?: "ar"
        val resources = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
        fun label(id: Int) = resources.getString(id)
        runBlocking {
            graph.session.restore()
            graph.settings.completeOnboarding()
            graph.settings.theme("light")
            graph.session.login("native.reader@example.test", "NativeFixture123!")
            // Explicit fixture reset adopts its current revision; another device may already have progress.
            graph.counters.useServer(1,3)
            graph.counters.useServer(2,1)
            graph.counters.tap(1, 3, reset = true)
            graph.counters.tap(2, 1, reset = true)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(25_000) { compose.onAllNodesWithText(label(R.string.your_journey)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(label(R.string.adhkar)).performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Interface counter fixture").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("adhkar-entries").performScrollToNode(hasText(label(R.string.count_action)))
                compose.onNodeWithText(label(R.string.count_action)).performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText(resources.getString(R.string.progress_count, 1, 3)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(label(R.string.reset_counter)).performScrollTo().performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText(resources.getString(R.string.progress_count, 0, 3)).fetchSemanticsNodes().isNotEmpty() }
                repeat(3) { index ->
                    compose.onNodeWithTag("adhkar-entries").performScrollToNode(hasText(label(R.string.count_action)))
                    compose.onNodeWithText(label(R.string.count_action)).performClick()
                    if (index < 2) compose.waitUntil(10_000) { compose.onAllNodesWithText(resources.getString(R.string.progress_count, index + 1, 3)).fetchSemanticsNodes().isNotEmpty() }
                }
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Second counter fixture").fetchSemanticsNodes().isNotEmpty() }
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                File(app.filesDir,"adhkar-$language-counter.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                compose.onNodeWithText(label(R.string.count_action)).performScrollTo().performClick()
                compose.onNodeWithTag("adhkar-entries").performScrollToNode(hasText(label(R.string.adhkar_section_end)))
                compose.onNodeWithText(label(R.string.adhkar_section_end)).assertIsDisplayed()
                compose.onNodeWithText("Other section").performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Unspecified count fixture").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("adhkar-entries").performScrollToNode(hasText(label(R.string.adhkar_no_fixed_count)))
                compose.onNodeWithText(label(R.string.adhkar_no_fixed_count)).assertIsDisplayed()
                compose.onNodeWithText(label(R.string.count_action)).assertDoesNotExist()
                Assert.assertEquals(3, runBlocking { graph.counters.observe(requireNotNull(graph.session.userId), 1, 3).first().count })
            }
        } finally { runBlocking { graph.session.logout() } }
    }
}
