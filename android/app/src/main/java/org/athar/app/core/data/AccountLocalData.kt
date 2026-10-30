package org.athar.app.core.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import org.athar.app.auth.SessionRepository
import org.athar.app.learning.LessonRepository
import org.athar.app.nightprayer.NightPrayerRepository
import org.athar.app.AppGraph
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Validate everything before the caller opens/truncates a user-selected export file. */
suspend fun retainedAccountData(owner:Long,session:SessionRepository,lessons:LessonRepository,night:NightPrayerRepository):JsonObject {
    session.requireVerifiedOwner(owner)
    val progress=lessons.retained(owner)
    val nights=JsonArray().apply{night.observe(owner).first().forEach{add(it.second)}}
    session.requireVerifiedOwner(owner)
    return JsonObject().apply{
        add("device_lesson_progress",Gson().toJsonTree(progress))
        add("device_night_sessions",nights)
    }
}

/** One owned Room snapshot; unreadable protected state aborts instead of producing a partial copy. */
internal suspend fun retainedAccountData(owner: Long, graph: AppGraph): JsonObject {
    graph.session.requireVerifiedOwner(owner)
    graph.bookActivity.capture(owner)
    return AccountLocalSnapshot(graph.partition, graph.database, graph.cipher, graph.session, graph.lessons, graph.nightPrayer, graph.reading, graph.counters).read(owner)
}

internal class AccountLocalSnapshot(
    private val namespace: String,
    private val database: AppDatabase,
    private val cipher: org.athar.app.core.security.PrivateCipher,
    private val session: SessionRepository,
    private val lessons: LessonRepository,
    private val night: NightPrayerRepository,
    private val reading: org.athar.app.quran.ReadingRepository,
    private val counters: CounterRepository,
) {
    suspend fun read(owner: Long): JsonObject = withContext(Dispatchers.IO) {
        val lease = session.lease(owner)
        database.withTransaction {
            session.requireLease(lease)
            val gson = Gson()
            val result = JsonObject().apply {
                addProperty("owner_id", owner); addProperty("namespace", namespace)
                add("device_lesson_progress", gson.toJsonTree(lessons.retained(owner)))
                night.retainedExport(owner).entrySet().forEach { add(it.key, it.value) }
                add("device_reading_position", gson.toJsonTree(reading.saved(owner)))
                add("device_adhkar_counters", gson.toJsonTree(counters.retained(owner)))
                add("device_book_activity", retainedBookActivity(namespace, owner, database.dao(), cipher))
                add("device_khatmah_state", org.athar.app.khatmah.retainedKhatmah(namespace, owner, database.dao(), cipher))
                org.athar.app.nightprayer.retainedNightPlans(owner, namespace, database.dao(), cipher).entrySet().forEach { add(it.key, it.value) }
                add("device_night_reading_positions", org.athar.app.nightprayer.retainedNightReading(owner, namespace, database.dao(), cipher))
                org.athar.app.nightprayer.retainedNightTimers(owner, namespace, database.dao(), cipher).entrySet().forEach { add(it.key, it.value) }
                org.athar.app.books.retainedBookData(namespace, owner, database.dao(), cipher, session).entrySet().forEach { add(it.key, it.value) }
            }
            session.requireLease(lease)
            AccountExportPolicy.requireDevice(owner, namespace, result)
            result
        }
    }
}
