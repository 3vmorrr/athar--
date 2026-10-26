package org.athar.app

import org.athar.app.auth.OtpInput
import org.junit.Assert.*
import org.junit.Test

class OtpInputTest {
    @Test fun arabicAndEasternDigitsProduceTheSameCodeAsAnEnglishKeyboard() {
        assertEquals("012345", OtpInput.normalize("٠١٢٣٤٥"))
        assertEquals("012345", OtpInput.normalize("۰۱۲۳۴۵"))
        assertEquals("012345", OtpInput.normalize("012345"))
        assertEquals("091827", OtpInput.normalize("٠9۱٨2۷"))
    }
    @Test fun pastedSeparatorsAndDirectionMarksDoNotDiscardLeadingZeroes() {
        assertEquals("001234", OtpInput.normalize("\u200f٠٠١ ۲-٣۴\u200e"))
        assertEquals("", OtpInput.normalize("\u200fabc🙂\u200e"))
    }
    @Test fun codeLengthIsBoundedAndNormalizationIsIdempotent() {
        val normalized = OtpInput.normalize("۱۲٣456789")
        assertEquals("123456", normalized)
        assertEquals(normalized, OtpInput.normalize(normalized))
    }
}
