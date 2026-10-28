package org.athar.app.core.data

import java.io.OutputStream

internal const val MAX_ACCOUNT_EXPORT_BYTES = 32 * 1024 * 1024

/** Ownership is rechecked before acquiring the capability, during writes and after close. */
internal fun writeAccountExport(bytes: ByteArray, authorize: () -> Unit, open: () -> OutputStream) {
    require(bytes.isNotEmpty() && bytes.size <= MAX_ACCOUNT_EXPORT_BYTES)
    authorize()
    open().use { output ->
        var offset = 0
        while (offset < bytes.size) {
            authorize()
            val length = minOf(8192, bytes.size - offset)
            output.write(bytes, offset, length); offset += length
        }
        output.flush(); authorize()
    }
    authorize()
}
