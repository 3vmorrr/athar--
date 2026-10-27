package org.athar.app.core.sync

enum class BackgroundWorkKind {
    NETWORK_SYNC,
    LOCAL_MAINTENANCE,
    USER_VISIBLE_LOCAL,
    MEDIA_PLAYBACK,
}

data class BackgroundWorkBudget(
    val name: String,
    val kind: BackgroundWorkKind,
    val requiresNetwork: Boolean = false,
    val requiresBatteryNotLow: Boolean = false,
    val requiresStorageNotLow: Boolean = false,
    val periodicHours: Long? = null,
    val flexHours: Long? = null,
    val maxRetryAttempts: Int,
    val uniqueCoalesced: Boolean,
) {
    fun validated(): BackgroundWorkBudget {
        require(name.matches(Regex("[a-z0-9_-]{3,48}")))
        require(maxRetryAttempts in 0..12)
        if (periodicHours == null) {
            require(flexHours == null)
        } else {
            require(periodicHours in 1..168)
            require(flexHours != null && flexHours in 1..periodicHours)
        }
        if (kind == BackgroundWorkKind.USER_VISIBLE_LOCAL) {
            require(!requiresNetwork)
        }
        if (kind == BackgroundWorkKind.MEDIA_PLAYBACK) {
            require(!requiresNetwork)
            require(periodicHours == null)
        }
        return this
    }

    val maximumPeriodicRunsPerDay: Int?
        get() = periodicHours?.let { hours ->
            kotlin.math.ceil(24.0 / hours.toDouble()).toInt()
        }
}

object BackgroundWorkBudgets {
    val PRIVATE_SYNC = BackgroundWorkBudget(
        name = "private_sync",
        kind = BackgroundWorkKind.NETWORK_SYNC,
        requiresNetwork = true,
        requiresBatteryNotLow = true,
        requiresStorageNotLow = true,
        periodicHours = 6,
        flexHours = 2,
        maxRetryAttempts = 8,
        uniqueCoalesced = true,
    ).validated()

    val PUSH_DEVICE_SYNC = BackgroundWorkBudget(
        name = "push_device_sync",
        kind = BackgroundWorkKind.NETWORK_SYNC,
        requiresNetwork = true,
        requiresBatteryNotLow = true,
        requiresStorageNotLow = true,
        maxRetryAttempts = 5,
        uniqueCoalesced = true,
    ).validated()

    val INBOX_SYNC = BackgroundWorkBudget(
        name = "inbox_sync",
        kind = BackgroundWorkKind.NETWORK_SYNC,
        requiresNetwork = true,
        requiresBatteryNotLow = true,
        requiresStorageNotLow = true,
        maxRetryAttempts = 5,
        uniqueCoalesced = true,
    ).validated()

    val ROUTINE_MAINTENANCE = BackgroundWorkBudget(
        name = "routine_maintenance",
        kind = BackgroundWorkKind.LOCAL_MAINTENANCE,
        requiresBatteryNotLow = true,
        requiresStorageNotLow = true,
        periodicHours = 24,
        flexHours = 6,
        maxRetryAttempts = 3,
        uniqueCoalesced = true,
    ).validated()

    val REMINDER_DELIVERY = BackgroundWorkBudget(
        name = "reminder_delivery",
        kind = BackgroundWorkKind.USER_VISIBLE_LOCAL,
        maxRetryAttempts = 0,
        uniqueCoalesced = true,
    ).validated()

    val REMINDER_RECONCILE = BackgroundWorkBudget(
        name = "reminder_reconcile",
        kind = BackgroundWorkKind.USER_VISIBLE_LOCAL,
        maxRetryAttempts = 0,
        uniqueCoalesced = true,
    ).validated()

    val MEDIA_PLAYBACK = BackgroundWorkBudget(
        name = "media_playback",
        kind = BackgroundWorkKind.MEDIA_PLAYBACK,
        maxRetryAttempts = 0,
        uniqueCoalesced = false,
    ).validated()

    val all = listOf(
        PRIVATE_SYNC,
        PUSH_DEVICE_SYNC,
        INBOX_SYNC,
        ROUTINE_MAINTENANCE,
        REMINDER_DELIVERY,
        REMINDER_RECONCILE,
        MEDIA_PLAYBACK,
    )

    init {
        require(all.map { it.name }.toSet().size == all.size)
    }
}
