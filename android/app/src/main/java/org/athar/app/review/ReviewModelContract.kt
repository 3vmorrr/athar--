package org.athar.app.review

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.security.MessageDigest

data class ReviewModelArtifact(
    val id: String,
    val fileName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    fun validated(): ReviewModelArtifact {
        require(id in setOf("model", "corpus", "io"))
        require(fileName.matches(Regex("[A-Za-z0-9._-]{3,120}")))
        require(!fileName.contains(".."))
        require(relativePath == "assets/models/" + fileName)
        require(sizeBytes in 1..100_000_000L)
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        return this
    }
}

data class ReviewModelManifest(
    val schemaVersion: Int,
    val version: String,
    val licenseId: String,
    val paidFeatureProhibited: Boolean,
    val runtimeVerified: Boolean,
    val artifacts: List<ReviewModelArtifact>,
) {
    val totalBytes: Long get() = artifacts.sumOf { it.sizeBytes }

    fun validated(): ReviewModelManifest {
        require(schemaVersion == 1)
        require(version.matches(Regex("[a-z0-9][a-z0-9._-]{2,80}")))
        require(licenseId == "NPL-1.2")
        require(paidFeatureProhibited)
        require(artifacts.size == 3)
        require(artifacts.map { it.id }.toSet() == setOf("model", "corpus", "io"))
        require(artifacts.map { it.fileName }.toSet().size == artifacts.size)
        artifacts.forEach { it.validated() }
        require(totalBytes in 1..100_000_000L)
        return this
    }
}

object ReviewModelManifestCodec {
    fun decode(raw: String): ReviewModelManifest {
        require(raw.length in 100..20_000)
        val root = JsonParser.parseString(raw).asJsonObject
        val license = root.getAsJsonObject("license")
        val artifacts = root.getAsJsonArray("artifacts").map { element ->
            val item = element.asJsonObject
            ReviewModelArtifact(
                id = item.requiredString("id"),
                fileName = item.requiredString("file_name"),
                relativePath = item.requiredString("relative_path"),
                sizeBytes = item.requiredLong("size_bytes"),
                sha256 = item.requiredString("sha256"),
            ).validated()
        }
        return ReviewModelManifest(
            schemaVersion = root.requiredInt("schema_version"),
            version = root.requiredString("version"),
            licenseId = license.requiredString("id"),
            paidFeatureProhibited = license.requiredBoolean("paid_feature_prohibited"),
            runtimeVerified = root.requiredBoolean("runtime_verified"),
            artifacts = artifacts,
        ).validated()
    }

    fun encode(value: ReviewModelManifest): String {
        value.validated()
        return JsonObject().apply {
            addProperty("schema_version", value.schemaVersion)
            addProperty("version", value.version)
            add("license", JsonObject().apply {
                addProperty("id", value.licenseId)
                addProperty("paid_feature_prohibited", value.paidFeatureProhibited)
            })
            addProperty("runtime_verified", value.runtimeVerified)
            add("artifacts", JsonArray().apply {
                value.artifacts.forEach { artifact ->
                    add(JsonObject().apply {
                        addProperty("id", artifact.id)
                        addProperty("file_name", artifact.fileName)
                        addProperty("relative_path", artifact.relativePath)
                        addProperty("size_bytes", artifact.sizeBytes)
                        addProperty("sha256", artifact.sha256)
                    })
                }
            })
        }.toString()
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asInt
    }

    private fun JsonObject.requiredLong(name: String): Long {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asLong
    }

    private fun JsonObject.requiredBoolean(name: String): Boolean {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
        return value.asBoolean
    }
}

object ReviewModelDigest {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, artifact: ReviewModelArtifact): Boolean =
        file.isFile &&
            file.length() == artifact.sizeBytes &&
            sha256(file) == artifact.sha256
}

object ReviewModelUrlPolicy {
    fun resolve(apiBaseUrl: String, artifact: ReviewModelArtifact): String {
        artifact.validated()
        val api = URI(apiBaseUrl)
        require(api.scheme == "https")
        require(!api.host.isNullOrBlank())
        require(api.userInfo == null)
        val root = URI(
            "https",
            null,
            api.host,
            api.port,
            "/",
            null,
            null,
        )
        val resolved = root.resolve(artifact.relativePath)
        require(resolved.scheme == "https")
        require(resolved.host.equals(api.host, ignoreCase = true))
        require(resolved.port == api.port)
        require(resolved.userInfo == null)
        require(resolved.query == null && resolved.fragment == null)
        return resolved.toASCIIString()
    }
}

object ReviewModelStoragePolicy {
    const val RESERVE_BYTES = 32L * 1024L * 1024L

    fun requiredFreeBytes(manifest: ReviewModelManifest): Long =
        Math.addExact(manifest.validated().totalBytes, RESERVE_BYTES)

    fun hasEnoughSpace(availableBytes: Long, manifest: ReviewModelManifest): Boolean =
        availableBytes >= requiredFreeBytes(manifest)
}
