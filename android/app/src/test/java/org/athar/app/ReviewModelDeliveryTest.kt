package org.athar.app

import java.io.File
import org.athar.app.review.*
import org.junit.Assert.*
import org.junit.Test

class ReviewModelDeliveryTest {
    private fun manifest(
        hash: String = "a".repeat(64),
        size: Long = 10,
    ) = ReviewModelManifest(
        schemaVersion = 1,
        version = "zipformer-test-v1",
        licenseId = "NPL-1.2",
        paidFeatureProhibited = true,
        runtimeVerified = false,
        artifacts = listOf(
            ReviewModelArtifact("model", "model.onnx", "assets/models/model.onnx", size, hash),
            ReviewModelArtifact("corpus", "corpus.json", "assets/models/corpus.json", size, hash),
            ReviewModelArtifact("io", "io.json", "assets/models/io.json", size, hash),
        ),
    ).validated()

    @Test fun manifestIsStrictVersionedLicensedAndBounded() {
        val value = manifest()
        assertEquals("NPL-1.2", value.licenseId)
        assertTrue(value.paidFeatureProhibited)
        assertFalse(value.runtimeVerified)
        assertEquals(30, value.totalBytes)
        assertTrue(
            runCatching {
                value.copy(paidFeatureProhibited = false).validated()
            }.isFailure,
        )
    }

    @Test fun manifestCodecRoundTripsExactHashesAndPaths() {
        val original = manifest()
        val restored = ReviewModelManifestCodec.decode(
            ReviewModelManifestCodec.encode(original),
        )
        assertEquals(original, restored)
    }

    @Test fun downloadUrlMustRemainOnSameHttpsOrigin() {
        val artifact = manifest().artifacts.first()
        assertEquals(
            "https://example.org/assets/models/model.onnx",
            ReviewModelUrlPolicy.resolve(
                "https://example.org/api/v1/",
                artifact,
            ),
        )
        assertTrue(
            runCatching {
                ReviewModelUrlPolicy.resolve(
                    "http://example.org/api/v1/",
                    artifact,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                artifact.copy(
                    relativePath = "https://evil.invalid/model.onnx",
                ).validated()
            }.isFailure,
        )
    }

    @Test fun storagePolicyRetainsExplicitSafetyReserve() {
        val value = manifest(size = 1_000)
        val required = ReviewModelStoragePolicy.requiredFreeBytes(value)
        assertEquals(
            value.totalBytes + ReviewModelStoragePolicy.RESERVE_BYTES,
            required,
        )
        assertTrue(ReviewModelStoragePolicy.hasEnoughSpace(required, value))
        assertFalse(ReviewModelStoragePolicy.hasEnoughSpace(required - 1, value))
    }

    @Test fun digestVerifierRejectsPartialAndCorruptFiles() {
        val file = File.createTempFile("athar-review-", ".bin")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3, 4))
            val hash = ReviewModelDigest.sha256(file)
            val artifact = ReviewModelArtifact(
                "model",
                "model.onnx",
                "assets/models/model.onnx",
                4,
                hash,
            ).validated()
            assertTrue(ReviewModelDigest.verify(file, artifact))
            assertFalse(
                ReviewModelDigest.verify(
                    file,
                    artifact.copy(sizeBytes = 5),
                ),
            )
            file.appendBytes(byteArrayOf(5))
            assertFalse(ReviewModelDigest.verify(file, artifact))
        } finally {
            file.delete()
        }
    }
}
