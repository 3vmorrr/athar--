package org.athar.app.books

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class BookScope(val namespace: String, val owner: Long) {
    init { require(namespace.matches(Regex("[a-f0-9]{12}")) && owner > 0) }
    val key get() = "$namespace-u-$owner"
}

internal data class BookCopy(val file: File, val token: String, val size: Long, val sha256: String)

/** Immutable copies + an atomic manifest. Cancellation/revocation fence publication durably. */
internal class BookFiles(private val root: File, val scope: BookScope) {
    private val lock = locks.computeIfAbsent(File(root, scope.key).absolutePath) { Any() }
    private val directory get() = File(root, scope.key)
    private val revoked get() = File(root, "revoked-${scope.key}")
    private fun book(id: Int): File { require(id > 0); return File(directory, "book-$id") }
    private fun token(value: String): String = value.also { require(UUID.fromString(it).toString() == it) }
    private fun allowed() { if (revoked.exists()) throw IOException("book_owner_revoked") }
    fun ownerRevoked(): Boolean = synchronized(lock) { revoked.exists() }
    fun freeBytes(): Long = directory.usableSpace
    private fun active(id: Int, request: String): Boolean = !revoked.exists() &&
        File(book(id), "request").takeIf { it.isFile }?.readText(Charsets.UTF_8)?.trim() == request

    fun begin(id: Int, request: String = UUID.randomUUID().toString()): String = synchronized(lock) {
        allowed(); token(request)
        val folder = book(id)
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("book_storage_unavailable")
        atomic(File(folder, "request"), request)
        // Only uncommitted temporary data is disposable; keep the prior manifest and PDFs.
        folder.listFiles()?.filter { it.name.endsWith(".part") || it.name.startsWith(".atomic-") }?.forEach { it.delete() }
        request
    }

    fun currentRequest(id: Int): String? = synchronized(lock) {
        allowed()
        File(book(id), "request").takeIf { it.exists() }?.readText(Charsets.UTF_8)?.trim()?.let(::token)
    }

    fun knownBooks(): List<Int> = synchronized(lock) {
        allowed()
        if (!directory.exists()) return@synchronized emptyList()
        val entries = directory.listFiles() ?: throw IOException("book_inventory_unreadable")
        entries.filter { it.isDirectory }.mapNotNull {
            Regex("book-([1-9][0-9]*)").matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
        }.sorted()
    }

    fun isActive(id: Int, request: String): Boolean = synchronized(lock) { active(id, token(request)) }

    fun openPart(id: Int, request: String): FileOutputStream = synchronized(lock) {
        check(active(id, token(request))) { "book_request_changed" }
        FileOutputStream(File(book(id), "$request.part"))
    }

    fun discardPart(id: Int, request: String) = synchronized(lock) {
        File(book(id), "${token(request)}.part").delete()
        Unit
    }

    fun publish(id: Int, request: String, checkCancelled: () -> Unit = {}): BookCopy {
        token(request)
        val part = synchronized(lock) {
            check(active(id, request)) { "book_request_changed" }
            File(book(id), "$request.part")
        }
        val size = part.length()
        require(size in 5..BookDownloadPolicy.MAX_BYTES && pdfHeader(part))
        val hash = hash(part, checkCancelled)
        return synchronized(lock) {
            checkCancelled()
            check(active(id, request)) { "book_request_changed" }
            val destination = File(book(id), "$request-$hash.pdf")
            Files.move(part.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            // If this fails, the old manifest still names the old valid copy.
            atomic(File(book(id), "manifest"), "ATHAR-PDF-1\n$request\n$size\n$hash")
            book(id).listFiles()?.filter { it.name.endsWith(".pdf") && it != destination }?.forEach { it.delete() }
            BookCopy(destination, request, size, hash)
        }
    }

    fun current(id: Int): BookCopy? = synchronized(lock) {
        allowed()
        val manifest = File(book(id), "manifest")
        if (!manifest.exists()) return@synchronized null
        val lines = manifest.readLines(Charsets.UTF_8)
        if (lines.size != 4 || lines[0] != "ATHAR-PDF-1") throw IOException("book_manifest_unreadable")
        val request = runCatching { token(lines[1]) }.getOrElse { throw IOException("book_manifest_unreadable", it) }
        val size = lines[2].toLongOrNull()?.takeIf { it in 5..BookDownloadPolicy.MAX_BYTES } ?: throw IOException("book_manifest_unreadable")
        if (!lines[3].matches(Regex("[a-f0-9]{64}"))) throw IOException("book_manifest_unreadable")
        BookCopy(File(book(id), "$request-${lines[3]}.pdf"), request, size, lines[3])
    }

    fun verify(copy: BookCopy): Boolean = synchronized(lock) {
        allowed()
        copy.file.isFile && copy.file.length() == copy.size && pdfHeader(copy.file) && hash(copy.file) == copy.sha256
    }

    fun <T> openVerified(id: Int, open: (BookCopy) -> T): T = synchronized(lock) {
        val copy = current(id) ?: throw IOException("book_copy_missing")
        if (!verify(copy)) throw IOException("book_integrity_failed")
        open(copy)
    }

    fun cancel(id: Int) = synchronized(lock) {
        allowed()
        val folder = book(id)
        if (folder.exists()) atomic(File(folder, "request"), UUID.randomUUID().toString())
    }

    fun remove(id: Int) = synchronized(lock) {
        cancel(id)
        val folder = book(id)
        if (folder.exists() && !folder.deleteRecursively()) throw IOException("book_remove_failed")
    }

    fun revoke() = synchronized(lock) {
        if (!root.isDirectory && !root.mkdirs()) throw IOException("book_storage_unavailable")
        atomic(revoked, "revoked")
        if (directory.exists() && !directory.deleteRecursively()) throw IOException("book_owner_cleanup_failed")
    }

    private fun atomic(destination: File, text: String) {
        val temporary = File(destination.parentFile, ".atomic-${UUID.randomUUID()}")
        try {
            FileOutputStream(temporary).use { output -> output.write(text.toByteArray(Charsets.UTF_8)); output.fd.sync() }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    private fun pdfHeader(file: File): Boolean = runCatching {
        val bytes = ByteArray(5)
        DataInputStream(FileInputStream(file)).use { it.readFully(bytes) }
        bytes.contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2d))
    }.getOrDefault(false)

    private fun hash(file: File, checkCancelled: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkCancelled()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val locks = ConcurrentHashMap<String, Any>()

        /** A process may die or hold an open file after revocation; the tombstone survives. */
        fun recoverRevoked(root: File) {
            root.listFiles()?.forEach { marker ->
                val match = Regex("revoked-([a-f0-9]{12})-u-([1-9][0-9]*)").matchEntire(marker.name) ?: return@forEach
                val owner = match.groupValues[2].toLongOrNull() ?: return@forEach
                if (marker.isFile) BookFiles(root, BookScope(match.groupValues[1], owner)).revoke()
            }
        }
    }
}
