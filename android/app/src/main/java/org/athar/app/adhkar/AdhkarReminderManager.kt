package org.athar.app.adhkar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
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
import org.athar.app.core.data.CounterState
import org.athar.app.core.data.ProtectedRead
import org.athar.app.core.data.protectedReads
import org.athar.app.core.security.PrivateCipher
import org.athar.app.core.ui.text
import org.athar.app.notifications.LocalReminderCoordinator
import org.athar.app.notifications.LocalReminderSpec
import org.athar.app.notifications.LocalReminderStore

data class AdhkarReminderRule(
    val category: String,
    val enabled: Boolean,
    val time: String,
    val days: Set<Int>,
    val timezone: String,
    val requirements: Map<Int, Int>,
    val revision: Int,
    val activatedAtMillis: Long,
) {
    fun validated(): AdhkarReminderRule {
        require(category.isNotBlank() && category.length <= 80)
        LocalTime.parse(time)
        require(days.isNotEmpty() && days.all { it in 1..7 })
        ZoneId.of(timezone)
        require(requirements.isNotEmpty() && requirements.size <= 500)
        require(requirements.all { (id, target) -> id > 0 && target in 1..65535 })
        require(revision > 0 && activatedAtMillis > 0)
        return this
    }
}

data class AdhkarReminderState(val rules: List<AdhkarReminderRule>)

data class AdhkarCategoryCompletion(
    val fixedRequirements: Int,
    val completedRequirements: Int,
) {
    val complete: Boolean
        get() = fixedRequirements > 0 && completedRequirements == fixedRequirements
}

object AdhkarCompletionPolicy {
    fun evaluate(
        requirements: Map<Int, Int>,
        states: Map<Int, CounterState>,
        utcDate: String,
    ): AdhkarCategoryCompletion {
        require(requirements.isNotEmpty())
        LocalDate.parse(utcDate)
        val completed = requirements.count { (item, target) ->
            val state = states[item]
            state != null &&
                !state.conflict &&
                state.target == target &&
                state.day == utcDate &&
                state.count >= target
        }
        return AdhkarCategoryCompletion(requirements.size, completed)
    }
}

object AdhkarReminderPlanner {
    fun plan(
        rule: AdhkarReminderRule,
        completion: AdhkarCategoryCompletion,
        now: Instant,
        title: String,
        body: String,
        horizonDays: Int = 14,
    ): List<LocalReminderSpec> {
        rule.validated()
        if (!rule.enabled) return emptyList()
        require(horizonDays in 1..30)
        val zone = ZoneId.of(rule.timezone)
        val localNow = now.atZone(zone)
        val reminderTime = LocalTime.parse(rule.time)
        val digest = digest(rule.category)

        val next = (0 until horizonDays).firstNotNullOfOrNull { offset ->
            val date = localNow.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek.value !in rule.days) return@firstNotNullOfOrNull null
            if (offset == 0 && completion.complete) return@firstNotNullOfOrNull null

            val due = ZonedDateTime.of(date, reminderTime, zone).toInstant()
            val expires = date.plusDays(1).atStartOfDay(zone).toInstant()
            if (!expires.isAfter(now)) return@firstNotNullOfOrNull null
            if (!due.isAfter(now) && date == localNow.toLocalDate() && rule.activatedAtMillis >= due.toEpochMilli()) {
                return@firstNotNullOfOrNull null
            }

            val dateKey = date.format(DateTimeFormatter.BASIC_ISO_DATE)
            LocalReminderSpec(
                id = "adhkar-" + digest + "-" + dateKey,
                category = "adhkar_routine",
                destinationToken = "adhkar",
                title = title,
                body = body,
                scheduledAtMillis = due.toEpochMilli(),
                expiresAtMillis = expires.toEpochMilli(),
                idempotencyKey = "adhkar:" + digest + ":" + date,
            ).validated()
        }
        return listOfNotNull(next)
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
}

enum class AdhkarRuleSaveResult { SAVED, NO_FIXED_REQUIREMENTS }

class AdhkarReminderManager(
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
    private val gson = Gson()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheKey = "private:adhkar-reminder-rules:v1"
    private val maintenance = AdhkarReminderMaintenanceSchedule(app, partition)

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

    fun observe(owner: Long): Flow<ProtectedRead<AdhkarReminderState>> = flow {
        session.requireVerifiedOwner(owner)
        emitAll(
            dao.observeCache(owner, cacheKey).map { row ->
                session.requireVerifiedOwner(owner)
                row?.let { decode(cipher.open(it.payloadCipher)) } ?: AdhkarReminderState(emptyList())
            },
        )
    }.protectedReads()

    suspend fun saveRule(
        owner: Long,
        category: String,
        time: LocalTime,
        days: Set<Int>,
        timezone: ZoneId,
    ): AdhkarRuleSaveResult = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val requirements = loadRequirements(owner, category)
        if (requirements.isEmpty()) return@withLock AdhkarRuleSaveResult.NO_FIXED_REQUIREMENTS
        val state = load(owner)
        val previous = state.rules.firstOrNull { it.category == category }
        val rule = AdhkarReminderRule(
            category = category,
            enabled = true,
            time = time.toString(),
            days = days.toSortedSet(),
            timezone = timezone.id,
            requirements = requirements,
            revision = (previous?.revision ?: 0) + 1,
            activatedAtMillis = now().toEpochMilli(),
        ).validated()
        val next = AdhkarReminderState(
            (state.rules.filterNot { it.category == category } + rule)
                .sortedBy { it.category },
        )
        require(next.rules.size <= 24)
        persist(owner, next)
        reconcileLocked(owner, next)
        maintenance.schedule(owner)
        AdhkarRuleSaveResult.SAVED
    }

    suspend fun disable(owner: Long, category: String) = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val state = load(owner)
        val current = state.rules.firstOrNull { it.category == category } ?: return@withLock
        val disabled = current.copy(
            enabled = false,
            revision = current.revision + 1,
            activatedAtMillis = now().toEpochMilli(),
        )
        val next = AdhkarReminderState(
            (state.rules.filterNot { it.category == category } + disabled).sortedBy { it.category },
        )
        persist(owner, next)
        removeCategory(owner, category)
    }

    suspend fun refreshRequirements(owner: Long, category: String): AdhkarRuleSaveResult? = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val state = load(owner)
        val current = state.rules.firstOrNull { it.category == category && it.enabled } ?: return@withLock null
        val requirements = loadRequirements(owner, category)
        if (requirements.isEmpty()) {
            val next = AdhkarReminderState(state.rules.filterNot { it.category == category })
            persist(owner, next)
            removeCategory(owner, category)
            return@withLock AdhkarRuleSaveResult.NO_FIXED_REQUIREMENTS
        }
        if (requirements != current.requirements) {
            val updated = current.copy(
                requirements = requirements,
                revision = current.revision + 1,
            ).validated()
            val next = AdhkarReminderState(
                (state.rules.filterNot { it.category == category } + updated).sortedBy { it.category },
            )
            persist(owner, next)
            reconcileLocked(owner, next)
        }
        AdhkarRuleSaveResult.SAVED
    }

    suspend fun recordCounterState(owner: Long, state: CounterState) = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val rules = load(owner)
        if (rules.rules.none { it.enabled && state.item in it.requirements }) return@withLock
        try {
            reconcileLocked(owner, rules)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // Counter persistence stays authoritative even if reminder maintenance needs a later retry.
        }
    }

    suspend fun reconcile(owner: Long): Int = mutex.withLock {
        session.requireVerifiedOwner(owner)
        reconcileLocked(owner, load(owner))
    }

    private suspend fun reconcileLocked(owner: Long, state: AdhkarReminderState): Int {
        val desired = mutableListOf<LocalReminderSpec>()
        state.rules.filter { it.enabled }.forEach { rule ->
            val completion = completion(owner, rule)
            desired += AdhkarReminderPlanner.plan(
                rule = rule,
                completion = completion,
                now = now(),
                title = app.getString(R.string.adhkar_reminder_notification_title),
                body = app.getString(R.string.adhkar_reminder_notification_body, rule.category),
            )
        }

        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value.filter { it.id.startsWith("adhkar-") }
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("adhkar_reminder_state_unreadable")
        }
        val wanted = desired.associateBy { it.id }
        current.filter { it.id !in wanted }.forEach { reminders.remove(it.id, owner) }
        desired.forEach { reminders.save(it, owner) }
        return desired.size
    }

    private suspend fun completion(owner: Long, rule: AdhkarReminderRule): AdhkarCategoryCompletion {
        val utcDate = now().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
        val states = mutableMapOf<Int, CounterState>()
        rule.requirements.forEach { (item, target) ->
            val row = dao.cache(owner, "adhkar:" + item) ?: return@forEach
            val value = runCatching {
                requireNotNull(gson.fromJson(cipher.open(row.payloadCipher), CounterState::class.java))
            }.getOrNull() ?: return@forEach
            if (
                value.item == item &&
                value.target == target &&
                value.count in 0..target &&
                value.revision >= 0 &&
                value.localVersion >= 0
            ) states[item] = value
        }
        return AdhkarCompletionPolicy.evaluate(rule.requirements, states, utcDate)
    }

    private suspend fun loadRequirements(owner: Long, category: String): Map<Int, Int> {
        require(category.isNotBlank() && category.length <= 80)
        val requirements = linkedMapOf<Int, Int>()
        var page = 1
        while (page <= 10) {
            session.requireVerifiedOwner(owner)
            val data = session.request(
                "GET",
                "adhkar",
                query = mapOf(
                    "category" to category,
                    "page" to page.toString(),
                    "per_page" to "50",
                ),
                expectedUser = owner,
            ).asJsonObject
            val rows = data["items"]?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
            rows.forEach { item ->
                val id = item.text("id").toIntOrNull() ?: return@forEach
                val target = item.text("repeat_count").toIntOrNull()
                if (id > 0 && target != null && target in 1..65535) requirements[id] = target
            }
            val more = data["pagination"]?.asJsonObject?.get("has_more")?.asBoolean == true
            if (!more) break
            page++
        }
        require(requirements.size <= 500)
        return requirements
    }

    private suspend fun removeCategory(owner: Long, category: String) {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(category.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("adhkar_reminder_state_unreadable")
        }
        current.filter { it.id.startsWith("adhkar-" + digest + "-") }.forEach { reminders.remove(it.id, owner) }
    }

    private suspend fun load(owner: Long): AdhkarReminderState {
        session.requireVerifiedOwner(owner)
        val row = dao.cache(owner, cacheKey) ?: return AdhkarReminderState(emptyList())
        val value = decode(cipher.open(row.payloadCipher))
        session.requireVerifiedOwner(owner)
        return value
    }

    private suspend fun persist(owner: Long, state: AdhkarReminderState) {
        session.requireVerifiedOwner(owner)
        dao.cache(CachedDocument(owner, cacheKey, cipher.seal(encode(state)), System.currentTimeMillis()))
        session.requireVerifiedOwner(owner)
    }

    private fun encode(state: AdhkarReminderState): String = JsonObject().apply {
        addProperty("version", 1)
        add("rules", JsonArray().apply {
            state.rules.forEach { rule ->
                add(JsonObject().apply {
                    addProperty("category", rule.category)
                    addProperty("enabled", rule.enabled)
                    addProperty("time", rule.time)
                    add("days", JsonArray().apply { rule.days.sorted().forEach { add(it) } })
                    addProperty("timezone", rule.timezone)
                    add("requirements", JsonObject().apply {
                        rule.requirements.toSortedMap().forEach { (id, target) -> addProperty(id.toString(), target) }
                    })
                    addProperty("revision", rule.revision)
                    addProperty("activated_at_ms", rule.activatedAtMillis)
                })
            }
        })
    }.toString()

    private fun decode(raw: String): AdhkarReminderState {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root["version"]?.asInt == 1)
        val rules = root.getAsJsonArray("rules").map { element ->
            val value = element.asJsonObject
            val requirements = value.getAsJsonObject("requirements").entrySet().associate { entry ->
                entry.key.toInt() to entry.value.asInt
            }
            AdhkarReminderRule(
                category = value["category"].asString,
                enabled = value["enabled"].asBoolean,
                time = value["time"].asString,
                days = value.getAsJsonArray("days").map { it.asInt }.toSet(),
                timezone = value["timezone"].asString,
                requirements = requirements,
                revision = value["revision"].asInt,
                activatedAtMillis = value["activated_at_ms"].asLong,
            ).validated()
        }
        require(rules.size <= 24)
        require(rules.map { it.category }.toSet().size == rules.size)
        return AdhkarReminderState(rules.sortedBy { it.category })
    }
}

class AdhkarReminderMaintenanceSchedule(
    context: Context,
    private val partition: String,
) {
    private val manager = WorkManager.getInstance(context.applicationContext)
    init { require(partition.matches(Regex("[a-f0-9]{12}"))) }

    fun schedule(owner: Long) {
        require(owner > 0)
        val data = workDataOf(
            AdhkarReminderMaintenanceWorker.ACTOR to owner,
            AdhkarReminderMaintenanceWorker.ENVIRONMENT to partition,
        )
        manager.enqueueUniqueWork(
            "adhkar-reminder-maintenance-now-$partition-$owner",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AdhkarReminderMaintenanceWorker>()
                .setInputData(data)
                .addTag(tag(owner))
                .build(),
        )
        manager.enqueueUniquePeriodicWork(
            "adhkar-reminder-maintenance-$partition-$owner",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AdhkarReminderMaintenanceWorker>(24, TimeUnit.HOURS)
                .setInputData(data)
                .addTag(tag(owner))
                .build(),
        )
    }

    fun cancel(owner: Long) {
        if (owner > 0) manager.cancelAllWorkByTag(tag(owner))
    }

    private fun tag(owner: Long) = "adhkar-reminder-maintenance-$partition-$owner"
}

class AdhkarReminderMaintenanceWorker(
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
            graph.adhkarReminders.reconcile(owner)
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
