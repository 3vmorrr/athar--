package org.athar.app.quran

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.athar.app.AtharApplication
import org.athar.app.R
import org.athar.app.auth.AuthState
import org.athar.app.auth.SessionRepository
import org.athar.app.core.data.AtharDao
import org.athar.app.core.data.CachedDocument
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.core.security.PrivateCipher
import org.athar.app.notifications.LocalReminderCoordinator
import org.athar.app.notifications.LocalReminderSpec
import org.athar.app.notifications.LocalReminderStore

data class QuranWirdRule(
    val enabled: Boolean,
    val targetAyahs: Int,
    val time: String,
    val days: Set<Int>,
    val timezone: String,
    val revision: Int,
    val activatedAtMillis: Long,
    val quietStart: String? = null,
    val quietEnd: String? = null,
) {
    fun validated(): QuranWirdRule {
        require(targetAyahs in 1..200)
        LocalTime.parse(time)
        require(days.isNotEmpty() && days.all { it in 1..7 })
        ZoneId.of(timezone)
        require(revision > 0 && activatedAtMillis > 0)
        require((quietStart == null) == (quietEnd == null))
        quietStart?.let { LocalTime.parse(it) }
        quietEnd?.let { LocalTime.parse(it) }
        return this
    }
}

data class QuranWirdDayProgress(
    val date: String,
    val baselineGlobal: Int,
    val highestGlobal: Int,
) {
    init {
        LocalDate.parse(date)
        require(baselineGlobal in 1..QuranIndex.TOTAL_AYAHS)
        require(highestGlobal in baselineGlobal..QuranIndex.TOTAL_AYAHS)
    }

    fun advancedAyahs(): Int = highestGlobal - baselineGlobal
    fun completed(rule: QuranWirdRule): Boolean = advancedAyahs() >= rule.targetAyahs
}

data class QuranWirdState(
    val rule: QuranWirdRule,
    val progress: QuranWirdDayProgress?,
)

object QuranWirdProgressTracker {
    fun advance(
        rule: QuranWirdRule,
        existing: QuranWirdDayProgress?,
        date: LocalDate,
        previousGlobal: Int?,
        currentGlobal: Int,
    ): QuranWirdDayProgress {
        rule.validated()
        require(currentGlobal in 1..QuranIndex.TOTAL_AYAHS)
        previousGlobal?.let { require(it in 1..QuranIndex.TOTAL_AYAHS) }
        if (existing == null || existing.date != date.toString()) {
            val baseline = previousGlobal ?: currentGlobal
            return QuranWirdDayProgress(
                date = date.toString(),
                baselineGlobal = baseline,
                highestGlobal = maxOf(baseline, currentGlobal),
            )
        }
        return existing.copy(highestGlobal = maxOf(existing.highestGlobal, currentGlobal))
    }
}

object QuranWirdPlanner {
    fun plan(
        rule: QuranWirdRule,
        progress: QuranWirdDayProgress?,
        now: Instant,
        title: String,
        body: String,
        horizonDays: Int = 14,
    ): List<LocalReminderSpec> {
        rule.validated()
        if (!rule.enabled) return emptyList()
        require(horizonDays in 1..45)
        val zone = ZoneId.of(rule.timezone)
        val localNow = now.atZone(zone)
        val reminderTime = LocalTime.parse(rule.time)
        val next = (0 until horizonDays).firstNotNullOfOrNull { offset ->
            val date = localNow.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek.value !in rule.days) return@firstNotNullOfOrNull null
            if (progress?.date == date.toString() && progress.completed(rule)) return@firstNotNullOfOrNull null

            val due = ZonedDateTime.of(date, reminderTime, zone).toInstant()
            val expires = date.plusDays(1).atStartOfDay(zone).toInstant()
            if (!expires.isAfter(now)) return@firstNotNullOfOrNull null
            if (!due.isAfter(now)) {
                if (date != localNow.toLocalDate()) return@firstNotNullOfOrNull null
                if (rule.activatedAtMillis >= due.toEpochMilli()) return@firstNotNullOfOrNull null
            }

            val dateKey = date.format(DateTimeFormatter.BASIC_ISO_DATE)
            LocalReminderSpec(
                id = "quran-wird-" + dateKey,
                category = "quran_routine",
                destinationToken = "quran",
                title = title,
                body = body,
                scheduledAtMillis = due.toEpochMilli(),
                expiresAtMillis = expires.toEpochMilli(),
                idempotencyKey = "quran-wird:" + date,
                quietStart = rule.quietStart,
                quietEnd = rule.quietEnd,
                timezone = if (rule.quietStart == null) null else rule.timezone,
            ).validated()
        }
        return listOfNotNull(next)
    }
}

class QuranWirdManager(
    context: Context,
    private val partition: String,
    private val dao: AtharDao,
    private val session: SessionRepository,
    private val cipher: PrivateCipher,
    private val reminderStore: LocalReminderStore,
    private val reminders: LocalReminderCoordinator,
    private val now: () -> Instant = Instant::now,
) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val cacheKey = "private:quran-wird-state:v1"
    private val prefix = "quran-wird-"
    private val maintenance = QuranWirdMaintenanceSchedule(app, partition)

    init {
        scope.launch {
            var previousOwner: Long? = null
            session.state.collect { state ->
                val owner = (state as? AuthState.SignedIn)?.user?.id
                if (state !is AuthState.Restoring && owner != previousOwner) {
                    previousOwner?.let(maintenance::cancel)
                    previousOwner = owner
                    owner?.let {
                        maintenance.schedule(it)
                        try {
                            reconcile(it)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Exception) {
                            // Encrypted state remains untouched for a later retry.
                        }
                    }
                }
            }
        }
    }

    fun observe(owner: Long): Flow<ProtectedRead<QuranWirdState?>> = flow {
        session.requireVerifiedOwner(owner)
        emitAll(
            dao.observeCache(owner, cacheKey).map { row ->
                session.requireVerifiedOwner(owner)
                row?.let { decode(cipher.open(it.payloadCipher)) }
            },
        )
    }.protectedReads()

    suspend fun saveRule(
        owner: Long,
        targetAyahs: Int,
        time: LocalTime,
        days: Set<Int>,
        timezone: ZoneId,
        quietStart: LocalTime? = null,
        quietEnd: LocalTime? = null,
    ): QuranWirdState = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val current = load(owner)
        val revision = (current?.rule?.revision ?: 0) + 1
        val rule = QuranWirdRule(
            enabled = true,
            targetAyahs = targetAyahs,
            time = time.toString(),
            days = days.toSortedSet(),
            timezone = timezone.id,
            revision = revision,
            activatedAtMillis = now().toEpochMilli(),
            quietStart = quietStart?.toString(),
            quietEnd = quietEnd?.toString(),
        ).validated()
        val state = QuranWirdState(rule, current?.progress)
        persist(owner, state)
        reconcileLocked(owner, state)
        maintenance.schedule(owner)
        state
    }

    suspend fun disable(owner: Long): QuranWirdState? = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val current = load(owner) ?: return@withLock null
        val disabled = current.copy(
            rule = current.rule.copy(
                enabled = false,
                revision = current.rule.revision + 1,
                activatedAtMillis = now().toEpochMilli(),
            ),
        )
        persist(owner, disabled)
        removeAll(owner)
        disabled
    }

    fun recordProgress(owner: Long, previousGlobal: Int?, currentGlobal: Int) {
        scope.launch {
            try {
                mutex.withLock {
                    session.requireVerifiedOwner(owner)
                    val current = load(owner) ?: return@withLock
                    if (!current.rule.enabled) return@withLock
                    val date = now().atZone(ZoneId.of(current.rule.timezone)).toLocalDate()
                    val progress = QuranWirdProgressTracker.advance(
                        current.rule,
                        current.progress,
                        date,
                        previousGlobal,
                        currentGlobal,
                    )
                    val updated = current.copy(progress = progress)
                    persist(owner, updated)
                    reconcileLocked(owner, updated)
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                // Reading position save must remain successful even if reminder reconciliation fails.
            }
        }
    }

    suspend fun reconcile(owner: Long): Int = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val state = load(owner)
        if (state == null || !state.rule.enabled) {
            removeAll(owner)
            return@withLock 0
        }
        reconcileLocked(owner, state)
    }

    private suspend fun reconcileLocked(owner: Long, state: QuranWirdState): Int {
        val desired = QuranWirdPlanner.plan(
            rule = state.rule,
            progress = state.progress,
            now = now(),
            title = app.getString(R.string.quran_wird_notification_title),
            body = app.getString(R.string.quran_wird_notification_body, state.rule.targetAyahs),
        )
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value.filter { it.id.startsWith(prefix) }
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("quran_wird_reminder_state_unreadable")
        }
        val wanted = desired.associateBy { it.id }
        current.filter { it.id !in wanted }.forEach { reminders.remove(it.id, owner) }
        desired.forEach { reminders.save(it, owner) }
        return desired.size
    }

    private suspend fun removeAll(owner: Long) {
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("quran_wird_reminder_state_unreadable")
        }
        current.filter { it.id.startsWith(prefix) }.forEach { reminders.remove(it.id, owner) }
    }

    private suspend fun load(owner: Long): QuranWirdState? {
        session.requireVerifiedOwner(owner)
        val row = dao.cache(owner, cacheKey) ?: return null
        val value = decode(cipher.open(row.payloadCipher))
        session.requireVerifiedOwner(owner)
        return value
    }

    private suspend fun persist(owner: Long, state: QuranWirdState) {
        session.requireVerifiedOwner(owner)
        dao.cache(CachedDocument(owner, cacheKey, cipher.seal(encode(state)), System.currentTimeMillis()))
        session.requireVerifiedOwner(owner)
    }

    private fun encode(state: QuranWirdState): String = JsonObject().apply {
        addProperty("version", 1)
        add("rule", JsonObject().apply {
            addProperty("enabled", state.rule.enabled)
            addProperty("target_ayahs", state.rule.targetAyahs)
            addProperty("time", state.rule.time)
            add("days", JsonArray().apply { state.rule.days.sorted().forEach { add(it) } })
            addProperty("timezone", state.rule.timezone)
            addProperty("revision", state.rule.revision)
            addProperty("activated_at_ms", state.rule.activatedAtMillis)
            if (state.rule.quietStart == null) add("quiet_start", null) else addProperty("quiet_start", state.rule.quietStart)
            if (state.rule.quietEnd == null) add("quiet_end", null) else addProperty("quiet_end", state.rule.quietEnd)
        })
        state.progress?.let { progress ->
            add("progress", JsonObject().apply {
                addProperty("date", progress.date)
                addProperty("baseline_global", progress.baselineGlobal)
                addProperty("highest_global", progress.highestGlobal)
            })
        }
    }.toString()

    private fun decode(raw: String): QuranWirdState {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root["version"]?.asInt == 1)
        val ruleObject = root.getAsJsonObject("rule")
        val rule = QuranWirdRule(
            enabled = ruleObject["enabled"].asBoolean,
            targetAyahs = ruleObject["target_ayahs"].asInt,
            time = ruleObject["time"].asString,
            days = ruleObject.getAsJsonArray("days").map { it.asInt }.toSet(),
            timezone = ruleObject["timezone"].asString,
            revision = ruleObject["revision"].asInt,
            activatedAtMillis = ruleObject["activated_at_ms"].asLong,
            quietStart = ruleObject["quiet_start"]?.takeUnless { it.isJsonNull }?.asString,
            quietEnd = ruleObject["quiet_end"]?.takeUnless { it.isJsonNull }?.asString,
        ).validated()
        val progress = root["progress"]?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
            QuranWirdDayProgress(
                date = it["date"].asString,
                baselineGlobal = it["baseline_global"].asInt,
                highestGlobal = it["highest_global"].asInt,
            )
        }
        return QuranWirdState(rule, progress)
    }
}

class QuranWirdMaintenanceSchedule(
    context: Context,
    private val partition: String,
) {
    private val manager = WorkManager.getInstance(context.applicationContext)

    init { require(partition.matches(Regex("[a-f0-9]{12}"))) }

    fun schedule(owner: Long) {
        require(owner > 0)
        val data = workDataOf(
            QuranWirdMaintenanceWorker.ACTOR to owner,
            QuranWirdMaintenanceWorker.ENVIRONMENT to partition,
        )
        manager.enqueueUniqueWork(
            "quran-wird-maintenance-now-$partition-$owner",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<QuranWirdMaintenanceWorker>()
                .setInputData(data)
                .addTag(tag(owner))
                .build(),
        )
        manager.enqueueUniquePeriodicWork(
            "quran-wird-maintenance-$partition-$owner",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<QuranWirdMaintenanceWorker>(24, TimeUnit.HOURS)
                .setInputData(data)
                .addTag(tag(owner))
                .build(),
        )
    }

    fun cancel(owner: Long) {
        if (owner > 0) manager.cancelAllWorkByTag(tag(owner))
    }

    private fun tag(owner: Long) = "quran-wird-maintenance-$partition-$owner"
}

class QuranWirdMaintenanceWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val owner = inputData.getLong(ACTOR, -1)
        val environment = inputData.getString(ENVIRONMENT)
        if (owner <= 0 || environment == null || !environment.matches(Regex("[a-f0-9]{12}"))) {
            return Result.failure(workDataOf("reason" to "invalid_input"))
        }
        val graph = (applicationContext as AtharApplication).graph
        if (environment != graph.partition) return Result.failure(workDataOf("reason" to "stale_environment"))
        return try {
            if (graph.session.state.value is AuthState.Restoring) graph.session.restore()
            graph.session.requireVerifiedOwner(owner)
            graph.quranWird.reconcile(owner)
            Result.success()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("reason" to "reconcile_failed_closed"))
        }
    }

    companion object {
        const val ACTOR = "actor_id"
        const val ENVIRONMENT = "environment"
    }
}
