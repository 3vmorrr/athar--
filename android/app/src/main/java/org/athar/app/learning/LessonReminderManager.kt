package org.athar.app.learning

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import org.athar.app.R
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.security.PrivateCipher
import org.athar.app.notifications.LocalReminderCoordinator
import org.athar.app.notifications.LocalReminderSpec
import java.time.Instant
import java.time.ZoneId

data class LessonReminderBinding(
    val lessonId: Int,
    val eventId: Long?,
    val eventRevision: Int,
    val eventStartMillis: Long,
    val sourceTimezone: String,
    val workId: String,
)

class LessonReminderBindingStore(
    private val dao: AtharDao,
    private val session: SessionRepository,
    private val cipher: PrivateCipher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private fun key(lessonId: Int): String {
        require(lessonId > 0)
        return "private:lesson-reminder-binding:$lessonId"
    }

    suspend fun read(lessonId: Int, expectedOwner: Long? = session.userId): LessonReminderBinding? {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val row = dao.cache(owner, key(lessonId)) ?: return null
        val root = JsonParser.parseString(cipher.open(row.payloadCipher)).asJsonObject
        require(root["version"]?.asInt == 1)
        val value = LessonReminderBinding(
            lessonId = root["lesson_id"]?.asInt ?: error("missing lesson"),
            eventId = root["event_id"]?.takeUnless { it.isJsonNull }?.asLong,
            eventRevision = root["event_revision"]?.asInt ?: error("missing revision"),
            eventStartMillis = root["event_start_ms"]?.asLong ?: error("missing start"),
            sourceTimezone = root["source_timezone"]?.asString ?: error("missing timezone"),
            workId = root["work_id"]?.asString ?: error("missing work id"),
        )
        require(value.lessonId == lessonId)
        require(value.eventRevision > 0 && value.eventStartMillis > 0)
        require(LocalReminderSpec.validId(value.workId))
        ZoneId.of(value.sourceTimezone)
        session.requireVerifiedOwner(owner)
        return value
    }

    suspend fun save(value: LessonReminderBinding, expectedOwner: Long? = session.userId) {
        val owner = session.requireVerifiedOwner(expectedOwner)
        require(value.lessonId > 0 && value.eventRevision > 0 && value.eventStartMillis > 0)
        require(LocalReminderSpec.validId(value.workId))
        ZoneId.of(value.sourceTimezone)
        val payload = JsonObject().apply {
            addProperty("version", 1)
            addProperty("lesson_id", value.lessonId)
            if (value.eventId == null) add("event_id", null) else addProperty("event_id", value.eventId)
            addProperty("event_revision", value.eventRevision)
            addProperty("event_start_ms", value.eventStartMillis)
            addProperty("source_timezone", value.sourceTimezone)
            addProperty("work_id", value.workId)
        }.toString()
        dao.cache(CachedDocument(owner, key(value.lessonId), cipher.seal(payload), now()))
        session.requireVerifiedOwner(owner)
    }

    suspend fun remove(lessonId: Int, expectedOwner: Long? = session.userId) {
        val owner = session.requireVerifiedOwner(expectedOwner)
        dao.removeCache(owner, key(lessonId))
        session.requireVerifiedOwner(owner)
    }
}

enum class LessonReminderPlanFailure { UNAVAILABLE, TOO_LATE }

sealed interface LessonReminderPlanResult {
    data class Ready(val spec: LocalReminderSpec, val binding: LessonReminderBinding) : LessonReminderPlanResult
    data class Rejected(val reason: LessonReminderPlanFailure) : LessonReminderPlanResult
}

object LessonReminderPlanner {
    fun plan(
        event: UpcomingLesson,
        choice: LessonReminderChoice,
        title: String,
        body: String,
        now: Instant,
    ): LessonReminderPlanResult {
        val start = event.start
        if (
            event.id <= 0 || event.revision <= 0 || event.status != EventStatus.SCHEDULED ||
            !event.remindersEnabled || start == null || choice.lessonId != event.id
        ) return LessonReminderPlanResult.Rejected(LessonReminderPlanFailure.UNAVAILABLE)

        try { ZoneId.of(event.scheduleTimezone) }
        catch (_: Exception) { return LessonReminderPlanResult.Rejected(LessonReminderPlanFailure.UNAVAILABLE) }

        val reminderAt = start.minusSeconds(choice.leadMinutes * 60L)
        if (!reminderAt.isAfter(now)) return LessonReminderPlanResult.Rejected(LessonReminderPlanFailure.TOO_LATE)

        val workId = "lesson-" + event.id
        val sourceId = event.eventId ?: event.id.toLong()
        val idempotency = "lesson:" + sourceId + ":r" + event.revision + ":at" + start.epochSecond + ":lead" + choice.leadMinutes
        return LessonReminderPlanResult.Ready(
            LocalReminderSpec(
                id = workId,
                category = "lesson_reminders",
                destinationToken = "lesson:" + event.id,
                title = title,
                body = body,
                scheduledAtMillis = reminderAt.toEpochMilli(),
                expiresAtMillis = start.toEpochMilli(),
                idempotencyKey = idempotency,
            ).validated(),
            LessonReminderBinding(
                lessonId = event.id,
                eventId = event.eventId,
                eventRevision = event.revision,
                eventStartMillis = start.toEpochMilli(),
                sourceTimezone = event.scheduleTimezone,
                workId = workId,
            ),
        )
    }
}

enum class LessonReminderEnableResult { SCHEDULED, TOO_LATE, UNAVAILABLE }

enum class LessonReminderReconcileResult { NO_CHOICE, UNCHANGED, RESCHEDULED, REMOVED, TOO_LATE }

class LessonReminderManager(
    context: Context,
    private val session: SessionRepository,
    private val choices: LessonReminderChoiceStore,
    private val bindings: LessonReminderBindingStore,
    private val reminders: LocalReminderCoordinator,
    private val now: () -> Instant = Instant::now,
) {
    private val app = context.applicationContext

    private fun plan(event: UpcomingLesson, choice: LessonReminderChoice): LessonReminderPlanResult =
        LessonReminderPlanner.plan(
            event,
            choice,
            event.title,
            app.getString(R.string.lesson_reminder_notification_body, choice.leadMinutes),
            now(),
        )

    suspend fun enable(event: UpcomingLesson, expectedOwner: Long? = session.userId): LessonReminderEnableResult {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val choice = LessonReminderChoice(event.id)
        val planned = plan(event, choice)
        if (planned is LessonReminderPlanResult.Rejected) return planned.toEnableResult()
        planned as LessonReminderPlanResult.Ready
        choices.enable(event.id, owner)
        try {
            reminders.save(planned.spec, owner)
            bindings.save(planned.binding, owner)
        } catch (failure: CancellationException) {
            runCatching { choices.disable(event.id, owner) }
            throw failure
        } catch (failure: Exception) {
            runCatching { reminders.remove(planned.binding.workId, owner) }
            runCatching { choices.disable(event.id, owner) }
            throw failure
        }
        return LessonReminderEnableResult.SCHEDULED
    }

    suspend fun disable(lessonId: Int, expectedOwner: Long? = session.userId) {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val binding = bindings.read(lessonId, owner)
        reminders.remove(binding?.workId ?: "lesson-" + lessonId, owner)
        bindings.remove(lessonId, owner)
        choices.disable(lessonId, owner)
    }

    suspend fun ensureScheduled(
        event: UpcomingLesson,
        expectedOwner: Long? = session.userId,
    ): LessonReminderEnableResult? {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val choice = choices.current(owner, event.id) ?: return null
        val binding = bindings.read(event.id, owner)
        if (
            binding != null &&
            binding.eventRevision == event.revision &&
            binding.eventStartMillis == event.start?.toEpochMilli() &&
            binding.sourceTimezone == event.scheduleTimezone &&
            event.status == EventStatus.SCHEDULED &&
            event.remindersEnabled
        ) return LessonReminderEnableResult.SCHEDULED

        val planned = plan(event, choice)
        if (planned is LessonReminderPlanResult.Rejected) {
            disable(event.id, owner)
            return planned.toEnableResult()
        }
        planned as LessonReminderPlanResult.Ready
        reminders.save(planned.spec, owner)
        bindings.save(planned.binding, owner)
        return LessonReminderEnableResult.SCHEDULED
    }

    suspend fun reconcileAuthoritative(
        event: UpcomingLesson,
        expectedOwner: Long? = session.userId,
    ): LessonReminderReconcileResult {
        val owner = session.requireVerifiedOwner(expectedOwner)
        val choice = choices.current(owner, event.id) ?: return LessonReminderReconcileResult.NO_CHOICE
        val binding = bindings.read(event.id, owner)

        if (
            event.status != EventStatus.SCHEDULED ||
            event.start == null ||
            !event.remindersEnabled
        ) {
            disable(event.id, owner)
            return LessonReminderReconcileResult.REMOVED
        }

        if (
            binding != null &&
            binding.eventRevision == event.revision &&
            binding.eventStartMillis == event.start.toEpochMilli() &&
            binding.sourceTimezone == event.scheduleTimezone
        ) return LessonReminderReconcileResult.UNCHANGED

        val planned = plan(event, choice)
        if (planned is LessonReminderPlanResult.Rejected) {
            disable(event.id, owner)
            return if (planned.reason == LessonReminderPlanFailure.TOO_LATE) {
                LessonReminderReconcileResult.TOO_LATE
            } else {
                LessonReminderReconcileResult.REMOVED
            }
        }
        planned as LessonReminderPlanResult.Ready
        reminders.save(planned.spec, owner)
        bindings.save(planned.binding, owner)
        return LessonReminderReconcileResult.RESCHEDULED
    }

    private fun LessonReminderPlanResult.Rejected.toEnableResult() = when (reason) {
        LessonReminderPlanFailure.TOO_LATE -> LessonReminderEnableResult.TOO_LATE
        LessonReminderPlanFailure.UNAVAILABLE -> LessonReminderEnableResult.UNAVAILABLE
    }
}
