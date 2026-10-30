package org.athar.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.Room
import kotlinx.coroutines.launch
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.*
import org.athar.app.core.network.RetrofitTransport
import org.athar.app.core.security.*
import org.athar.app.quran.QuranRepository

open class AtharApplication : Application() {
    open val graph: AppGraph by lazy { AppGraph(this) }
    override fun onCreate() {
        super.onCreate()
        if(android.os.Build.VERSION.SDK_INT>=33 && !Application.getProcessName().startsWith(packageName + ":book_pdf")){
            val manager=getSystemService(android.app.LocaleManager::class.java)
            if(manager.applicationLocales.isEmpty)manager.applicationLocales=android.os.LocaleList.forLanguageTags("ar")
        }
    }
}
class AppGraph(application: Application, val apiBaseUrl: String = BuildConfig.API_BASE_URL,enablePersistentSync:Boolean=true) {
    val partition = java.security.MessageDigest.getInstance("SHA-256").digest(apiBaseUrl.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(12)
    val cipher = PrivateCipher()
    val database = Room.databaseBuilder(application, AppDatabase::class.java, "athar-$partition.db").addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3).build()
    internal val deletionStore = AccountDeletionStore(partition,database,cipher)
    private suspend fun initializeDeletion(){accountDeletion.initialize()}
    val settings = SettingsStore(application)
    val session = SessionRepository(RetrofitTransport(apiBaseUrl), KeystoreTokenStore(application, cipher, namespace = partition), language = { AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "ar" } },ownerBlocked=deletionStore::isBlocked,beforeRestore={initializeDeletion()})
    val content = ContentRepository(session, database.dao(), cipher, language = { AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "ar" } })
    private var persistentSync:org.athar.app.core.sync.PersistentSyncCoordinator?=null
    private fun requestSync(owner:Long,module:org.athar.app.core.sync.SyncModule){persistentSync?.request(owner,module)}
    val quran = QuranRepository(application)
    val reading = org.athar.app.quran.ReadingRepository(database.dao(), cipher, session, index = { quran.data().index },requestSync={requestSync(it,org.athar.app.core.sync.SyncModule.READING)})
    val nightPrayer = org.athar.app.nightprayer.NightPrayerRepository(database, session, cipher,requestSync={requestSync(it,org.athar.app.core.sync.SyncModule.NIGHT_PRAYER)})
    val counters = CounterRepository(database.dao(), cipher, session,requestSync={requestSync(it,org.athar.app.core.sync.SyncModule.COUNTERS)})
    val lessons = org.athar.app.learning.LessonRepository(database.dao(), cipher, session,requestSync={requestSync(it,org.athar.app.core.sync.SyncModule.LESSONS)})
    internal val khatmah = org.athar.app.khatmah.KhatmahRepository(partition, database, cipher, session, requestSync = { requestSync(it, org.athar.app.core.sync.SyncModule.KHATMAH) })
    internal val bookActivity = BookActivityRepository(partition,database,cipher,session,files={org.athar.app.books.bookFiles(application,org.athar.app.books.BookScope(partition,it))},requestSync={requestSync(it,org.athar.app.core.sync.SyncModule.ACTIVITY)},onAcknowledged={content.invalidate("me/activity-summary",it);content.invalidate("home",it)})
    internal val accountDeletion=AccountDeletionCoordinator(application,partition,database,session,deletionStore,enablePersistentSync)
    init{
        if(enablePersistentSync)persistentSync=org.athar.app.core.sync.PersistentSyncCoordinator(application,this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try{accountDeletion.recoverAll()}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){/* Journal and owned bytes remain available for explicit retry. */}
        }
    }
}
