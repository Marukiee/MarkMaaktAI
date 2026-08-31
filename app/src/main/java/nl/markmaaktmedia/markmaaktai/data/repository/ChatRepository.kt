package nl.markmaaktmedia.markmaaktai.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.markmaaktai.data.db.ConversationDao
import nl.markmaaktmedia.markmaaktai.data.db.ConversationEntity
import nl.markmaaktmedia.markmaaktai.data.db.MessageDao
import nl.markmaaktmedia.markmaaktai.data.db.MessageEntity
import nl.markmaaktmedia.markmaaktai.data.db.WebSource
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {

    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()

    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.observeForConversation(conversationId)

    suspend fun history(conversationId: Long): List<MessageEntity> =
        messageDao.forConversation(conversationId)

    suspend fun createConversation(title: String = DEFAULT_TITLE): Long {
        val now = System.currentTimeMillis()
        return conversationDao.insert(
            ConversationEntity(title = title, createdAt = now, updatedAt = now)
        )
    }

    suspend fun conversation(id: Long): ConversationEntity? = conversationDao.byId(id)

    suspend fun rename(conversationId: Long, title: String) {
        conversationDao.rename(conversationId, title, System.currentTimeMillis())
    }

    suspend fun deleteConversation(conversationId: Long) = conversationDao.delete(conversationId)

    suspend fun addUserMessage(conversationId: Long, text: String, imagePath: String?): Long {
        val now = System.currentTimeMillis()
        val id = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                role = ROLE_USER,
                content = text,
                createdAt = now,
                imagePath = imagePath,
            )
        )
        conversationDao.touch(conversationId, now)
        return id
    }

    /**
     * Creates the empty assistant row up front so tokens can be streamed into a
     * message that is already on screen, rather than the bubble appearing only once
     * the answer is finished.
     */
    suspend fun startAssistantMessage(conversationId: Long): Long = messageDao.insert(
        MessageEntity(
            conversationId = conversationId,
            role = ROLE_ASSISTANT,
            content = "",
            createdAt = System.currentTimeMillis(),
        )
    )

    suspend fun updateAssistantMessage(messageId: Long, content: String) {
        messageDao.updateContent(messageId, content)
    }

    suspend fun finishAssistantMessage(
        message: MessageEntity,
        content: String,
        sources: List<WebSource>,
        isError: Boolean = false,
    ) {
        messageDao.update(message.copy(content = content, sources = sources, isError = isError))
        conversationDao.touch(message.conversationId, System.currentTimeMillis())
    }

    suspend fun messageById(conversationId: Long, messageId: Long): MessageEntity? =
        messageDao.forConversation(conversationId).firstOrNull { it.id == messageId }

    suspend fun deleteMessage(messageId: Long) = messageDao.delete(messageId)

    suspend fun clear(conversationId: Long) = messageDao.clearConversation(conversationId)

    /**
     * Copies an attachment into app storage and scales it down on the way in.
     *
     * A 12 megapixel photo is both far more than a vision model can take and a slow
     * thing to decode twice, so it is resized once here and every later read is
     * cheap. Orientation is applied from EXIF, because a sideways photo confuses a
     * small model far more than a low resolution one does.
     */
    suspend fun saveAttachment(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.filesDir, "attachments").apply { mkdirs() }
            val target = File(directory, "img-${System.currentTimeMillis()}.jpg")

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight)

            val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            } ?: return@runCatching null

            val rotation = context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).rotationDegrees
            } ?: 0

            val oriented = if (rotation != 0) {
                Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postRotate(rotation.toFloat()) },
                    true,
                ).also { if (it != decoded) decoded.recycle() }
            } else {
                decoded
            }

            target.outputStream().use { output ->
                oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            oriented.recycle()
            target.absolutePath
        }.getOrNull()
    }

    suspend fun loadAttachment(path: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / sample > MAX_EDGE || height / sample > MAX_EDGE) sample *= 2
        return sample
    }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        private const val DEFAULT_TITLE = "New conversation"
        private const val MAX_EDGE = 1280
        private const val JPEG_QUALITY = 88
    }
}
