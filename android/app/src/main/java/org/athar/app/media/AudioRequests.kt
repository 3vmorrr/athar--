package org.athar.app.media

/** Fences responses even when a cancelled transport finishes after a new UI intent. */
internal class AudioRequests {
    var generation = 0L
        private set
    private var key: String? = null
    fun begin(mediaKey: String?): Long {
        key = mediaKey
        return ++generation
    }
    fun current(request: Long): Boolean = request == generation
    fun selectionChanged(selectedKey: String?): Boolean {
        val pending = key ?: return false
        if (pending.startsWith("radio:") || pending == selectedKey) return false
        begin(null)
        return true
    }
}
