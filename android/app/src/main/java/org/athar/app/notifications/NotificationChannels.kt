package org.athar.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import org.athar.app.R

object NotificationChannels {
    const val LESSONS = "athar.lessons.v1"
    const val ROUTINES = "athar.routines.v1"
    const val FOLLOWING = "athar.following.v1"
    const val CAMPAIGNS = "athar.campaigns.v1"

    fun channelIdForCategory(category: String): String? = when (category) {
        "lesson_reminders" -> LESSONS
        "following_updates" -> FOLLOWING
        "quran_routine",
        "adhkar_routine",
        "salawat_friday",
        "custom_routines",
        "khatmah",
        "night_prayer" -> ROUTINES
        else -> null
    }

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(
                LESSONS,
                context.getString(R.string.notification_channel_lessons),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_lessons_description)
                setShowBadge(true)
            },
            NotificationChannel(
                ROUTINES,
                context.getString(R.string.notification_channel_routines),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_routines_description)
                setShowBadge(true)
            },
            NotificationChannel(
                FOLLOWING,
                context.getString(R.string.notification_channel_following),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_following_description)
                setShowBadge(true)
            },
            NotificationChannel(
                CAMPAIGNS,
                context.getString(R.string.notification_channel_campaigns),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_campaigns_description)
                setShowBadge(true)
            },
        )
        manager.createNotificationChannels(channels)
    }
}
