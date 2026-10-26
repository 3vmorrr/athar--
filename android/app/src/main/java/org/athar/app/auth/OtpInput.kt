package org.athar.app.auth

/** OTP transport uses ASCII digits; Arabic keyboards/paste remain first-class input. Never apply to passwords. */
object OtpInput {
    fun normalize(value: String): String = buildString {
        for (character in value) {
            val digit = when (character) {
                in '0'..'9' -> character
                in '٠'..'٩' -> '0' + (character - '٠')
                in '۰'..'۹' -> '0' + (character - '۰')
                else -> continue
            }
            append(digit)
            if (length == 6) break
        }
    }
}
