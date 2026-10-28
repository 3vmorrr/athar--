package org.athar.app.books

import java.net.InetAddress
import java.net.URI

/** Public HTTPS only; the downloader must also check every resolved address and redirect. */
internal object BookDownloadPolicy {
    const val MAX_BYTES = 200L * 1024 * 1024
    const val MAX_REDIRECTS = 5
    const val MIN_FREE_BYTES = 32L * 1024 * 1024

    fun safeHttps(value: String): Boolean {
        if (value.isBlank() || value.length > 2048 || value.any { it <= ' ' || it == '\u007f' }) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null || uri.port !in listOf(-1, 443)) return false
        val host = uri.host?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return false
        if (host.isBlank() || host.endsWith('.') || host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) return false
        if (host.startsWith("0x", true) && host.drop(2).all { it in "0123456789abcdefABCDEF" }) return false
        // Only numeric literals are parsed here; domain resolution belongs to the network worker.
        if (host.contains(':') || host.matches(Regex("[0-9.]+"))) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
            if (!publicAddress(address)) return false
        }
        return true
    }

    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        if (bytes.size == 4) {
            val a = bytes[0]; val b = bytes[1]; val c = bytes[2]
            if (a == 0 || a >= 240 || (a == 100 && b in 64..127) || (a == 198 && b in 18..19)) return false
            if ((a == 192 && b == 0 && (c == 0 || c == 2)) || (a == 198 && b == 51 && c == 100) || (a == 203 && b == 0 && c == 113)) return false
        } else if (bytes.size == 16) {
            if (bytes[0] and 0xfe == 0xfc) return false
            if (bytes.take(4) == listOf(0x20, 0x01, 0x0d, 0xb8)) return false
        } else return false
        return true
    }
}
