package nl.markmaaktmedia.markmaaktai.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.markmaaktai.ai.AiOrchestrator
import nl.markmaaktmedia.markmaaktai.ai.vision.ImageTextExtractor
import nl.markmaaktmedia.markmaaktai.data.db.ScreenshotDao
import nl.markmaaktmedia.markmaaktai.data.db.ScreenshotEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Counters shown while a scan is running. */
data class IndexProgress(val done: Int, val total: Int) {
    val isRunning: Boolean get() = total > 0 && done < total
}

/**
 * Makes every screenshot on the phone searchable by what is written in it.
 *
 * The work is split in two on purpose. OCR is cheap and runs on everything, which is
 * what makes search work at all and needs no model to be installed. The model pass
 * comes second and only writes a short title and one line of description, so a phone
 * with no model still gets the useful half of the feature.
 *
 * Nothing is uploaded and nothing is copied. The recognised text lives in the app
 * database and the pictures stay in the gallery.
 */
@Singleton
class ScreenshotRepository @Inject constructor(
    private val context: Context,
    private val dao: ScreenshotDao,
    private val textExtractor: ImageTextExtractor,
    private val orchestrator: AiOrchestrator,
) {

    fun observeAll(): Flow<List<ScreenshotEntity>> = dao.observeAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun byId(id: Long): ScreenshotEntity? = dao.byId(id)

    suspend fun setFavourite(id: Long, favourite: Boolean) = dao.setFavourite(id, favourite)

    suspend fun forget(id: Long) = dao.deleteWithIndex(id)

    fun hasMediaPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Reads the newest screenshots that are not indexed yet.
     *
     * Newest first and capped, because the first run on a phone with years of
     * screenshots would otherwise sit there for an hour. The rest is picked up by the
     * next scan, so the backlog drains over a few runs instead of in one long stall.
     */
    suspend fun indexNew(
        limit: Int = DEFAULT_BATCH,
        onProgress: (IndexProgress) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        if (!hasMediaPermission()) return@withContext 0

        val known = dao.knownMediaIds().toHashSet()
        val candidates = queryScreenshots().filter { it.mediaId !in known }.take(limit)
        if (candidates.isEmpty()) return@withContext 0

        var done = 0
        onProgress(IndexProgress(0, candidates.size))
        candidates.forEach { item ->
            runCatching { index(item) }
                .onFailure { Log.w(TAG, "Could not index ${item.fileName}", it) }
            done++
            onProgress(IndexProgress(done, candidates.size))
        }
        onProgress(IndexProgress(candidates.size, candidates.size))
        done
    }

    /** Second pass: gives the already indexed shots a title and a one line summary. */
    suspend fun enrichWithModel(limit: Int = MODEL_BATCH): Int = withContext(Dispatchers.IO) {
        val pending = dao.awaitingModel(limit)
        if (pending.isEmpty()) return@withContext 0

        var enriched = 0
        pending.forEach { shot ->
            if (shot.ocrText.isBlank()) {
                dao.updateWithIndex(shot.copy(aiProcessed = true))
                return@forEach
            }
            val prompt = buildString {
                appendLine("Below is the text read from a screenshot on a phone.")
                appendLine("Give it a short name, at most five words.")
                appendLine("Reply with the name only. No quotes, no label, no full stop.")
                appendLine()
                appendLine(shot.ocrText.take(MAX_OCR_FOR_MODEL))
                append("Name:")
            }
            val answer = orchestrator.complete(prompt, maxTokens = 32).getOrNull()
            if (answer.isNullOrBlank()) return@withContext enriched

            val title = cleanTitle(answer)

            dao.updateWithIndex(
                shot.copy(
                    title = title.ifBlank { shot.title },
                    aiProcessed = true,
                )
            )
            enriched++
        }
        enriched
    }

    /**
     * Turns whatever the model said into something that fits under a thumbnail.
     *
     * Small models answer the letter of a prompt rather than its intent: asked for two
     * lines they reply "Line 1: '...'", and they will hand back an escaped newline as
     * four characters rather than a break. Everything below the first real line is
     * dropped, along with the labels and quoting they wrap it in.
     */
    private fun cleanTitle(raw: String): String = raw
        .replace("\\n", "\n")
        .lines()
        .map { line ->
            line.trim()
                .removePrefix("Name:")
                .removePrefix("Title:")
                .replace(LINE_LABEL, "")
                .trim()
                .trim('"', '\'', '*', '-', ':', '.', ' ')
        }
        .firstOrNull { it.length >= 3 }
        .orEmpty()
        .take(48)

    /** Drops rows whose picture the user deleted from the gallery. */
    suspend fun pruneMissing(): Int = withContext(Dispatchers.IO) {
        if (!hasMediaPermission()) return@withContext 0
        val existing = queryScreenshots().map { it.mediaId }
        if (existing.isEmpty()) return@withContext 0
        val gone = dao.orphans(existing)
        gone.forEach { dao.deleteWithIndex(it.id) }
        gone.size
    }

    suspend fun search(query: String, limit: Int = 60): List<ScreenshotEntity> {
        val match = toMatchQuery(query) ?: return emptyList()
        return runCatching { dao.search(match, limit) }.getOrDefault(emptyList())
    }

    /** Lines handed to the model when a chat question is about screenshots. */
    suspend fun contextFor(question: String, limit: Int = 8): List<String> =
        search(question, limit).map { shot ->
            val when0 = dateFormat.format(Date(shot.capturedAt))
            "$when0, ${shot.title.ifBlank { shot.fileName }}: ${shot.ocrText.take(400)}"
        }

    suspend fun loadThumbnail(entity: ScreenshotEntity, maxEdge: Int = THUMB_EDGE): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching { decode(Uri.parse(entity.contentUri), maxEdge) }.getOrNull()
        }

    private suspend fun index(item: MediaItem) {
        val bitmap = decode(item.uri, OCR_EDGE) ?: return
        val text = runCatching { textExtractor.extractStructured(bitmap) }.getOrDefault("")
        bitmap.recycle()

        val fallbackTitle = text.lines()
            .firstOrNull { it.trim().length in TITLE_RANGE }
            ?.trim()
            ?.take(60)
            ?: item.fileName.substringBeforeLast('.')

        dao.insertWithIndex(
            ScreenshotEntity(
                mediaId = item.mediaId,
                contentUri = item.uri.toString(),
                fileName = item.fileName,
                capturedAt = item.capturedAt,
                indexedAt = System.currentTimeMillis(),
                ocrText = text,
                title = fallbackTitle,
                summary = "",
                category = guessCategory(text),
                aiProcessed = false,
            )
        )
    }

    private data class MediaItem(
        val mediaId: Long,
        val uri: Uri,
        val fileName: String,
        val capturedAt: Long,
    )

    /**
     * Screenshots only, not the whole camera roll. Android 10 and up records the
     * folder in RELATIVE_PATH, so that is the primary filter, with the bucket name as
     * a fallback for anything filed differently.
     */
    private fun queryScreenshots(): List<MediaItem> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR " +
            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%Screenshots%", "%Screenshot%")
        val order = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val items = mutableListOf<MediaItem>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, args, order)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    // DATE_TAKEN is already in milliseconds, DATE_ADDED is in seconds.
                    val taken = cursor.getLong(takenColumn)
                    val added = cursor.getLong(addedColumn) * 1000L
                    items += MediaItem(
                        mediaId = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        fileName = cursor.getString(nameColumn) ?: "screenshot-$id",
                        capturedAt = if (taken > 0) taken else added,
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Reading the gallery failed", it) }
        return items
    }

    private fun decode(uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) sample *= 2

        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }
    }

    /** Keyword buckets, good enough for the filter row and cheap enough to run on OCR. */
    private fun guessCategory(text: String): String {
        val lower = text.lowercase()
        return when {
            listOf("iban", "eur", "betaal", "factuur", "invoice", "total", "bedrag", "tikkie")
                .any { it in lower } -> "finance"
            listOf("boarding", "gate", "vlucht", "flight", "ticket", "reservering", "booking")
                .any { it in lower } -> "travel"
            listOf("track", "bezorg", "delivery", "pakket", "parcel").any { it in lower } -> "delivery"
            listOf("recept", "recipe", "ingredi", "oven", "bereiding").any { it in lower } -> "recipe"
            listOf("http", "www.", ".com", ".nl").any { it in lower } -> "web"
            text.isBlank() -> "image"
            else -> "other"
        }
    }

    private fun toMatchQuery(question: String): String? {
        val words = question.lowercase()
            .split(NON_WORD)
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinct()
            .take(8)
        if (words.isEmpty()) return null
        return words.joinToString(" OR ") { "$it*" }
    }

    private companion object {
        const val TAG = "ScreenshotRepository"
        const val DEFAULT_BATCH = 40
        const val MODEL_BATCH = 8
        const val OCR_EDGE = 1600
        const val THUMB_EDGE = 480
        const val MAX_OCR_FOR_MODEL = 1200
        val TITLE_RANGE = 4..60
        val NON_WORD = Regex("[^\\p{L}\\p{Nd}]+")
        val LINE_LABEL = Regex("^Line\\s*\\d+\\s*:", RegexOption.IGNORE_CASE)
        val dateFormat = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
    }
}
