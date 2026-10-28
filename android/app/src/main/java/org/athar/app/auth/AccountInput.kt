package org.athar.app.auth

import java.util.Locale

object AccountInput {
    private val reserved = setOf(
        "admin",
        "administrator",
        "root",
        "support",
        "athar",
        "api",
        "www",
        "owner",
    )

    fun normalizeAsciiDigits(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    in '٠'..'٩' -> ('0'.code + (char - '٠')).toChar()
                    in '۰'..'۹' -> ('0'.code + (char - '۰')).toChar()
                    else -> char
                },
            )
        }
    }

    fun normalizeUsernameInput(value: String): String =
        normalizeAsciiDigits(value).lowercase(Locale.ROOT).take(24)

    fun validDisplayName(value: String): Boolean =
        value.trim().let { it.codePointCount(0, it.length) in 2..80 }

    fun validUsername(value: String): Boolean {
        val normalized = value.trim().lowercase(Locale.ROOT)
        return normalized.matches(Regex("[a-z0-9_]{3,24}")) &&
            normalized !in reserved
    }

    fun validEmail(value: String): Boolean {
        val email = value.trim()
        if (email.length !in 3..190 || email.any(Char::isWhitespace)) return false
        val at = email.indexOf('@')
        if (at <= 0 || at != email.lastIndexOf('@') || at >= email.lastIndex) return false
        val domain = email.substring(at + 1)
        return domain.contains('.') &&
            !domain.startsWith('.') &&
            !domain.endsWith('.')
    }

    fun validPassword(value: String): Boolean =
        value.codePointCount(0, value.length) in 10..200 && '\u0000' !in value

    fun validProfile(displayName: String, username: String): Boolean =
        validDisplayName(displayName) && validUsername(username)
}
