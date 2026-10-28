package org.athar.app.media

internal const val PLAYBACK_OWNER_HINT = "org.athar.app.media.OWNER"

/** Queue ownership, separate from Media3's verified controller identity. */
internal class PlaybackOwnership {
    var owner: Long? = null
        private set

    fun update(value: Long?): Boolean {
        val next = value?.takeIf { it > 0 }
        if (next == owner) return false
        owner = next
        return true
    }

    fun allows(appUid: Int, controllerUid: Int, trusted: Boolean, requestedOwner: Long?): Boolean {
        val current = owner ?: return false
        if (controllerUid != appUid && !trusted) return false
        return requestedOwner == null || requestedOwner == current
    }

    fun allowsMediaReplacement(appUid: Int, controllerUid: Int, trusted: Boolean, requestedOwner: Long?): Boolean =
        controllerUid == appUid && allows(appUid, controllerUid, trusted, requestedOwner)
}
