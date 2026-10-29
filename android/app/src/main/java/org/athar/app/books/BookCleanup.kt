package org.athar.app.books

import android.content.Context
import java.io.File

/** Old revocation markers were created only after successful remote deletion. No files are removed here. */
internal fun legacyRevokedBookOwners(context: Context, namespace: String): List<Long> {
    require(namespace.matches(Regex("[a-f0-9]{12}")))
    return File(context.filesDir, "books").listFiles().orEmpty().mapNotNull { marker ->
        val match = Regex("revoked-([a-f0-9]{12})-u-([1-9][0-9]*)").matchEntire(marker.name) ?: return@mapNotNull null
        if (marker.isFile && match.groupValues[1] == namespace) match.groupValues[2].toLongOrNull() else null
    }
}
