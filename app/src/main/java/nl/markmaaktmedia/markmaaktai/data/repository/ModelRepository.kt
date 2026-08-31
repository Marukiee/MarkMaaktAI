package nl.markmaaktmedia.markmaaktai.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.markmaaktai.ai.ModelRole
import nl.markmaaktmedia.markmaaktai.ai.ModelSpec
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A model file that is actually on this phone. */
data class InstalledModel(
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val role: ModelRole,
    val isActive: Boolean,
)

/** Progress of a download, keyed by [ModelSpec.id]. */
data class DownloadProgress(
    val specId: String,
    val fraction: Float,
    val bytesDone: Long,
    val bytesTotal: Long,
    val error: String? = null,
)

/**
 * Owns the model files.
 *
 * Two ways in. The file picker copies whatever the user already has on the phone
 * into app storage, which is the primary route: it works with no network at all and
 * it keeps a several gigabyte file out of the download flow. The downloader is the
 * convenience path for someone starting from nothing.
 *
 * Everything ends up in the app's own files directory. Not the shared Downloads
 * folder: a model is app data, and on a hardened ROM the fewer storage permissions
 * this app needs, the better.
 */
@Singleton
class ModelRepository @Inject constructor(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val notifications: nl.markmaaktmedia.markmaaktai.service.notifications.NotificationPresenter,
) {

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    /**
     * Downloads run on the repository's own scope, not the caller's.
     *
     * A model is a gigabyte and a half, and the screen that started it is the
     * onboarding, which the user leaves as soon as they press Next. Tying the transfer
     * to that screen's view model means the download dies the moment they move on,
     * which is exactly when they expect it to be getting on with it.
     */
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val jobs = mutableMapOf<String, Job>()

    /**
     * A client of its own for model transfers. The shared one has a read timeout
     * meant for API calls, and applying that to a gigabyte over mobile data is what
     * turns a slow minute into a failed download.
     */
    private val downloadClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }

    fun speechDir(): File = File(context.filesDir, "speech").apply { mkdirs() }

    suspend fun installed(): List<InstalledModel> = withContext(Dispatchers.IO) {
        val prefs = settings.current()
        val active = setOf(prefs.textModelPath, prefs.visionModelPath, prefs.speechModelPath)
        modelsDir().listFiles().orEmpty()
            .filter { it.isFile }
            .sortedBy { it.name.lowercase() }
            .map { file ->
                InstalledModel(
                    fileName = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    role = guessRole(file.name),
                    isActive = file.absolutePath in active,
                )
            }
    }

    fun isInstalled(spec: ModelSpec): Boolean = File(modelsDir(), spec.fileName).exists()

    /**
     * Picks up a model that is on disk but not selected.
     *
     * Settings and files can drift apart: a restore, a cleared data directory, or a
     * file dropped in by hand. When that happens the app would sit there saying it has
     * no model while a perfectly good one is a folder away, and the only way out is a
     * gigabyte of downloading. This looks first and returns whether it found anything.
     */
    suspend fun adoptExistingModels(): Boolean = withContext(Dispatchers.IO) {
        val prefs = settings.current()
        var adopted = false

        val candidates = modelsDir().listFiles().orEmpty()
            .filter { it.isFile && it.length() > MIN_MODEL_BYTES && it.extension != "part" }

        if (prefs.textModelPath.isBlankOrMissing()) {
            candidates.firstOrNull { guessRole(it.name) == ModelRole.TEXT }?.let {
                settings.setTextModelPath(it.absolutePath)
                adopted = true
            }
        }
        if (prefs.visionModelPath.isBlankOrMissing()) {
            candidates.firstOrNull { guessRole(it.name) == ModelRole.VISION }?.let {
                settings.setVisionModelPath(it.absolutePath)
                adopted = true
            }
        }
        if (prefs.speechModelPath.isBlankOrMissing()) {
            speechDir().listFiles().orEmpty().firstOrNull { it.isDirectory }?.let {
                settings.setSpeechModelPath(it.absolutePath)
                adopted = true
            }
        }
        adopted
    }

    private fun String.isBlankOrMissing(): Boolean = isBlank() || !File(this).exists()

    fun fileFor(spec: ModelSpec): File = File(modelsDir(), spec.fileName)

    /**
     * Copies a file the user picked into app storage. The copy is deliberate: a
     * content URI from the picker is not readable after a reboot, and the runtime
     * needs a real path it can memory map.
     */
    suspend fun importFromUri(uri: Uri, role: ModelRole): Result<InstalledModel> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = displayName(uri) ?: "model-${System.currentTimeMillis()}.task"
                val target = File(modelsDir(), name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, COPY_BUFFER) }
                } ?: error("The picked file could not be opened")

                if (target.length() == 0L) {
                    target.delete()
                    error("The picked file was empty")
                }

                InstalledModel(
                    fileName = target.name,
                    path = target.absolutePath,
                    sizeBytes = target.length(),
                    role = role,
                    isActive = false,
                )
            }
        }

    /**
     * Starts a download if one is not already running for this model, and returns
     * immediately. Progress is reported through [downloads].
     */
    fun startDownload(spec: ModelSpec, onFinished: suspend (Result<File>) -> Unit = {}) {
        if (jobs[spec.id]?.isActive == true) return
        jobs[spec.id] = downloadScope.launch {
            val result = download(spec)
            jobs.remove(spec.id)
            onFinished(result)
        }
    }

    fun isDownloading(specId: String): Boolean = jobs[specId]?.isActive == true

    /**
     * Downloads a catalogue entry, resuming and retrying as needed.
     *
     * A model is well over a gigabyte, which on a phone means minutes on a connection
     * that will move between cells, drop to nothing in a lift, or be put to sleep
     * behind a screen lock. A single long lived socket does not survive that, and the
     * failure it produces is "software caused connection abort" a few hundred
     * megabytes in.
     *
     * So the transfer is built to be interrupted. Bytes land in a .part file that is
     * never thrown away on failure, each attempt asks the server to continue from
     * wherever that file ended with a Range header, and a dropped connection is
     * retried with a growing pause between tries. Progress counts from the whole
     * file, not from the current attempt, so resuming does not make the bar go
     * backwards.
     *
     * The client is its own instance with the read timeout switched off. The shared
     * one is tuned for API calls, where a stalled response should give up quickly;
     * here a slow minute is normal and giving up on it is the bug.
     */
    suspend fun download(spec: ModelSpec): Result<File> = withContext(Dispatchers.IO) {
        val url = spec.downloadUrl ?: return@withContext Result.failure(
            IllegalArgumentException("That model has no download link")
        )
        val target = File(modelsDir(), spec.fileName)
        val partial = File(modelsDir(), spec.fileName + ".part")

        setProgress(DownloadProgress(spec.id, 0f, partial.length(), spec.sizeBytes))

        var lastError: Throwable? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            currentCoroutineContext().ensureActive()
            val outcome = runCatching { fetchInto(spec, url, partial) }
            if (outcome.isSuccess) {
                lastError = null
                break
            }
            lastError = outcome.exceptionOrNull()
            if (lastError is kotlinx.coroutines.CancellationException) throw lastError
            Log.w(TAG, "Download attempt ${attempt + 1} failed, resuming", lastError)
            // Grows with each try, so a genuinely dead connection is not hammered.
            kotlinx.coroutines.delay(RETRY_DELAY_MS * (attempt + 1))
        }

        if (lastError != null) {
            notifications.postDownloadFinished(spec.displayName, success = false)
            setProgress(
                DownloadProgress(
                    spec.id,
                    0f,
                    partial.length(),
                    spec.sizeBytes,
                    lastError.message ?: "Download failed",
                )
            )
            return@withContext Result.failure(lastError)
        }

        runCatching {
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) error("Could not finish writing the model file")

            val finalFile = if (spec.role == ModelRole.SPEECH && target.extension == "zip") {
                unpackSpeechModel(target)
            } else {
                target
            }
            clearProgress(spec.id)
            notifications.postDownloadFinished(spec.displayName, success = true)
            finalFile
        }.onFailure { error ->
            notifications.postDownloadFinished(spec.displayName, success = false)
            setProgress(
                DownloadProgress(spec.id, 0f, 0L, spec.sizeBytes, error.message ?: "Download failed")
            )
        }
    }

    /** One attempt, continuing from whatever is already in the part file. */
    private suspend fun fetchInto(spec: ModelSpec, url: String, partial: File) {
        val alreadyHave = partial.length()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MarkMaaktAI")
            .apply { if (alreadyHave > 0) header("Range", "bytes=$alreadyHave-") }
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download failed with status ${response.code}")
            val body = response.body ?: error("Empty download")

            // 206 means the server honoured the range, so keep what is on disk. A 200
            // means it sent the whole file again, and appending would corrupt it.
            val resuming = response.code == 206 && alreadyHave > 0
            val startAt = if (resuming) alreadyHave else 0L
            val total = body.contentLength().takeIf { it > 0 }?.plus(startAt) ?: spec.sizeBytes

            java.io.FileOutputStream(partial, resuming).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(COPY_BUFFER)
                    var copied = startAt
                    var lastReport = startAt
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        // Reporting every chunk on a multi gigabyte file floods the UI
                        // for nothing, so it is throttled to whole percents.
                        if (total > 0 && copied - lastReport > total / 100) {
                            lastReport = copied
                            val fraction = (copied.toFloat() / total).coerceIn(0f, 1f)
                            setProgress(
                                DownloadProgress(
                                    specId = spec.id,
                                    fraction = fraction,
                                    bytesDone = copied,
                                    bytesTotal = total,
                                )
                            )
                            notifications.postDownloadProgress(
                                title = spec.displayName,
                                percent = (fraction * 100).toInt(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancelDownload(specId: String) {
        jobs.remove(specId)?.cancel()
        clearProgress(specId)
        notifications.cancelDownloadProgress()
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        val prefs = settings.current()
        if (prefs.textModelPath == path) settings.setTextModelPath("")
        if (prefs.visionModelPath == path) settings.setVisionModelPath("")
        if (prefs.speechModelPath == path) settings.setSpeechModelPath("")
        file.deleteRecursively()
    }

    suspend fun activate(model: InstalledModel, role: ModelRole) {
        when (role) {
            ModelRole.TEXT -> settings.setTextModelPath(model.path)
            ModelRole.VISION -> settings.setVisionModelPath(model.path)
            ModelRole.SPEECH -> settings.setSpeechModelPath(model.path)
        }
    }

    /** Vosk ships a zipped directory, and the recogniser wants the unpacked folder. */
    private fun unpackSpeechModel(zip: File): File {
        val destination = File(speechDir(), zip.nameWithoutExtension)
        if (destination.exists()) destination.deleteRecursively()
        destination.mkdirs()

        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val outFile = File(destination, entry.name)
                // Guard against a zip entry that tries to escape the target folder.
                if (!outFile.canonicalPath.startsWith(destination.canonicalPath)) {
                    input.closeEntry()
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output -> input.copyTo(output, COPY_BUFFER) }
                }
                input.closeEntry()
            }
        }
        zip.delete()

        // Vosk archives carry one top level folder; point at that when it is there.
        val single = destination.listFiles()?.singleOrNull()
        return if (single != null && single.isDirectory) single else destination
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun guessRole(fileName: String): ModelRole {
        val lower = fileName.lowercase()
        return when {
            lower.contains("vosk") || lower.contains("whisper") -> ModelRole.SPEECH
            lower.contains("vl") || lower.contains("vision") || lower.contains("3n") -> ModelRole.VISION
            else -> ModelRole.TEXT
        }
    }

    private fun setProgress(progress: DownloadProgress) {
        _downloads.update { it + (progress.specId to progress) }
    }

    private fun clearProgress(specId: String) {
        _downloads.update { it - specId }
    }

    private companion object {
        const val TAG = "ModelRepository"
        const val COPY_BUFFER = 128 * 1024

        /** Enough to ride out a lift, a cell handover or a screen lock. */
        const val MAX_ATTEMPTS = 6
        const val RETRY_DELAY_MS = 1500L

        /** Below this it is a stray file, not a model. */
        const val MIN_MODEL_BYTES = 10L * 1024 * 1024
    }
}
