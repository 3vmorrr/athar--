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
import java.util.UUID
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

data class CustomRoutineRule(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val time: String,
    val days: Set<Int>,
    val timezone: String,
    val revision: Int,
    val activatedAtMillis: Long,
    val quietStart: String? = null,
    val quietEnd: String? = null,
) {
    fun validated(): CustomRoutineRule {
        require(ID.matches(id))
        require(label == label.trim() && label.length in 1..60)
        LocalTime.parse(time)
        require(days.isNotEmpty() && days.all { it in 1..7 })
        ZoneId.of(timezone)
        require(revision > 0 && activatedAtMillis > 0)
        require((quietStart == null) == (quietEnd == null))
        quietStart?.let { LocalTime.parse(it) }
        quietEnd?.let { LocalTime.parse(it) }
        return this
    }

    companion object {
        private val ID = Regex("[a-f0-9]{16}")
    }
}

data class CustomRoutineState(val rules: List<CustomRoutineRule>) {
    fun validated(): CustomRoutineState {
        require(rules.size <= MAX_RULES)
        require(rules.map { it.id }.toSet().size == rules.size)
        rules.forEach { it.validated() }
        return this
    }

    fun upsert(rule: CustomRoutineRule): CustomRoutineState =
        CustomRoutineState((rules.filterNot { it.id == rule.id } + rule.validated()).sortedBy { it.id }).validated()

    fun remove(id: String): CustomRoutineState =
        CustomRoutineState(rules.filterNot { it.id == id }).validated()

    companion object { const val MAX_RULES = 12 }
}

object CustomRoutineCodec {
    fun encode(state: CustomRoutineState): String {
        state.validated()
        return JsonObject().apply {
            addProperty("version", 1)
            add("rules", JsonArray().apply {
                state.rules.forEach { rule ->
                    add(JsonObject().apply {
                        addProperty("id", rule.id)
                        addProperty("label", rule.label)
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
    }

    fun decode(raw: String): CustomRoutineState {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root["version"]?.asInt == 1)
        return CustomRoutineState(
            root.getAsJsonArray("rules").map { element ->
                val value = element.asJsonObject
                CustomRoutineRule(
                    id = value["id"].asString,
                    label = value["label"].asString,
                    enabled = value["enabled"].asBoolean,
                    time = value["time"].asString,
                    days = value.getAsJsonArray("days").map { it.asInt }.toSet(),
                    timezone = value["timezone"].asString,
                    revision = value["revision"].asInt,
                    activatedAtMillis = value["activated_at_ms"].asLong,
                    quietStart = value["quiet_start"]?.takeUnless { it.isJsonNull }?.asString,
                    quietEnd = value["quiet_end"]?.takeUnless { it.isJsonNull }?.asString,
                ).validated()
            },
        ).validated()
    }
}

object CustomRoutinePlanner {
    fun next(
        rule: CustomRoutineRule,
        now: Instant,
        title: String,
        horizonDays: Int = 14,
    ): LocalReminderSpec? {
        rule.validated()
        if (!rule.enabled) return null
        require(horizonDays in 1..30)
        val zone = ZoneId.of(rule.timezone)
        val localNow = now.atZone(zone)
        val reminderTime = LocalTime.parse(rule.time)

        return (0 until horizonDays).firstNotNullOfOrNull { offset ->
            val date = localNow.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek.value !in rule.days) return@firstNotNullOfOrNull null
            val due = ZonedDateTime.of(date, reminderTime, zone).toInstant()
            val expires = date.plusDays(1).atStartOfDay(zone).toInstant()
            if (!expires.isAfter(now)) return@firstNotNullOfOrNull null
            if (!due.isAfter(now) && date == localNow.toLocalDate() &&
                rule.activatedAtMillis >= due.toEpochMilli()) return@firstNotNullOfOrNull null

            val day = date.format(DateTimeFormatter.BASIC_ISO_DATE)
            LocalReminderSpec(
                id = "custom-" + rule.id + "-" + day,
                category = "custom_routines",
                destinationToken = "home",
                title = title,
                body = rule.label,
                scheduledAtMillis = due.toEpochMilli(),
                expiresAtMillis = expires.toEpochMilli(),
                idempotencyKey = "custom:" + rule.id + ":" + date,
                quietStart = rule.quietStart,
                quietEnd = rule.quietEnd,
                timezone = if (rule.quietStart == null) null else rule.timezone,
            ).validated()
        }
    }
}

class CustomRoutineReminderManager(
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
    private val cacheKey = "private:custom-routine-reminders:v1"
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maintenance = CustomRoutineMaintenanceSchedule(app, partition)

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

    fun observe(owner: Long): Flow<ProtectedRead<CustomRoutineState>> = flow {
        session.requireVerifiedOwner(owner)
        emitAll(
            dao.observeCache(owner, cacheKey).map { row ->
                session.requireVerifiedOwner(owner)
                row?.let { CustomRoutineCodec.decode(cipher.open(it.payloadCipher)) }
                    ?: CustomRoutineState(emptyList())
            },
        )
    }.protectedReads()

    suspend fun create(
        owner: Long,
        label: String,
        time: LocalTime,
        days: Set<Int>,
        timezone: ZoneId,
    ): CustomRoutineRule = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val state = load(owner)
        require(state.rules.size < CustomRoutineState.MAX_RULES)
        val rule = CustomRoutineRule(
            id = UUID.randomUUID().toString().replace("-", "").take(16),
            label = label.trim(),
            enabled = true,
            time = time.toString(),
            days = days.toSortedSet(),
            timezone = timezone.id,
            revision = 1,
            activatedAtMillis = now().toEpochMilli(),
        ).validated()
        val next = state.upsert(rule)
        persist(owner, next)
        reconcileLocked(owner, next)
        maintenance.schedule(owner)
        rule
    }

    suspend fun edit(
        owner: Long,
        id: String,
        label: String,
        time: LocalTime,
        days: Set<Int>,
        timezone: ZoneId,
    ): CustomRoutineRule = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val state = load(owner)
        val current = state.rules.firstOrNull { it.id == id } ?: throw IllegalArgumentException("custom_rule_missing")
        val rule = current.copy(
            label = label.trim(),
            time = time.toString(),
            days = days.toSortedSet(),
            timezone = timezone.id,
            revision = current.revision + 1,
            activatedAtMillis = now().toEpochMilli(),
        ).validated()
        val next = state.upsert(rule)
        persist(owner, next)
        reconcileLocked(owner, next)
        rule
    }

    suspend fun setEnabled(owner: Long, id: String, enabled: Boolean): CustomRoutineRule = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val state = load(owner)
        val current = state.rules.firstOrNull { it.id == id } ?: throw IllegalArgumentException("custom_rule_missing")
        if (current.enabled == enabled) return@withLock current
        val updated = current.copy(
            enabled = enabled,
            revision = current.revision + 1,
            activatedAtMillis = now().toEpochMilli(),
        ).validated()
        val next = state.upsert(updated)
        persist(owner, next)
        reconcileLocked(owner, next)
        updated
    }

    suspend fun delete(owner: Long, id: String) = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val state = load(owner)
        if (state.rules.none { it.id == id }) return@withLock
        val next = state.remove(id)
        persist(owner, next)
        removeRule(owner, id)
    }

    suspend fun reconcile(owner: Long): Int = mutex.withLock {
        session.requireVerifiedOwner(owner)
        reconcileLocked(owner, load(owner))
    }

    private suspend fun reconcileLocked(owner: Long, state: CustomRoutineState): Int {
        val desired = state.rules.filter { it.enabled }.mapNotNull { rule ->
            CustomRoutinePlanner.next(rule, now(), app.getString(R.string.custom_routine_notification_title))
        }
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value.filter { it.id.startsWith("custom-") }
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("custom_routine_state_unreadable")
        }
        val wanted = desired.associateBy { it.id }
        current.filter { it.id !in wanted }.forEach { reminders.remove(it.id, owner) }
        desired.forEach { reminders.save(it, owner) }
        return desired.size
    }

    private suspend fun removeRule(owner: Long, id: String) {
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("custom_routine_state_unreadable")
        }
        current.filter { it.id.startsWith("custom-" + id + "-") }.forEach { reminders.remove(it.id, owner) }
    }

    private suspend fun load(owner: Long): CustomRoutineState {
        session.requireVerifiedOwner(owner)
        val row = dao.cache(owner, cacheKey) ?: return CustomRoutineState(emptyList())
        val state = CustomRoutineCodec.decode(cipher.open(row.payloadCipher))
        session.requireVerifiedOwner(owner)
        return state
    }

    private suspend fun persist(owner: Long, state: CustomRoutineState) {
        session.requireVerifiedOwner(owner)
        if (state.rules.isEmpty()) dao.removeCache(owner, cacheKey)
        else dao.cache(CachedDocument(owner, cacheKey, cipher.seal(CustomRoutineCodec.encode(state)), System.currentTimeMillis()))
        session.requireVerifiedOwner(owner)
    }
}

private class CustomRoutineMaintenanceSchedule(
    context: Context,
    private val partition: String,
) {
    private val manager = WorkManager.getInstance(context.applicationContext)
    init { require(partition.matches(Regex("[a-f0-9]{12}"))) }

    fun schedule(owner: Long) {
        require(owner > 0)
        val input = workDataOf(CustomRoutineMaintenanceWorker.ACTOR to owner, CustomRoutineMaintenanceWorker.ENVIRONMENT to partition)
        manager.enqueueUniqueWork(
            "custom-routine-maintenance-now-$partition-$owner",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CustomRoutineMaintenanceWorker>().setInputData(input).addTag(tag(owner)).build(),
        )
        manager.enqueueUniquePeriodicWork(
            "custom-routine-maintenance-$partition-$owner",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CustomRoutineMaintenanceWorker>(
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
    private fun tag(owner: Long) = "custom-routine-maintenance-$partition-$owner"
}

class CustomRoutineMaintenanceWorker(
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
            graph.customRoutines.reconcile(owner)
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
