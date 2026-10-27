package org.athar.app.quran

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

data class QuranAyahSharePayload(
    val canonicalText: String,
    val reference: String,
    val source: String,
    val plainText: String,
)

object QuranAyahShareContract {
    const val SOURCE_NAME = "Tanzil Project 1.1"
    const val SOURCE_URL = "https://tanzil.net/"
    const val MAX_CANONICAL_TEXT_CHARS = 2_000
    const val MAX_PAYLOAD_CHARS = 2_300

    fun from(ayah: AyahText): QuranAyahSharePayload {
        require(ayah.position.surah in 1..114)
        require(ayah.position.ayah > 0)
        require(ayah.position.globalIndex in 1..6236)
        require(ayah.text.isNotBlank())
        require(ayah.text.length <= MAX_CANONICAL_TEXT_CHARS)
        require('\u0000' !in ayah.text)

        val reference = "[" + ayah.position.surah + ":" + ayah.position.ayah + "]"
        val source = SOURCE_NAME + " — " + SOURCE_URL
        val plain = buildString {
            append(ayah.text)
            append("\n\n")
            append(reference)
            append("\n")
            append(source)
        }
        require(plain.length <= MAX_PAYLOAD_CHARS)
        return QuranAyahSharePayload(
            canonicalText = ayah.text,
            reference = reference,
            source = source,
            plainText = plain,
        )
    }
}

object QuranAyahShareActions {
    fun copy(context: Context, payload: QuranAyahSharePayload) {
        require(payload.plainText.length <= QuranAyahShareContract.MAX_PAYLOAD_CHARS)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
            ?: error("clipboard_unavailable")
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "Quran " + payload.reference,
                payload.plainText,
            ),
        )
    }

    fun share(
        context: Context,
        payload: QuranAyahSharePayload,
        chooserTitle: String,
    ) {
        require(payload.plainText.length <= QuranAyahShareContract.MAX_PAYLOAD_CHARS)
        require(chooserTitle.isNotBlank() && chooserTitle.length <= 120)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, payload.plainText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
