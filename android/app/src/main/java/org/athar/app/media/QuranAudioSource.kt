package org.athar.app.media

/** Canonical provider selection, distinct from radio and from current playback state. */
internal data class QuranAudioSource(val reciter: Int, val edition: Int, val surah: Int, val ayah: Int? = null) {
    init {
        require(reciter in 1..1_000_000 && edition in 1..1_000_000)
        require(surah in 1..114 && (ayah == null || ayah in 1..286))
    }
    val mediaKey: String get() = "$reciter:$edition:$surah:${ayah ?: 0}"
    companion object {
        fun fromKey(key: String): QuranAudioSource? = runCatching {
            val parts = key.split(':')
            require(parts.size == 4)
            val values = parts.map { it.toInt() }
            QuranAudioSource(values[0], values[1], values[2], values[3].takeUnless { it == 0 })
                .also { require(it.mediaKey == key) }
        }.getOrNull()
    }
}
