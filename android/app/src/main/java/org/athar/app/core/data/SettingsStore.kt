package org.athar.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.preferences by preferencesDataStore("athar_preferences")
data class AppSettings(val onboardingComplete: Boolean = false, val theme: String = "light", val quranFontSize: Int = 28)
class SettingsStore(private val context: Context) {
    private val onboarding = booleanPreferencesKey("onboarding_complete")
    private val theme = stringPreferencesKey("theme")
    private val size = intPreferencesKey("quran_font_size")
    val settings = context.preferences.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { AppSettings(it[onboarding] ?: false, it[theme] ?: "light", (it[size] ?: 28).coerceIn(18, 48)) }
    suspend fun completeOnboarding() { context.preferences.edit { it[onboarding] = true } }
    suspend fun theme(value: String) { require(value in listOf("light", "dark", "system")); context.preferences.edit { it[theme] = value } }
    suspend fun quranFontSize(value: Int) { context.preferences.edit { it[size] = value.coerceIn(18, 48) } }
}
