package org.athar.app

import org.athar.app.core.data.writeAccountExport
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class AccountExportWriterTest {
    @Test fun expiredOwnershipCannotEvenOpenTheDestination() {
        var opened = false
        assertTrue(runCatching { writeAccountExport(byteArrayOf(1), { error("expired") }) { opened = true; ByteArrayOutputStream() } }.isFailure)
        assertFalse(opened)
    }
    @Test fun completeCopyClosesAndChecksOwnershipAfterClose() {
        var closed = false; var checkedAfterClose = false
        val output = object : ByteArrayOutputStream() { override fun close() { closed = true } }
        val bytes = ByteArray(20000) { (it % 251).toByte() }
        writeAccountExport(bytes, { if (closed) checkedAfterClose = true }) { output }
        assertTrue(closed && checkedAfterClose); assertArrayEquals(bytes, output.toByteArray())
    }
    @Test fun accountChangeDuringWriteFailsAndClosesThePartialDestination() {
        var changed = false; var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) { super.write(bytes, offset, length); changed = true }
            override fun close() { closed = true }
        }
        assertTrue(runCatching { writeAccountExport(ByteArray(20000), { check(!changed) }) { output } }.isFailure)
        assertTrue(closed); assertEquals(8192, output.size())
    }
    @Test fun storageFailureOrFailureOnCloseNeverBecomesSuccess() {
        for (failOnClose in listOf(false, true)) {
            val output = object : ByteArrayOutputStream() {
                override fun write(bytes: ByteArray, offset: Int, length: Int) { if (!failOnClose) throw IOException("full fixture"); super.write(bytes, offset, length) }
                override fun close() { if (failOnClose) throw IOException("provider close fixture") }
            }
            assertTrue(runCatching { writeAccountExport(byteArrayOf(1), {}) { output } }.exceptionOrNull() is IOException)
        }
    }
}
