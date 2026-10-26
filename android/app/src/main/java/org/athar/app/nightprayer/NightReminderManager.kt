package org.athar.app.nightprayer

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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
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

enum class NightReminderMode(val key: String) {
    LAST_THIRD("last_third"),
    FIXED_TIME("fixed_time");

    companion object {
        fun fromKey(value: String): NightReminderMode =
            values().firstOrNull { it.key == value } ?: error("invalid_night_reminder_mode")
    }
}

data class NightReminderRule(
    val enabled: Boolean,
    val mode: NightReminderMode,
    val fixedTime: String?,
    val timezone: String,
    val preferences: NightPrayerPreferences?,
    val revision: Int,
    val activatedAtMillis: Long,
) {
    fun validated(): NightReminderRule {
        ZoneId.of(timezone)
        require(revision > 0 && activatedAtMillis > 0)
        when (mode) {
            NightReminderMode.LAST_THIRD -> require(fixedTime == null)
            NightReminderMode.FIXED_TIME -> LocalTime.parse(requireNotNull(fixedTime))
        }
        preferences?.validated()
        return this
    }
}

enum class NightReminderUnavailable {
    DISABLED,
    PREFERENCES_MISSING,
    CALCULATION_UNAVAILABLE,
    NO_ELIGIBLE_NIGHT,
}

sealed interface NightReminderPlanResult {
    data class Ready(
        val spec: LocalReminderSpec,
        val nightDate: LocalDate,
        val source: NightReminderMode,
    ) : NightReminderPlanResult
    data class Unavailable(val reason: NightReminderUnavailable) : NightReminderPlanResult
}

object NightReminderPlanner {
    fun next(
        rule: NightReminderRule,
        exemptions: Set<LocalDate>,
        now: Instant,
        title: String,
        body: String,
        horizonNights: Int = 8,
        calculate: (LocalDate, NightPrayerPreferences) -> NightContext = { date, prefs ->
            PrayerCalculator.calculate(
                date = date,
                latitude = prefs.latitude,
                longitude = prefs.longitude,
                zone = ZoneId.of(prefs.timezone),
                method = prefs.calculationMethod,
                hanafi = prefs.madhab == "HANAFI",
                highLatitude = prefs.highLatitudeRule,
            )
        },
    ): NightReminderPlanResult {
        rule.validated()
        if (!rule.enabled) return NightReminderPlanResult.Unavailable(NightReminderUnavailable.DISABLED)
        val prefs = rule.preferences
            ?: return NightReminderPlanResult.Unavailable(NightReminderUnavailable.PREFERENCES_MISSING)
        require(horizonNights in 1..14)
        val zone = ZoneId.of(prefs.timezone)
        val localDate = now.atZone(zone).toLocalDate()
        var calculationFailed = false

        repeat(horizonNights) { offset ->
            val date = localDate.plusDays(offset.toLong())
            val context = try {
                calculate(date, prefs)
            } catch (_: Exception) {
                calculationFailed = true
                return@repeat
            }
            if (context.date in exemptions) return@repeat

            val due = when (rule.mode) {
                NightReminderMode.LAST_THIRD -> context.lastThird
                NightReminderMode.FIXED_TIME -> {
                    val time = LocalTime.parse(requireNotNull(rule.fixedTime))
                    var candidate = ZonedDateTime.of(date, time, zone).toInstant()
                    if (candidate < context.maghrib) {
                        candidate = ZonedDateTime.of(date.plusDays(1), time, zone).toInstant()
                    }
                    if (!context.contains(candidate)) return@repeat
                    candidate
                }
            }
            if (!due.isAfter(now)) return@repeat
            if (!context.fajr.isAfter(due)) return@repeat

            val id = "night-" + rule.mode.key.replace('_','-') + "-" + context.date.toString().replace("-","")
            return NightReminderPlanResult.Ready(
                spec = LocalReminderSpec(
                    id = id,
                    category = "night_prayer",
                    destinationToken = "night",
                    title = title,
                    body = body,
                    scheduledAtMillis = due.toEpochMilli(),
                    expiresAtMillis = context.fajr.toEpochMilli(),
                    idempotencyKey = "night:" + rule.mode.key + ":" + context.date,
                ).validated(),
                nightDate = context.date,
                source = rule.mode,
            )
        }

        return NightReminderPlanResult.Unavailable(
            if (calculationFailed) NightReminderUnavailable.CALCULATION_UNAVAILABLE
            else NightReminderUnavailable.NO_ELIGIBLE_NIGHT,
        )
    }
}

class NightReminderManager(
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
    private val cacheKey = "private:night-prayer-reminder:v1"
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val maintenance = NightReminderMaintenanceSchedule(app, partition)

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

    fun observe(owner: Long): Flow<ProtectedRead<NightReminderRule?>> = flow {
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
        mode: NightReminderMode,
        fixedTime: LocalTime?,
        preferences: NightPrayerPreferences,
        exemptions: Set<LocalDate>,
    ): NightReminderPlanResult = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val current = load(owner)
        val rule = NightReminderRule(
            enabled = true,
            mode = mode,
            fixedTime = if (mode == NightReminderMode.FIXED_TIME) requireNotNull(fixedTime).toString() else null,
            timezone = preferences.timezone,
            preferences = preferences.validated(),
            revision = (current?.revision ?: 0) + 1,
            activatedAtMillis = now().toEpochMilli(),
        ).validated()
        persist(owner, rule)
        val result = reconcileLocked(owner, rule, exemptions)
        maintenance.schedule(owner)
        result
    }

    suspend fun disable(owner: Long) = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val current = load(owner) ?: return@withLock
        persist(
            owner,
            current.copy(
                enabled = false,
                revision = current.revision + 1,
                activatedAtMillis = now().toEpochMilli(),
            ),
        )
        removeAll(owner)
    }

    suspend fun updatePreferences(owner: Long, preferences: NightPrayerPreferences?) = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val current = load(owner) ?: return@withLock
        val updated = current.copy(
            timezone = preferences?.timezone ?: current.timezone,
            preferences = preferences,
            revision = current.revision + 1,
            activatedAtMillis = now().toEpochMilli(),
        ).validated()
        persist(owner, updated)
        removeAll(owner)
        maintenance.schedule(owner)
    }

    suspend fun reconcile(owner: Long, exemptions: Set<LocalDate>): NightReminderPlanResult = mutex.withLock {
        session.requireVerifiedOwner(owner)
        val rule = load(owner)
        if (rule == null) {
            removeAll(owner)
            return@withLock NightReminderPlanResult.Unavailable(NightReminderUnavailable.DISABLED)
        }
        reconcileLocked(owner, rule, exemptions)
    }

    private suspend fun reconcileLocked(
        owner: Long,
        rule: NightReminderRule,
        exemptions: Set<LocalDate>,
    ): NightReminderPlanResult {
        val result = NightReminderPlanner.next(
            rule,
            exemptions,
            now(),
            app.getString(R.string.night_reminder_notification_title),
            app.getString(R.string.night_reminder_notification_body),
        )
        val desired = (result as? NightReminderPlanResult.Ready)?.spec
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value.filter {
                it.id.startsWith("night-last-third-") || it.id.startsWith("night-fixed-time-")
            }
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("night_reminder_state_unreadable")
        }
        current.filter { desired == null || it.id != desired.id }.forEach { reminders.remove(it.id, owner) }
        if (desired != null) reminders.save(desired, owner)
        return result
    }

    private suspend fun removeAll(owner: Long) {
        val current = when (val read = reminderStore.list(owner)) {
            is ProtectedRead.Ready -> read.value
            ProtectedRead.Loading, ProtectedRead.Unreadable -> throw IllegalStateException("night_reminder_state_unreadable")
        }
        current.filter {
            it.id.startsWith("night-last-third-") || it.id.startsWith("night-fixed-time-")
        }.forEach { reminders.remove(it.id, owner) }
    }

    private suspend fun load(owner: Long): NightReminderRule? {
        session.requireVerifiedOwner(owner)
        val row = dao.cache(owner, cacheKey) ?: return null
        val value = decode(cipher.open(row.payloadCipher))
        session.requireVerifiedOwner(owner)
        return value
    }

    private suspend fun persist(owner: Long, rule: NightReminderRule) {
        session.requireVerifiedOwner(owner)
        dao.cache(CachedDocument(owner, cacheKey, cipher.seal(encode(rule)), System.currentTimeMillis()))
        session.requireVerifiedOwner(owner)
    }

    private fun encode(rule: NightReminderRule): String = JsonObject().apply {
        addProperty("version", 1)
        addProperty("enabled", rule.enabled)
        addProperty("mode", rule.mode.key)
        if (rule.fixedTime == null) add("fixed_time", null) else addProperty("fixed_time", rule.fixedTime)
        addProperty("timezone", rule.timezone)
        addProperty("revision", rule.revision)
        addProperty("activated_at_ms", rule.activatedAtMillis)
        if (rule.preferences == null) {
            add("preferences", null)
        } else {
            val p = rule.preferences
            add("preferences", JsonObject().apply {
                addProperty("city", p.city)
                addProperty("timezone", p.timezone)
                addProperty("latitude", p.latitude)
                addProperty("longitude", p.longitude)
                addProperty("calculation_method", p.calculationMethod)
                addProperty("madhab", p.madhab)
                addProperty("high_latitude_rule", p.highLatitudeRule)
            })
        }
    }.toString()

    private fun decode(raw: String): NightReminderRule {
        val root = JsonParser.parseString(raw).asJsonObject
        require(root["version"]?.asInt == 1)
        val prefs = root["preferences"]?.takeUnless { it.isJsonNull }?.asJsonObject?.let {
            NightPrayerPreferences(
                city = it["city"].asString,
                timezone = it["timezone"].asString,
                latitude = it["latitude"].asDouble,
                longitude = it["longitude"].asDouble,
                calculationMethod = it["calculation_method"].asString,
                madhab = it["madhab"].asString,
                highLatitudeRule = it["high_latitude_rule"].asString,
            ).validated()
        }
        return NightReminderRule(
            enabled = root["enabled"].asBoolean,
            mode = NightReminderMode.fromKey(root["mode"].asString),
            fixedTime = root["fixed_time"]?.takeUnless { it.isJsonNull }?.asString,
            timezone = root["timezone"].asString,
            preferences = prefs,
            revision = root["revision"].asInt,
            activatedAtMillis = root["activated_at_ms"].asLong,
        ).validated()
    }
}

private class NightReminderMaintenanceSchedule(
    context: Context,
    private val partition: String,
) {
    private val manager = WorkManager.getInstance(context.applicationContext)
    init { require(partition.matches(Regex("[a-f0-9]{12}"))) }

    fun schedule(owner: Long) {
        require(owner > 0)
        val input = workDataOf(NightReminderMaintenanceWorker.ACTOR to owner, NightReminderMaintenanceWorker.ENVIRONMENT to partition)
        manager.enqueueUniqueWork(
            "night-reminder-maintenance-now-$partition-$owner",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NightReminderMaintenanceWorker>().setInputData(input).addTag(tag(owner)).build(),
        )
        manager.enqueueUniquePeriodicWork(
            "night-reminder-maintenance-$partition-$owner",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<NightReminderMaintenanceWorker>(24, TimeUnit.HOURS).setInputData(input).addTag(tag(owner)).build(),
        )
    }

    fun cancel(owner: Long) { if (owner > 0) manager.cancelAllWorkByTag(tag(owner)) }
    private fun tag(owner: Long) = "night-reminder-maintenance-$partition-$owner"
}

class NightReminderMaintenanceWorker(
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
            graph.nightReminders.reconcile(owner, graph.nightPrayer.localExemptions(owner))
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
