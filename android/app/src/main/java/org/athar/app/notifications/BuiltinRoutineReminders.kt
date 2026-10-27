package org.athar.app.notifications

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
import java.time.Instant
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
import org.athar.app.core.sync.BackgroundWorkBudgets
import org.athar.app.core.sync.BackgroundWorkConstraints

enum class BuiltinRoutineKind(val key: String) {
    SALAWAT("salawat"),
    FRIDAY("friday");

    companion object {
        fun fromKey(value: String): BuiltinRoutineKind =
            values().firstOrNull { it.key == value } ?: error("unknown routine")
    }
}

data class BuiltinRoutineRule(
    val kind: BuiltinRoutineKind,
    val enabled: Boolean,
    val time: String,
    val days: Set<Int>,
    val timezone: String,
    val revision: Int,
    val activatedAtMillis: Long,
    val quietStart: String? = null,
    val quietEnd: String? = null,
) {
    fun validated(): BuiltinRoutineRule {
        LocalTime.parse(time)
        require(days.isNotEmpty() && days.all { it in 1..7 })
        if (kind == BuiltinRoutineKind.FRIDAY) require(days == setOf(5))
        ZoneId.of(timezone)
        require(revision > 0 && activatedAtMillis > 0)
        require((quietStart == null) == (quietEnd == null))
        quietStart?.let { LocalTime.parse(it) }
        quietEnd?.let { LocalTime.parse(it) }
        return this
    }
}

data class BuiltinRoutineState(val rules: List<BuiltinRoutineRule>)

object BuiltinRoutinePlanner {
    fun next(
        rule: BuiltinRoutineRule,
        now: Instant,
        title: String,
        body: String,
        horizonDays: Int = 14,
    ): LocalReminderSpec? {
        rule.validated()
        if (!rule.enabled) return null
        require(horizonDays in 1..30)
        val zone = ZoneId.of(rule.timezone)
        val localNow = now.atZone(zone)
        val time = LocalTime.parse(rule.time)
        return (0 until horizonDays).firstNotNullOfOrNull { offset ->
            val date = localNow.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek.value !in rule.days) return@firstNotNullOfOrNull null
            val due = ZonedDateTime.of(date, time, zone).toInstant()
            val expires = date.plusDays(1).atStartOfDay(zone).toInstant()
            if (!expires.isAfter(now)) return@firstNotNullOfOrNull null
            if (!due.isAfter(now) && date == localNow.toLocalDate() &&
                rule.activatedAtMillis >= due.toEpochMilli()) return@firstNotNullOfOrNull null
            val day = date.format(DateTimeFormatter.BASIC_ISO_DATE)
            LocalReminderSpec(
                id = "routine-" + rule.kind.key + "-" + day,
                category = "salawat_friday",
                destinationToken = "home",
                title = title,
                body = body,
                scheduledAtMillis = due.toEpochMilli(),
                expiresAtMillis = expires.toEpochMilli(),
                idempotencyKey = "routine:" + rule.kind.key + ":" + date,
                quietStart = rule.quietStart,
                quietEnd = rule.quietEnd,
                timezone = if (rule.quietStart == null) null else rule.timezone,
            ).validated()
        }
    }
}

class BuiltinRoutineReminderManager(
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
    private val cacheKey = "private:builtin-routine-reminders:v1"
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maintenance = BuiltinRoutineMaintenanceSchedule(app, partition)

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
                        try { reconcile(it) }
                        catch (failure: CancellationException) { throw failure }
                        catch (_: Exception) { }
                    }
                }
            }
        }
    }

    fun observe(owner: Long): Flow<ProtectedRead<BuiltinRoutineState>> = flow {
        session.requireVerifiedOwner(owner)
        emitAll(
            dao.observeCache(owner, cacheKey).map { row ->
                session.requireVerifiedOwner(owner)
                row?.let { decode(cipher.open(it.payloadCipher)) } ?: BuiltinRoutineState(emptyList())
            },
        )
    }.protectedReads()

    suspend fun save(
        owner: Long,
        kind: BuiltinRoutineKind,
        time: LocalTime,
        days: Set<Int>,
        timezone: ZoneId,
        quietStart: LocalTime? = null,
        quietEnd: LocalTime? = null,
    ): BuiltinRoutineRule = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val current = load(owner)
        val previous = current.rules.firstOrNull { it.kind == kind }
        val canonicalDays = if (kind == BuiltinRoutineKind.FRIDAY) setOf(5) else days
        val rule = BuiltinRoutineRule(
            kind = kind,
            enabled = true,
            time = time.toString(),
            days = canonicalDays.toSortedSet(),
            timezone = timezone.id,
            revision = (previous?.revision ?: 0) + 1,
            activatedAtMillis = now().toEpochMilli(),
            quietStart = quietStart?.toString(),
            quietEnd = quietEnd?.toString(),
        ).validated()
        val next = BuiltinRoutineState(
            (current.rules.filterNot { it.kind == kind } + rule).sortedBy { it.kind.key },
        )
        persist(owner, next)
        reconcileLocked(owner, next)
        maintenance.schedule(owner)
        rule
    }

    suspend fun disable(owner: Long, kind: BuiltinRoutineKind) = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val current = load(owner)
        val old = current.rules.firstOrNull { it.kind == kind } ?: return@withLock
        val disabled = old.copy(
            enabled = false,
            revision = old.revision + 1,
            activatedAtMillis = now().toEpochMilli(),
        )
        val next = BuiltinRoutineState(
            (current.rules.filterNot { it.kind == kind } + disabled).sortedBy { it.kind.key },
        )
        persist(owner, next)
        removeKind(owner, kind)
    }

    suspend fun reconcile(owner: Long): Int = mutex.withLock {
        session.requireVerifiedOwner(owner)
        reconcileLocked(owner, load(owner))
    }

    private suspend fun reconcileLocked(owner: Long, state: BuiltinRoutineState): Int {
        val desired = state.rules.filter { it.enabled }.mapNotNull { rule ->
            BuiltinRoutinePlanner.next(
                rule,
                now(),
                app.getString(if (rule.kind == BuiltinRoutineKind.SALAWAT) R.string.salawat_notification_title else R.string.friday_notification_title),
                app.getString(if (rule.kind == BuiltinRoutineKind.SALAWAT) R.string.salawat_notification_body else R.string.friday_notification_body),
            )
        }
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value.filter {
                it.id.startsWith("routine-salawat-") || it.id.startsWith("routine-friday-")
            }
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("builtin_routine_state_unreadable")
        }
        val wanted = desired.associateBy { it.id }
        current.filter { it.id !in wanted }.forEach { reminders.remove(it.id, owner) }
        desired.forEach { reminders.save(it, owner) }
        return desired.size
    }

    private suspend fun removeKind(owner: Long, kind: BuiltinRoutineKind) {
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("builtin_routine_state_unreadable")
        }
        current.filter { it.id.startsWith("routine-" + kind.key + "-") }
            .forEach { reminders.remove(it.id, owner) }
    }

    private suspend fun load(owner: Long): BuiltinRoutineState {
        session.requireVerifiedOwner(owner)
        val row = dao.cache(owner, cacheKey) ?: return BuiltinRoutineState(emptyList())
        val value = decode(cipher.open(row.payloadCipher))
        session.requireVerifiedOwner(owner)
        return value
    }

    private suspend fun persist(owner: Long, state: BuiltinRoutineState) {
        session.requireVerifiedOwner(owner)
        dao.cache(CachedDocument(owner, cacheKey, cipher.seal(encode(state)), System.currentTimeMillis()))
        session.requireVerifiedOwner(owner)
    }

    private fun encode(state: BuiltinRoutineState): String = JsonObject().apply {
        addProperty("version", 1)
        add("rules", JsonArray().apply {
            state.rules.forEach { rule ->
                add(JsonObject().apply {
                    addProperty("kind", rule.kind.key)
                    addProperty("enabled", rule.enabled)
                    addProperty("time", rule.time)
                    add("days", JsonArray().apply { rule.days.sorted().forEach { add(it) } })
                    addProperty("timezone", rule.timezone)
                    addProperty("revision", rule.revision)
                    addProperty("activated_at_ms", rule.activatedAtMillis)
                    if (rule.quietStart == null) add("quiet_start", null) else addProperty("quiet_start", rule.quietStart)
                    if (rule.quietEnd == null) add("quiet_end", null) else addProperty("quiet_end", rule.quietEnd)
                })
            }
        })
    }.toString()

    private fun decode(raw: String): BuiltinRoutineState {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root["version"]?.asInt == 1)
        val rules = root.getAsJsonArray("rules").map { element ->
            val value = element.asJsonObject
            BuiltinRoutineRule(
                kind = BuiltinRoutineKind.fromKey(value["kind"].asString),
                enabled = value["enabled"].asBoolean,
                time = value["time"].asString,
                days = value.getAsJsonArray("days").map { it.asInt }.toSet(),
                timezone = value["timezone"].asString,
                revision = value["revision"].asInt,
                activatedAtMillis = value["activated_at_ms"].asLong,
                quietStart = value["quiet_start"]?.takeUnless { it.isJsonNull }?.asString,
                quietEnd = value["quiet_end"]?.takeUnless { it.isJsonNull }?.asString,
            ).validated()
        }
        require(rules.size <= BuiltinRoutineKind.values().size)
        require(rules.map { it.kind }.toSet().size == rules.size)
        return BuiltinRoutineState(rules.sortedBy { it.kind.key })
    }
}

private class BuiltinRoutineMaintenanceSchedule(
    context: Context,
    private val partition: String,
) {
    private val manager = WorkManager.getInstance(context.applicationContext)
    init { require(partition.matches(Regex("[a-f0-9]{12}"))) }

    fun schedule(owner: Long) {
        require(owner > 0)
        val input = workDataOf(BuiltinRoutineMaintenanceWorker.ACTOR to owner, BuiltinRoutineMaintenanceWorker.ENVIRONMENT to partition)
        manager.enqueueUniqueWork(
            "builtin-routine-maintenance-now-$partition-$owner",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BuiltinRoutineMaintenanceWorker>().setInputData(input).addTag(tag(owner)).build(),
        )
        manager.enqueueUniquePeriodicWork(
            "builtin-routine-maintenance-$partition-$owner",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<BuiltinRoutineMaintenanceWorker>(
                BackgroundWorkBudgets.ROUTINE_MAINTENANCE.periodicHours!!,
                TimeUnit.HOURS,
                BackgroundWorkBudgets.ROUTINE_MAINTENANCE.flexHours!!,
                TimeUnit.HOURS,
            )
                .setInputData(input)
                .setConstraints(
                    BackgroundWorkConstraints.forBudget(
                        BackgroundWorkBudgets.ROUTINE_MAINTENANCE,
                    ),
                )
                .addTag(tag(owner))
                .build(),
        )
    }

    fun cancel(owner: Long) { if (owner > 0) manager.cancelAllWorkByTag(tag(owner)) }
    private fun tag(owner: Long) = "builtin-routine-maintenance-$partition-$owner"
}

class BuiltinRoutineMaintenanceWorker(
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
            graph.builtinRoutines.reconcile(owner)
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
