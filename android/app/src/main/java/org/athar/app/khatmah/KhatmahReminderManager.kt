package org.athar.app.khatmah

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

data class KhatmahReminderRule(
    val enabled: Boolean,
    val time: String,
    val timezone: String,
    val revision: Int,
    val activatedAtMillis: Long,
) {
    fun validated(): KhatmahReminderRule {
        LocalTime.parse(time)
        ZoneId.of(timezone)
        require(revision > 0 && activatedAtMillis > 0)
        return this
    }
}

object KhatmahReminderPlanner {
    fun next(
        rule: KhatmahReminderRule,
        plan: KhatmahPlanState?,
        now: Instant,
        title: String,
        body: (Int, Int) -> String,
        horizonDays: Int = 14,
    ): LocalReminderSpec? {
        rule.validated()
        if (!rule.enabled || plan == null || plan.conflict || plan.status != "active" || plan.completedPages >= 604) {
            return null
        }
        require(horizonDays in 1..30)
        val zone = ZoneId.of(rule.timezone)
        val localNow = now.atZone(zone)
        val time = LocalTime.parse(rule.time)

        return (0 until horizonDays).firstNotNullOfOrNull { offset ->
            val localDate = localNow.toLocalDate().plusDays(offset.toLong())
            val due = ZonedDateTime.of(localDate, time, zone).toInstant()
            val expires = localDate.plusDays(1).atStartOfDay(zone).toInstant()
            if (!expires.isAfter(now)) return@firstNotNullOfOrNull null
            if (!due.isAfter(now) && localDate == localNow.toLocalDate() &&
                rule.activatedAtMillis >= due.toEpochMilli()) return@firstNotNullOfOrNull null

            val workloadDate = due.atZone(ZoneOffset.UTC).toLocalDate()
            val metrics = calculateMetrics(plan, workloadDate)
            if (metrics.startsInDays > 0 || metrics.dailyPages <= 0 || metrics.remainingPages <= 0 || metrics.overdue) {
                return@firstNotNullOfOrNull null
            }

            val key = localDate.format(DateTimeFormatter.BASIC_ISO_DATE)
            LocalReminderSpec(
                id = "khatmah-" + key,
                category = "khatmah",
                destinationToken = "home",
                title = title,
                body = body(metrics.dailyPages, metrics.remainingPages),
                scheduledAtMillis = due.toEpochMilli(),
                expiresAtMillis = expires.toEpochMilli(),
                idempotencyKey = "khatmah:" + localDate,
            ).validated()
        }
    }
}

class KhatmahReminderManager(
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
    private val cacheKey = "private:khatmah-reminder-rule:v1"
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maintenance = KhatmahReminderMaintenanceSchedule(app, partition)

    init {
        scope.launch {
            var previousOwner: Long? = null
            session.state.collect { state ->
                val owner = (state as? AuthState.SignedIn)?.user?.id
                if (state !is AuthState.Restoring && owner != previousOwner) {
                    previousOwner?.let(maintenance::cancel)
                    previousOwner = owner
                    owner?.let(maintenance::schedule)
                }
            }
        }
    }

    fun observe(owner: Long): Flow<ProtectedRead<KhatmahReminderRule?>> = flow {
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
        time: LocalTime,
        timezone: ZoneId,
        plan: KhatmahPlanState?,
    ): KhatmahReminderRule = mutex.withLock {
        session.requireVerifiedOwner(owner)
        require(plan != null && !plan.conflict && plan.status == "active" && plan.completedPages < 604)
        val current = load(owner)
        val rule = KhatmahReminderRule(
            enabled = true,
            time = time.toString(),
            timezone = timezone.id,
            revision = (current?.revision ?: 0) + 1,
            activatedAtMillis = now().toEpochMilli(),
        ).validated()
        persist(owner, rule)
        reconcileLocked(owner, rule, plan)
        maintenance.schedule(owner)
        rule
    }

    suspend fun disable(owner: Long) = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val current = load(owner) ?: return@withLock
        val disabled = current.copy(
            enabled = false,
            revision = current.revision + 1,
            activatedAtMillis = now().toEpochMilli(),
        )
        persist(owner, disabled)
        removeAll(owner)
    }

    suspend fun recordPlan(owner: Long, plan: KhatmahPlanState?) = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val rule = load(owner) ?: return@withLock
        reconcileLocked(owner, rule, plan)
    }

    suspend fun reconcile(owner: Long, plan: KhatmahPlanState?): Int = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val rule = load(owner)
        if (rule == null || !rule.enabled) {
            removeAll(owner)
            return@withLock 0
        }
        reconcileLocked(owner, rule, plan)
    }

    private suspend fun reconcileLocked(
        owner: Long,
        rule: KhatmahReminderRule,
        plan: KhatmahPlanState?,
    ): Int {
        val desired = KhatmahReminderPlanner.next(
            rule,
            plan,
            now(),
            app.getString(R.string.khatmah_reminder_notification_title),
        ) { daily, remaining ->
            app.getString(R.string.khatmah_reminder_notification_body, daily, remaining)
        }
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value.filter { it.id.startsWith("khatmah-") }
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("khatmah_reminder_state_unreadable")
        }
        current.filter { desired == null || it.id != desired.id }.forEach { reminders.remove(it.id, owner) }
        if (desired != null) reminders.save(desired, owner)
        return if (desired == null) 0 else 1
    }

    private suspend fun removeAll(owner: Long) {
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("khatmah_reminder_state_unreadable")
        }
        current.filter { it.id.startsWith("khatmah-") }.forEach { reminders.remove(it.id, owner) }
    }

    private suspend fun load(owner: Long): KhatmahReminderRule? {
        session.requireVerifiedOwner(owner)
        val row = dao.cache(owner, cacheKey) ?: return null
        val value = decode(cipher.open(row.payloadCipher))
        session.requireVerifiedOwner(owner)
        return value
    }

    private suspend fun persist(owner: Long, rule: KhatmahReminderRule) {
        session.requireVerifiedOwner(owner)
        val payload = JsonObject().apply {
            addProperty("version", 1)
            addProperty("enabled", rule.enabled)
            addProperty("time", rule.time)
            addProperty("timezone", rule.timezone)
            addProperty("revision", rule.revision)
            addProperty("activated_at_ms", rule.activatedAtMillis)
        }.toString()
        dao.cache(CachedDocument(owner, cacheKey, cipher.seal(payload), System.currentTimeMillis()))
        session.requireVerifiedOwner(owner)
    }

    private fun decode(raw: String): KhatmahReminderRule {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root["version"]?.asInt == 1)
        return KhatmahReminderRule(
            enabled = root["enabled"].asBoolean,
            time = root["time"].asString,
            timezone = root["timezone"].asString,
            revision = root["revision"].asInt,
            activatedAtMillis = root["activated_at_ms"].asLong,
        ).validated()
    }
}

private class KhatmahReminderMaintenanceSchedule(
    context: Context,
    private val partition: String,
) {
    private val manager = WorkManager.getInstance(context.applicationContext)
    init { require(partition.matches(Regex("[a-f0-9]{12}"))) }

    fun schedule(owner: Long) {
        require(owner > 0)
        val input = workDataOf(KhatmahReminderMaintenanceWorker.ACTOR to owner, KhatmahReminderMaintenanceWorker.ENVIRONMENT to partition)
        manager.enqueueUniqueWork(
            "khatmah-reminder-maintenance-now-$partition-$owner",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<KhatmahReminderMaintenanceWorker>().setInputData(input).addTag(tag(owner)).build(),
        )
        manager.enqueueUniquePeriodicWork(
            "khatmah-reminder-maintenance-$partition-$owner",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<KhatmahReminderMaintenanceWorker>(24, TimeUnit.HOURS).setInputData(input).addTag(tag(owner)).build(),
        )
    }

    fun cancel(owner: Long) { if (owner > 0) manager.cancelAllWorkByTag(tag(owner)) }
    private fun tag(owner: Long) = "khatmah-reminder-maintenance-$partition-$owner"
}

class KhatmahReminderMaintenanceWorker(
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
            graph.khatmahReminders.reconcile(owner, graph.khatmah.saved(owner))
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
