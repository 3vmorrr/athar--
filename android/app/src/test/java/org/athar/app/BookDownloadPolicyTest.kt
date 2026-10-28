package org.athar.app

import org.athar.app.books.BookDownloadPolicy
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class BookDownloadPolicyTest {
    @Test fun sourceAndRedirectUrlsRejectPrivateDestinationsCredentialsAndAlternatePorts() {
        for (url in listOf("http://example.org/a.pdf", "https://user:pass@example.org/a.pdf", "https://example.org:8443/a.pdf",
            "https://127.0.0.1/a.pdf", "https://127.1/a.pdf", "https://2130706433/a.pdf", "https://[::1]/a.pdf",
            "https://100.64.0.1/a.pdf", "https://192.0.2.1/a.pdf", "https://[2001:db8::1]/a.pdf", "https://local.local/a.pdf",
            "https://localhost./a.pdf", "https://example.org/a\n.pdf", "file:///tmp/a.pdf")) assertFalse(url, BookDownloadPolicy.safeHttps(url))
        assertTrue(BookDownloadPolicy.safeHttps("https://example.org/a.pdf?edition=1"))
        assertTrue(BookDownloadPolicy.safeHttps("https://example.org:443/a.pdf"))
    }
    @Test fun dnsAnswersAreCheckedIndependentlyOfTheHostname() {
        for (ip in listOf("10.0.0.1", "172.16.0.1", "192.168.0.1", "169.254.0.1", "100.127.255.1", "198.18.0.1", "fc00::1"))
            assertFalse(ip, BookDownloadPolicy.publicAddress(InetAddress.getByName(ip)))
        assertTrue(BookDownloadPolicy.publicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(BookDownloadPolicy.publicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
}
