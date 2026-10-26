package org.athar.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.test.platform.app.InstrumentationRegistry

/** Test APK only. Production has no endpoint/authentication override hook. */
class AtharTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application = super.newApplication(cl, AtharTestApplication::class.java.name, context)
    override fun onStart() {
        val language = InstrumentationRegistry.getArguments().getString("nativeLanguage")
        if (language != null) {
            require(language in setOf("ar", "en"))
            runOnMainSync { androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(language)) }
        }
        super.onStart()
    }
}
class AtharTestApplication : AtharApplication() {
    override val graph by lazy {
        val fixture = InstrumentationRegistry.getArguments().getString("nativeApiSmoke") == "true"
        val fixtureHost = InstrumentationRegistry.getArguments().getString("nativeFixtureHost") ?: "10.0.2.2"
        require(fixtureHost in setOf("10.0.2.2", "127.0.0.1")) { "Native fixtures must use an emulator bridge or explicit USB reverse." }
        AppGraph(this, if (fixture) "http://$fixtureHost:8004/api/v1/" else BuildConfig.API_BASE_URL,enablePersistentSync=false)
    }
}
