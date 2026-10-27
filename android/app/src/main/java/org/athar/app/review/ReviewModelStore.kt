package org.athar.app.review

import android.content.Context
import android.os.StatFs
import java.io.File
import org.athar.app.auth.SessionRepository

class ReviewModelManifestLoader(private val context: Context) {
    fun raw(): String =
        context.assets.open("review/model_manifest.json").bufferedReader().use { it.readText() }

    fun load(): ReviewModelManifest = ReviewModelManifestCodec.decode(raw())

    fun licenseText(): String =
        context.assets.open("review/NPL-1.2.txt").bufferedReader().use { it.readText() }

    fun noticeText(): String =
        context.assets.open("review/NOTICE.md").bufferedReader().use { it.readText() }
}

class ReviewModelConsentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "athar-review-model-consent",
        Context.MODE_PRIVATE,
    )

    fun accepted(owner: Long, manifest: ReviewModelManifest): Boolean {
        require(owner > 0)
        manifest.validated()
        return preferences.getString(key(owner), null) ==
            manifest.version + "|" + manifest.licenseId
    }

    fun accept(owner: Long, manifest: ReviewModelManifest) {
        require(owner > 0)
        manifest.validated()
        check(
            preferences.edit()
                .putString(key(owner), manifest.version + "|" + manifest.licenseId)
                .commit(),
        )
    }

    fun revoke(owner: Long) {
        require(owner > 0)
        check(preferences.edit().remove(key(owner)).commit())
    }

    private fun key(owner: Long) = "owner-" + owner
}

class ReviewModelStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "review-models")
    private val preferences = app.getSharedPreferences(
        "athar-review-model-state",
        Context.MODE_PRIVATE,
    )

    fun availableBytes(): Long = StatFs(app.filesDir.absolutePath).availableBytes

    fun versionDirectory(version: String): File {
        require(version.matches(Regex("[a-z0-9][a-z0-9._-]{2,80}")))
        return File(root, version)
    }

    fun artifactFile(
        manifest: ReviewModelManifest,
        artifact: ReviewModelArtifact,
    ): File {
        manifest.validated()
        artifact.validated()
        require(artifact in manifest.artifacts)
        return File(versionDirectory(manifest.version), artifact.fileName)
    }

    fun temporaryFile(
        manifest: ReviewModelManifest,
        artifact: ReviewModelArtifact,
    ): File = File(
        versionDirectory(manifest.version),
        artifact.fileName + ".part",
    )

    fun verified(manifest: ReviewModelManifest): Boolean =
        manifest.validated().artifacts.all { artifact ->
            ReviewModelDigest.verify(artifactFile(manifest, artifact), artifact)
        }

    fun activeVersion(): String? = preferences.getString(KEY_ACTIVE, null)
    fun previousVersion(): String? = preferences.getString(KEY_PREVIOUS, null)

    fun isActive(manifest: ReviewModelManifest): Boolean =
        activeVersion() == manifest.version && verified(manifest)

    fun activate(manifest: ReviewModelManifest) {
        require(verified(manifest))
        val directory = versionDirectory(manifest.version)
        check(directory.isDirectory)
        val manifestFile = File(directory, MANIFEST_FILE)
        val temporary = File(directory, MANIFEST_FILE + ".part")
        temporary.writeText(ReviewModelManifestCodec.encode(manifest), Charsets.UTF_8)
        if (manifestFile.exists()) check(manifestFile.delete())
        check(temporary.renameTo(manifestFile))

        val oldActive = activeVersion()
        val editor = preferences.edit().putString(KEY_ACTIVE, manifest.version)
        if (oldActive != null && oldActive != manifest.version) {
            editor.putString(KEY_PREVIOUS, oldActive)
        }
        check(editor.commit())
        cleanup()
    }

    fun rollback(): Boolean {
        val previous = previousVersion() ?: return false
        val previousManifest = manifestForVersion(previous) ?: return false
        if (!verified(previousManifest)) return false
        val current = activeVersion()
        val editor = preferences.edit().putString(KEY_ACTIVE, previous)
        if (current != null && current != previous) {
            editor.putString(KEY_PREVIOUS, current)
        } else {
            editor.remove(KEY_PREVIOUS)
        }
        return editor.commit()
    }

    fun removeAll() {
        if (root.exists()) check(root.deleteRecursively())
        check(
            preferences.edit()
                .remove(KEY_ACTIVE)
                .remove(KEY_PREVIOUS)
                .commit(),
        )
    }

    fun deleteTemporaryFiles(manifest: ReviewModelManifest) {
        manifest.validated().artifacts.forEach { artifact ->
            temporaryFile(manifest, artifact).delete()
        }
    }

    private fun manifestForVersion(version: String): ReviewModelManifest? {
        val file = File(versionDirectory(version), MANIFEST_FILE)
        if (!file.isFile || file.length() !in 100..20_000) return null
        return runCatching {
            ReviewModelManifestCodec.decode(file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    private fun cleanup() {
        if (!root.isDirectory) return
        val keep = setOfNotNull(activeVersion(), previousVersion())
        root.listFiles()
            ?.filter { it.isDirectory && it.name !in keep }
            ?.forEach { it.deleteRecursively() }
    }

    companion object {
        private const val KEY_ACTIVE = "active_version"
        private const val KEY_PREVIOUS = "previous_version"
        private const val MANIFEST_FILE = "manifest.json"
    }
}

class ReviewModelAccess(
    private val session: SessionRepository,
    private val consent: ReviewModelConsentStore,
    private val store: ReviewModelStore,
    private val manifest: ReviewModelManifest,
) {
    suspend fun accept(owner: Long) {
        session.requireVerifiedOwner(owner)
        consent.accept(owner, manifest)
        session.requireVerifiedOwner(owner)
    }

    suspend fun revokeConsent(owner: Long) {
        session.requireVerifiedOwner(owner)
        consent.revoke(owner)
        session.requireVerifiedOwner(owner)
    }

    suspend fun remove(owner: Long) {
        session.requireVerifiedOwner(owner)
        store.removeAll()
        session.requireVerifiedOwner(owner)
    }
}
