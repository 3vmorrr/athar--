package org.athar.app

import org.athar.app.quran.AyahText
import org.athar.app.quran.QuranAyahShareContract
import org.athar.app.quran.QuranPosition
import org.junit.Assert.*
import org.junit.Test

class QuranAyahShareContractTest {
    @Test fun payloadKeepsCanonicalVerseBytesUnchanged() {
        val canonical = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ"
        val payload = QuranAyahShareContract.from(
            AyahText(
                position = QuranPosition(2, 255, 262),
                text = canonical,
                bismillah = null,
            ),
        )
        assertEquals(canonical, payload.canonicalText)
        assertTrue(payload.plainText.startsWith(canonical + "\n\n"))
        assertEquals("[2:255]", payload.reference)
        assertEquals(
            "Tanzil Project 1.1 — https://tanzil.net/",
            payload.source,
        )
        assertTrue(payload.plainText.contains(payload.reference))
        assertTrue(payload.plainText.endsWith(payload.source))
    }

    @Test fun payloadIsBoundedAndContainsNoBismillahFabrication() {
        val payload = QuranAyahShareContract.from(
            AyahText(
                position = QuranPosition(1, 1, 1),
                text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                bismillah = "separate-source-attribute",
            ),
        )
        assertTrue(payload.plainText.length <= QuranAyahShareContract.MAX_PAYLOAD_CHARS)
        assertFalse(payload.plainText.contains("separate-source-attribute"))
    }

    @Test fun malformedOrOversizedCanonicalTextFailsClosed() {
        assertTrue(
            runCatching {
                QuranAyahShareContract.from(
                    AyahText(QuranPosition(1, 1, 1), "", null),
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                QuranAyahShareContract.from(
                    AyahText(
                        QuranPosition(1, 1, 1),
                        "ا".repeat(QuranAyahShareContract.MAX_CANONICAL_TEXT_CHARS + 1),
                        null,
                    ),
                )
            }.isFailure,
        )
    }
}
