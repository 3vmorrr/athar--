package org.athar.app.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class NotificationPermissionStatus {
    NOT_REQUIRED,
    GRANTED,
    READY_TO_REQUEST,
    DENIED_CAN_RETRY,
    SETTINGS_REQUIRED,
}

data class NotificationPermissionState(
    val status: NotificationPermissionStatus,
    val granted: Boolean,
    val canRequest: Boolean,
)

class NotificationPermissionController(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "athar-notification-permission",
        Context.MODE_PRIVATE,
    )

    fun state(activity: Activity): NotificationPermissionState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationPermissionState(
                status = NotificationPermissionStatus.NOT_REQUIRED,
                granted = true,
                canRequest = false,
            )
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return NotificationPermissionState(
                status = NotificationPermissionStatus.GRANTED,
                granted = true,
                canRequest = false,
            )
        }

        val requested = preferences.getBoolean(KEY_REQUESTED, false)
        if (!requested) {
            return NotificationPermissionState(
                status = NotificationPermissionStatus.READY_TO_REQUEST,
                granted = false,
                canRequest = true,
            )
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
            return NotificationPermissionState(
                status = NotificationPermissionStatus.DENIED_CAN_RETRY,
                granted = false,
                canRequest = true,
            )
        }
        return NotificationPermissionState(
            status = NotificationPermissionStatus.SETTINGS_REQUIRED,
            granted = false,
            canRequest = false,
        )
    }

    /**
     * Call only from an explicit user action immediately before launching the Android permission request.
     * Athar never requests notification permission on startup or merely because a consent row exists.
     */
    fun markRequestLaunched() {
        preferences.edit().putBoolean(KEY_REQUESTED, true).apply()
    }

    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            data = Uri.parse("package:" + context.packageName)
        }

    private companion object {
        const val KEY_REQUESTED = "post-notifications-requested"
    }
}
