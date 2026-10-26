package org.athar.app.notifications

import android.content.Context
import android.content.Intent
import org.athar.app.MainActivity

sealed interface NotificationDestination {
    data class Lesson(val id: Int) : NotificationDestination
    data class Series(val id: Int) : NotificationDestination
    data object Home : NotificationDestination
    data object Quran : NotificationDestination
    data object Adhkar : NotificationDestination
    data object NightPrayer : NotificationDestination
    data object Inbox : NotificationDestination
}

data class NotificationLaunch(
    val ownerId: Long,
    val destination: NotificationDestination,
)

object NotificationDeepLinks {
    const val EXTRA_OWNER_ID = "org.athar.app.notification.OWNER_ID"
    const val EXTRA_DESTINATION = "org.athar.app.notification.DESTINATION"

    fun encode(destination: NotificationDestination): String = when (destination) {
        is NotificationDestination.Lesson -> "lesson:" + destination.id
        is NotificationDestination.Series -> "series:" + destination.id
        NotificationDestination.Home -> "home"
        NotificationDestination.Quran -> "quran"
        NotificationDestination.Adhkar -> "adhkar"
        NotificationDestination.NightPrayer -> "night"
        NotificationDestination.Inbox -> "inbox"
    }

    fun decode(raw: String?): NotificationDestination? {
        val value = raw?.trim().orEmpty()
        return when {
            value == "home" -> NotificationDestination.Home
            value == "quran" -> NotificationDestination.Quran
            value == "adhkar" -> NotificationDestination.Adhkar
            value == "night" -> NotificationDestination.NightPrayer
            value == "inbox" -> NotificationDestination.Inbox
            value.startsWith("lesson:") -> value.substringAfter(':').toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let(NotificationDestination::Lesson)
            value.startsWith("series:") -> value.substringAfter(':').toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let(NotificationDestination::Series)
            else -> null
        }
    }

    fun fromIntent(intent: Intent?): NotificationLaunch? {
        val source = intent ?: return null
        val owner = source.getLongExtra(EXTRA_OWNER_ID, -1L).takeIf { it > 0 } ?: return null
        val destination = decode(source.getStringExtra(EXTRA_DESTINATION)) ?: return null
        return NotificationLaunch(owner, destination)
    }

    fun activityIntent(
        context: Context,
        ownerId: Long,
        destination: NotificationDestination,
    ): Intent {
        require(ownerId > 0)
        return Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OWNER_ID, ownerId)
            putExtra(EXTRA_DESTINATION, encode(destination))
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}
