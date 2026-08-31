package nl.markmaaktmedia.markmaaktai.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import nl.markmaaktmedia.markmaaktai.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks the GitHub releases feed and, when the user asks for it, downloads the APK
 * and hands it to the system installer.
 *
 * There is no update service and no background polling: the check runs when the app
 * starts and when the settings button is pressed, at most once a day, so an offline
 * phone never spends anything on this.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: Flow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = VersionComparator.normalise(BuildConfig.VERSION_NAME)

    val releasesPageUrl: String
        get() = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases"

    fun reset() {
        _state.value = UpdateState.Idle
    }

    /** Returns the release when there is a newer one, null otherwise. */
    suspend fun check(): ReleaseInfo? {
        _state.value = UpdateState.Checking
        val release = fetchLatest()
        if (release == null) {
            _state.value = UpdateState.Failed(FAILED_REASON)
            return null
        }
        return if (VersionComparator.isNewer(release.versionName, currentVersion)) {
            _state.value = UpdateState.Available(release)
            release
        } else {
            _state.value = UpdateState.UpToDate
            null
        }
    }

    private suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "MarkMaaktAI/${BuildConfig.VERSION_NAME}")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (tag.isBlank()) return@use null

                val assets = root["assets"]?.jsonArray.orEmpty()
                val apk = assets.map { it.jsonObject }.firstOrNull { asset ->
                    asset["name"]?.jsonPrimitive?.contentOrNull.orEmpty().endsWith(".apk", ignoreCase = true)
                }

                ReleaseInfo(
                    tag = tag,
                    versionName = VersionComparator.normalise(tag),
                    title = root["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: tag,
                    changelog = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
                    apkUrl = apk?.get("browser_download_url")?.jsonPrimitive?.contentOrNull,
                    apkSizeBytes = apk?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
                    htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull ?: releasesPageUrl,
                    publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.getOrElse { error ->
            Log.w(TAG, "Could not read the releases feed", error)
            null
        }
    }

    /** Streams the APK into the cache directory, reporting progress as it goes. */
    suspend fun download(release: ReleaseInfo): File? {
        val url = release.apkUrl ?: run {
            _state.value = UpdateState.Failed("That release has no APK attached")
            return null
        }
        _state.value = UpdateState.Downloading(release, 0f)

        return withContext(Dispatchers.IO) {
            val target = File(updatesDir(), "MarkMaaktAI-${release.versionName}.apk")
            runCatching {
                val request = Request.Builder().url(url).header("User-Agent", "MarkMaaktAI").build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed with status ${response.code}")
                    val body = response.body ?: error("Empty download")
                    val total = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes
                    target.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER)
                            var copied = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (total > 0) {
                                    _state.value = UpdateState.Downloading(
                                        release,
                                        (copied.toFloat() / total).coerceIn(0f, 1f),
                                    )
                                }
                            }
                        }
                    }
                }
                _state.value = UpdateState.ReadyToInstall(release, target.absolutePath)
                target
            }.getOrElse { error ->
                target.delete()
                _state.value = UpdateState.Failed(error.message ?: FAILED_REASON)
                null
            }
        }
    }

    /**
     * Hands the downloaded file to the package installer. Android 8 and up asks the
     * user to allow installs from this app first, which is why the permission screen
     * is opened instead of failing silently when it has not been granted.
     */
    fun install(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            _state.value = UpdateState.Failed("The downloaded file is gone")
            return
        }
        if (!canRequestInstalls()) {
            openInstallPermissionSettings()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { _state.value = UpdateState.Failed("No installer available on this phone") }
    }

    fun canRequestInstalls(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Old downloads are dead weight once a newer one has been installed. */
    fun cleanUpOldDownloads(keepFileName: String? = null) {
        runCatching {
            updatesDir().listFiles()?.forEach { file ->
                if (file.name != keepFileName) file.delete()
            }
        }
    }

    private fun updatesDir(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    private companion object {
        const val TAG = "UpdateRepository"
        const val DOWNLOAD_BUFFER = 64 * 1024
        const val FAILED_REASON = "Could not reach GitHub"
    }
}
