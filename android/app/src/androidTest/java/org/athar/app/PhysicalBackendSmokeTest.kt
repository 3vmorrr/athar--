package org.athar.app

import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.athar.app.core.network.RetrofitTransport
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Read-only real development API probe. Does not reset an existing installation or sign into a real account. */
class PhysicalBackendSmokeTest {
    @Test fun actualApplicationReachesUsbBackendAndUsesArabicRtlLight() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("physicalApiSmoke") == "true")
        val app = ApplicationProvider.getApplicationContext<AtharApplication>()
        val graph = app.graph
        assertEquals("http://127.0.0.1:8002/api/v1/", graph.apiBaseUrl)
        val health = runBlocking { RetrofitTransport(graph.apiBaseUrl).request("GET", "health") }
        assertEquals("available", health.asJsonObject["status"].asString)
        val settings = runBlocking { graph.settings.settings.first() }
        assertEquals("light", settings.theme)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("ar", activity.resources.configuration.locales[0].language)
                assertEquals(View.LAYOUT_DIRECTION_RTL, activity.resources.configuration.layoutDirection)
            }
        }
        File(app.filesDir, "physical-backend-outcome.txt").writeText("model=${Build.MODEL}; api=${Build.VERSION.SDK_INT}; real USB API=available; language=ar; direction=RTL; selected theme=light; existing data retained; onboardingComplete=${settings.onboardingComplete}")
    }
}
