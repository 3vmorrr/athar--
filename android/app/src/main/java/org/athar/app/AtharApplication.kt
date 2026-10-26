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
        org.athar.app.notifications.NotificationChannels.ensure(this)
    }
}
class AppGraph(application: Application, val apiBaseUrl: String = BuildConfig.API_BASE_URL,enablePersistentSync:Boolean=true) {
    val partition = java.security.MessageDigest.getInstance("SHA-256").digest(apiBaseUrl.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(12)
    val cipher = PrivateCipher()
    val database = Room.databaseBuilder(application, AppDatabase::class.java, "athar-$partition.db").build()
    val settings = SettingsStore(application)
    val session = SessionRepository(RetrofitTransport(apiBaseUrl), KeystoreTokenStore(application, cipher, namespace = partition), language = { AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "ar" } })
    val content = ContentRepository(session, database.dao(), cipher, language = { AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "ar" } })
    val notificationPreferences = org.athar.app.notifications.NotificationPreferenceRepository(database.dao(), session, cipher)
    val notificationPolicyLedger = org.athar.app.notifications.NotificationPolicyLedgerRepository(database.dao(), session, cipher)
    val reminderStore = org.athar.app.notifications.LocalReminderStore(database.dao(), session, cipher)
    val reminders = org.athar.app.notifications.LocalReminderCoordinator(application, partition, session, reminderStore, notificationPolicyLedger)
    private var persistentSync:org.athar.app.core.sync.PersistentSyncCoordinator?=null
    private fun requestSync(owner:Long,module:org.athar.app.core.sync.SyncModule){persistentSync?.request(owner,module)}
    val quran = QuranRepository(application)
    val reading = org.athar.app.quran.ReadingRepository(database.dao(), cipher, session, index = { quran.data().index },requestSync={requestSync(it,org.athar.app.core.sync.SyncModule.READING)})
    val nightPrayer = org.athar.app.nightprayer.NightPrayerRepository(database, session, cipher,requestSync={requestSync(it,org.athar.app.core.sync.SyncModule.NIGHT_PRAYER)})
    val counters = CounterRepository(database.dao(), cipher, session,requestSync={requestSync(it,org.athar.app.core.sync.SyncModule.COUNTERS)})
    val lessons = org.athar.app.learning.LessonRepository(database.dao(), cipher, session,requestSync={requestSync(it,org.athar.app.core.sync.SyncModule.LESSONS)})
    init{if(enablePersistentSync)persistentSync=org.athar.app.core.sync.PersistentSyncCoordinator(application,this)}
}
