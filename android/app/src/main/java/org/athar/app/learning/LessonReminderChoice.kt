package org.athar.app.learning

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.core.security.PrivateCipher

data class LessonReminderChoice(
    val lessonId: Int,
    val leadMinutes: Int = DEFAULT_LEAD_MINUTES,
) {
    init {
        require(lessonId > 0)
        require(leadMinutes == DEFAULT_LEAD_MINUTES)
    }
    companion object { const val DEFAULT_LEAD_MINUTES = 15 }
}

class LessonReminderChoiceStore(
    private val dao: AtharDao,
    private val session: SessionRepository,
    private val cipher: PrivateCipher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private fun key(lessonId: Int): String {
        require(lessonId > 0)
        return "private:lesson-reminder-choice:$lessonId"
    }

    fun observe(owner: Long, lessonId: Int): Flow<ProtectedRead<LessonReminderChoice?>> = flow {
        session.requireVerifiedOwner(owner)
        emitAll(
            dao.observeCache(owner, key(lessonId)).map { row ->
                session.requireVerifiedOwner(owner)
                row?.let { decode(it.payloadCipher, lessonId) }
            },
        )
    }.protectedReads()

    suspend fun enable(lessonId: Int, expectedOwner: Long? = session.userId): LessonReminderChoice {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val value = LessonReminderChoice(lessonId)
        val payload = JsonObject().apply {
            addProperty("version", 1)
            addProperty("lesson_id", value.lessonId)
            addProperty("lead_minutes", value.leadMinutes)
        }.toString()
        session.requireVerifiedOwner(owner)
        dao.cache(CachedDocument(owner, key(lessonId), cipher.seal(payload), now()))
        session.requireVerifiedOwner(owner)
        return value
    }

    suspend fun disable(lessonId: Int, expectedOwner: Long? = session.userId) {
        val owner = session.requireVerifiedOwner(expectedOwner)
        dao.removeCache(owner, key(lessonId))
        session.requireVerifiedOwner(owner)
    }

    fun observeAll(owner: Long): Flow<ProtectedRead<List<LessonReminderChoice>>> = flow {
        session.requireVerifiedOwner(owner)
        emitAll(
            dao.observeCaches(owner, "private:lesson-reminder-choice:%").map { rows ->
                session.requireVerifiedOwner(owner)
                require(rows.size <= 128)
                val values = rows.map { row ->
                    val lessonId = row.cacheKey.substringAfterLast(':').toIntOrNull()
                        ?: error("invalid lesson reminder key")
                    decode(row.payloadCipher, lessonId)
                }.sortedBy { it.lessonId }
                require(values.map { it.lessonId }.toSet().size == values.size)
                values
            },
        )
    }.protectedReads()

    suspend fun all(expectedOwner: Long? = session.userId): List<LessonReminderChoice> {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val rows = dao.caches(owner, "private:lesson-reminder-choice:%")
        require(rows.size <= 128)
        val values = rows.map { row ->
            val lessonId = row.cacheKey.substringAfterLast(':').toIntOrNull()
                ?: error("invalid lesson reminder key")
            decode(row.payloadCipher, lessonId)
        }.sortedBy { it.lessonId }
        require(values.map { it.lessonId }.toSet().size == values.size)
        session.requireVerifiedOwner(owner)
        return values
    }

    suspend fun current(owner: Long, lessonId: Int): LessonReminderChoice? {
        session.requireVerifiedOwner(owner)
        val row = dao.cache(owner, key(lessonId)) ?: return null
        val value = decode(row.payloadCipher, lessonId)
        session.requireVerifiedOwner(owner)
        return value
    }

    private fun decode(ciphertext: String, expectedLesson: Int): LessonReminderChoice {
        val root = JsonParser.parseString(cipher.open(ciphertext)).asJsonObject
        require(root["version"]?.asInt == 1)
        val lessonId = root["lesson_id"]?.asInt ?: error("missing lesson id")
        val lead = root["lead_minutes"]?.asInt ?: error("missing lead")
        require(lessonId == expectedLesson)
        return LessonReminderChoice(lessonId, lead)
    }
}
