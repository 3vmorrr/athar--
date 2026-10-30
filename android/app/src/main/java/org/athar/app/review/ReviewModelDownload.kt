package org.athar.app.review

import android.content.Context
import androidx.work.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.athar.app.AtharApplication
import org.athar.app.auth.AuthState
import org.athar.app.auth.SessionRepository
import org.athar.app.core.sync.BackgroundWorkBudgets
import org.athar.app.core.sync.BackgroundWorkConstraints

class ReviewModelManager(
    context: Context,
    private val partition: String,
    private val session: SessionRepository,
    val apiBaseUrl: String,
) {
    private val app = context.applicationContext
    private val loader = ReviewModelManifestLoader(app)
    val manifest: ReviewModelManifest = loader.load()
    internal val store = ReviewModelStore(app)
    internal val consent = ReviewModelConsentStore(app)
    private val work = WorkManager.getInstance(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        require(partition.matches(Regex("[a-f0-9]{12}")))
        manifest.validated()
        scope.launch {
            var previousOwner: Long? = null
            session.state.collectLatest { state ->
                val owner = (state as? AuthState.SignedIn)?.user?.id
                if (owner == null) {
                    previousOwner?.let(::cancel)
                    previousOwner = null
                    return@collectLatest
                }
                if (previousOwner != null && previousOwner != owner) {
                    cancel(requireNotNull(previousOwner))
                }
                previousOwner = owner
            }
        }
    }

    fun licenseText(): String = loader.licenseText()
    fun noticeText(): String = loader.noticeText()
    fun accepted(owner: Long): Boolean = consent.accepted(owner, manifest)
    fun installed(): Boolean = store.isActive(manifest)
    fun activeVersion(): String? = store.activeVersion()
    fun previousVersion(): String? = store.previousVersion()
    fun availableBytes(): Long = store.availableBytes()
    fun requiredFreeBytes(): Long = ReviewModelStoragePolicy.requiredFreeBytes(manifest)

    fun hostReady(): Boolean = runCatching {
        manifest.artifacts.forEach { ReviewModelUrlPolicy.resolve(apiBaseUrl, it) }
    }.isSuccess

    fun workName(owner: Long): String {
        require(owner > 0)
        return "review-model-" + partition + "-" + owner + "-" + manifest.version
    }

    fun observe(owner: Long) =
        work.getWorkInfosForUniqueWorkFlow(workName(owner))

    internal fun artifact(id: String): ReviewModelArtifact =
        manifest.artifacts.single { it.id == id }

    internal fun verifiedArtifactFile(id: String): File {
        val artifact = artifact(id)
        val file = store.artifactFile(manifest, artifact)
        require(ReviewModelDigest.verify(file, artifact))
        return file
    }

    suspend fun accept(owner: Long) {
        ReviewModelAccess(session, consent, store, manifest).accept(owner)
    }

    suspend fun enqueue(owner: Long) {
        session.requireVerifiedOwner(owner)
        require(accepted(owner))
        require(hostReady())
        require(
            ReviewModelStoragePolicy.hasEnoughSpace(
                store.availableBytes(),
                manifest,
            ),
        )
        val request = OneTimeWorkRequestBuilder<ReviewModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    ReviewModelDownloadWorker.ACTOR to owner,
                    ReviewModelDownloadWorker.ENVIRONMENT to partition,
                    ReviewModelDownloadWorker.VERSION to manifest.version,
                ),
            )
            .setConstraints(
                BackgroundWorkConstraints.forBudget(
                    BackgroundWorkBudgets.REVIEW_MODEL_DOWNLOAD,
                ),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .addTag(ownerTag(owner))
            .build()
        work.enqueueUniqueWork(
            workName(owner),
            ExistingWorkPolicy.KEEP,
            request,
        )
        session.requireVerifiedOwner(owner)
    }

    fun cancel(owner: Long) {
        if (owner > 0) work.cancelAllWorkByTag(ownerTag(owner))
    }

    suspend fun remove(owner: Long) {
        session.requireVerifiedOwner(owner)
        cancel(owner)
        ReviewModelAccess(session, consent, store, manifest).remove(owner)
    }

    suspend fun rollback(owner: Long): Boolean {
        session.requireVerifiedOwner(owner)
        val result = store.rollback()
        session.requireVerifiedOwner(owner)
        return result
    }

    private fun ownerTag(owner: Long) =
        "review-model-" + partition + "-" + owner
}

private class ReviewModelHttpException(val status: Int) : IOException()
private class ReviewModelIntegrityException : IOException()

class ReviewModelDownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    @Volatile private var currentCall: Call? = null
    @Volatile private var currentTemporary: File? = null

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.MINUTES)
        .build()

    override suspend fun doWork(): Result {
        val owner = inputData.getLong(ACTOR, -1)
        val environment = inputData.getString(ENVIRONMENT)
        val version = inputData.getString(VERSION)
        if (
            owner <= 0 ||
            environment == null ||
            !environment.matches(Regex("[a-f0-9]{12}")) ||
            version == null
        ) {
            return Result.failure(workDataOf(REASON to "invalid_input"))
        }

        val graph = (applicationContext as AtharApplication).graph
        val manager = graph.reviewModels
        if (environment != graph.partition || version != manager.manifest.version) {
            return Result.failure(workDataOf(REASON to "stale_manifest"))
        }

        return try {
            if (graph.session.state.value is AuthState.Restoring) {
                graph.session.restore()
            }
            graph.session.requireVerifiedOwner(owner)
            if (!manager.accepted(owner)) {
                return Result.failure(workDataOf(REASON to "consent_required"))
            }
            if (!manager.hostReady()) {
                return Result.failure(workDataOf(REASON to "https_host_required"))
            }
            if (
                !ReviewModelStoragePolicy.hasEnoughSpace(
                    manager.store.availableBytes(),
                    manager.manifest,
                )
            ) {
                return Result.failure(workDataOf(REASON to "low_storage"))
            }

            manager.store.deleteTemporaryFiles(manager.manifest)
            var completed = manager.manifest.artifacts
                .filter { ReviewModelDigest.verify(manager.store.artifactFile(manager.manifest, it), it) }
                .sumOf { it.sizeBytes }
            setProgress(workDataOf(DOWNLOADED to completed, TOTAL to manager.manifest.totalBytes))

            for (artifact in manager.manifest.artifacts) {
                graph.session.requireVerifiedOwner(owner)
                val target = manager.store.artifactFile(manager.manifest, artifact)
                if (ReviewModelDigest.verify(target, artifact)) continue
                download(manager, artifact, target)
                completed += artifact.sizeBytes
                setProgress(
                    workDataOf(
                        DOWNLOADED to completed,
                        TOTAL to manager.manifest.totalBytes,
                    ),
                )
            }

            graph.session.requireVerifiedOwner(owner)
            if (!manager.store.verified(manager.manifest)) {
                throw ReviewModelIntegrityException()
            }
            manager.store.activate(manager.manifest)
            Result.success(
                workDataOf(
                    DOWNLOADED to manager.manifest.totalBytes,
                    TOTAL to manager.manifest.totalBytes,
                ),
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: ReviewModelHttpException) {
            if (
                runAttemptCount < 3 &&
                (
                    failure.status == 408 ||
                        failure.status == 425 ||
                        failure.status == 429 ||
                        failure.status >= 500
                    )
            ) {
                Result.retry()
            } else {
                Result.failure(workDataOf(REASON to "http_" + failure.status))
            }
        } catch (_: ReviewModelIntegrityException) {
            Result.failure(workDataOf(REASON to "integrity_failed"))
        } catch (_: IOException) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf(REASON to "network_failed"))
        } catch (_: Exception) {
            Result.failure(workDataOf(REASON to "download_failed"))
        } finally {
            currentCall = null
            currentTemporary?.delete()
            currentTemporary = null
        }
    }

    private suspend fun download(
        manager: ReviewModelManager,
        artifact: ReviewModelArtifact,
        target: File,
    ) = withContext(Dispatchers.IO) {
        val temporary = manager.store.temporaryFile(manager.manifest, artifact)
        currentTemporary = temporary
        target.parentFile?.mkdirs()
        temporary.delete()

        val url = ReviewModelUrlPolicy.resolve(manager.apiBaseUrl, artifact)
        val call = client.newCall(Request.Builder().url(url).get().build())
        currentCall = call
        call.execute().use { response ->
            if (!response.isSuccessful) throw ReviewModelHttpException(response.code)
            val body = response.body ?: throw IOException("empty_model_body")
            val declared = body.contentLength()
            if (declared >= 0 && declared != artifact.sizeBytes) {
                throw ReviewModelIntegrityException()
            }

            var total = 0L
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            body.byteStream().buffered().use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isStopped) throw CancellationException("worker_stopped")
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        if (total > artifact.sizeBytes) {
                            throw ReviewModelIntegrityException()
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }

            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (total != artifact.sizeBytes || sha != artifact.sha256) {
                throw ReviewModelIntegrityException()
            }
            if (target.exists() && !target.delete()) {
                throw IOException("unable_to_replace_model_file")
            }
            if (!temporary.renameTo(target)) {
                throw IOException("unable_to_commit_model_file")
            }
            currentTemporary = null
        }
    }


    companion object {
        const val ACTOR = "actor_id"
        const val ENVIRONMENT = "environment"
        const val VERSION = "model_version"
        const val DOWNLOADED = "downloaded_bytes"
        const val TOTAL = "total_bytes"
        const val REASON = "reason"
    }
}
