package nl.markmaaktmedia.markmaaktai.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
) {

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

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

    /** Downloads a catalogue entry, reporting progress. Speech models are unzipped. */
    suspend fun download(spec: ModelSpec): Result<File> = withContext(Dispatchers.IO) {
        val url = spec.downloadUrl ?: return@withContext Result.failure(
            IllegalArgumentException("That model has no download link")
        )
        val target = File(modelsDir(), spec.fileName)
        val partial = File(modelsDir(), spec.fileName + ".part")

        setProgress(DownloadProgress(spec.id, 0f, 0L, spec.sizeBytes))
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MarkMaaktAI")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed with status ${response.code}")
                val body = response.body ?: error("Empty download")
                val total = body.contentLength().takeIf { it > 0 } ?: spec.sizeBytes

                partial.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(COPY_BUFFER)
                        var copied = 0L
                        var lastReport = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            // Reporting every chunk on a multi gigabyte file floods
                            // the UI for nothing, so it is throttled to whole percents.
                            if (total > 0 && copied - lastReport > total / 100) {
                                lastReport = copied
                                setProgress(
                                    DownloadProgress(
                                        specId = spec.id,
                                        fraction = (copied.toFloat() / total).coerceIn(0f, 1f),
                                        bytesDone = copied,
                                        bytesTotal = total,
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (!partial.renameTo(target)) error("Could not finish writing the model file")

            val finalFile = if (spec.role == ModelRole.SPEECH && target.extension == "zip") {
                unpackSpeechModel(target)
            } else {
                target
            }
            clearProgress(spec.id)
            finalFile
        }.onFailure { error ->
            partial.delete()
            Log.w(TAG, "Model download failed", error)
            setProgress(
                DownloadProgress(spec.id, 0f, 0L, spec.sizeBytes, error.message ?: "Download failed")
            )
        }
    }

    fun cancelDownload(specId: String) {
        clearProgress(specId)
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
    }
}
