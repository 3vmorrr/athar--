package org.athar.app.auth

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
        normalizeAsciiDigits(value).lowercase().take(24)

    fun validDisplayName(value: String): Boolean =
        value.trim().length in 2..80

    fun validUsername(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.matches(Regex("[a-z0-9_]{3,24}")) &&
            normalized !in reserved
    }

    fun validProfile(displayName: String, username: String): Boolean =
        validDisplayName(displayName) && validUsername(username)
}
