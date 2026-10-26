package org.athar.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.Room
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.RetrofitTransport
import org.athar.app.core.security.*
import org.athar.app.quran.QuranRepository

open class AtharApplication : Application() {
    open val graph: AppGraph by lazy { AppGraph(this) }
    override fun onCreate() {
        super.onCreate()
        if(android.os.Build.VERSION.SDK_INT>=33){
            val manager=getSystemService(android.app.LocaleManager::class.java)
            if(manager.applicationLocales.isEmpty)manager.applicationLocales=android.os.LocaleList.forLanguageTags("ar")
        }
    }
}
class AppGraph(application: Application, val apiBaseUrl: String = BuildConfig.API_BASE_URL) {
    val partition = java.security.MessageDigest.getInstance("SHA-256").digest(apiBaseUrl.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(12)
    val cipher = PrivateCipher()
    val database = Room.databaseBuilder(application, AppDatabase::class.java, "athar-$partition.db").build()
    val settings = SettingsStore(application)
    val session = SessionRepository(RetrofitTransport(apiBaseUrl), KeystoreTokenStore(application, cipher, namespace = partition), language = { AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "ar" } })
    val content = ContentRepository(session, database.dao(), cipher, language = { AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "ar" } })
    val quran = QuranRepository(application)
    val reading = org.athar.app.quran.ReadingRepository(database.dao(), cipher, session, index = { quran.data().index })
    val nightPrayer = org.athar.app.nightprayer.NightPrayerRepository(database, session, cipher)
    val counters = CounterRepository(database.dao(), cipher, session)
    val lessons = org.athar.app.learning.LessonRepository(database.dao(), cipher, session)
}
