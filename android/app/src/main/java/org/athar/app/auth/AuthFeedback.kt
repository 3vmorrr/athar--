package org.athar.app.auth

import androidx.annotation.StringRes
import org.athar.app.R

/** Translate transport codes at the presentation boundary; never expose unknown internal details. */
@StringRes internal fun authFeedbackResource(code: String): Int = when (code) {
    "password_mismatch" -> R.string.password_mismatch
    "invalid_fields", "invalid_input" -> R.string.invalid_input
    "invalid_credentials" -> R.string.auth_invalid_credentials
    "invalid_or_expired_code" -> R.string.auth_invalid_code
    "rate_limited" -> R.string.auth_rate_limited
    "account_conflict" -> R.string.auth_account_conflict
    "email_verification_required" -> R.string.auth_verification_required
    "unauthenticated", "refresh_reused" -> R.string.auth_session_expired
    "forbidden" -> R.string.auth_forbidden
    "mail_accepted" -> R.string.mail_accepted
    "mail_unavailable" -> R.string.mail_unavailable
    "reset_sent" -> R.string.reset_sent
    "password_updated" -> R.string.password_updated
    "saved" -> R.string.saved
    "sessions_revoked" -> R.string.sessions_revoked
    else -> R.string.network_error
}
