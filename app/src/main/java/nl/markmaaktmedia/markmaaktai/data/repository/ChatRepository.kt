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

    /**
     * The branch currently on screen, oldest first.
     *
     * The table holds every version of every turn, so a thread is a tree. What the
     * user sees is one path through it: start at the conversation's active leaf and
     * walk up the parent links. With no leaf recorded, the newest message is the tip,
     * which is what a conversation that has never been branched looks like anyway.
     */
    fun observeThread(conversationId: Long): Flow<List<MessageEntity>> =
        kotlinx.coroutines.flow.combine(
            messageDao.observeForConversation(conversationId),
            conversationDao.observeById(conversationId),
        ) { all, conversation ->
            pathTo(all, conversation?.activeLeafId)
        }

    /** Every version of the turn this message belongs to, oldest first. */
    suspend fun variantsOf(message: MessageEntity): List<MessageEntity> =
        messageDao.forConversation(message.conversationId)
            .filter { it.parentId == message.parentId && it.role == message.role }
            .sortedBy { it.id }

    /**
     * Moves the view onto another version of a turn.
     *
     * The leaf is set to the deepest message under the chosen sibling, so switching
     * back to an older question restores the whole exchange that followed it rather
     * than truncating the thread at the switch.
     */
    suspend fun switchToVariant(variant: MessageEntity) {
        val all = messageDao.forConversation(variant.conversationId)
        conversationDao.setActiveLeaf(variant.conversationId, deepestUnder(all, variant.id))
    }

    private fun pathTo(all: List<MessageEntity>, leafId: Long?) = MessageTree.pathTo(all, leafId)

    private fun deepestUnder(all: List<MessageEntity>, startId: Long) =
        MessageTree.deepestUnder(all, startId)

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

    /** Pinned threads sort to the top and stay there regardless of when they were used. */
    suspend fun setPinned(conversationId: Long, pinned: Boolean) =
        conversationDao.setPinned(conversationId, pinned)

    suspend fun addUserMessage(
        conversationId: Long,
        text: String,
        imagePath: String?,
        parentId: Long?,
    ): Long {
        val now = System.currentTimeMillis()
        val id = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                parentId = parentId,
                role = ROLE_USER,
                content = text,
                createdAt = now,
                imagePath = imagePath,
            )
        )
        conversationDao.setActiveLeaf(conversationId, id)
        conversationDao.touch(conversationId, now)
        return id
    }

    /** The tip of the branch on screen, which is what a new message hangs off. */
    suspend fun currentLeafId(conversationId: Long): Long? {
        val conversation = conversationDao.byId(conversationId) ?: return null
        conversation.activeLeafId?.let { return it }
        return messageDao.forConversation(conversationId).maxByOrNull { it.id }?.id
    }

    /**
     * Creates the empty assistant row up front so tokens can be streamed into a
     * message that is already on screen, rather than the bubble appearing only once
     * the answer is finished.
     */
    suspend fun startAssistantMessage(conversationId: Long, parentId: Long?): Long {
        val id = messageDao.insert(
            MessageEntity(
                conversationId = conversationId,
                parentId = parentId,
                role = ROLE_ASSISTANT,
                content = "",
                createdAt = System.currentTimeMillis(),
            )
        )
        conversationDao.setActiveLeaf(conversationId, id)
        return id
    }

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

    /** History along the branch, for the prompt. Excludes the turn being answered. */
    suspend fun historyBefore(conversationId: Long, messageId: Long): List<MessageEntity> {
        val all = messageDao.forConversation(conversationId)
        return pathTo(all, messageId).dropLast(1)
    }

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

/**
 * The tree walking behind branched conversations.
 *
 * Pulled out of the repository because it is the part that can be wrong in ways that
 * are invisible until a thread quietly loses half of itself, and it is pure, so it can
 * be tested without a database.
 */
internal object MessageTree {

    /** The chain from the first message down to [leafId], oldest first. */
    fun pathTo(all: List<MessageEntity>, leafId: Long?): List<MessageEntity> {
        if (all.isEmpty()) return emptyList()
        val byId = all.associateBy { it.id }
        val leaf = leafId?.let { byId[it] } ?: all.maxByOrNull { it.id } ?: return emptyList()

        val path = ArrayDeque<MessageEntity>()
        var current: MessageEntity? = leaf
        // Bounded rather than trusting the data. A cycle cannot be written by this
        // code, but if one ever were, an unbounded walk would hang the UI thread.
        var steps = 0
        while (current != null && steps++ <= all.size) {
            path.addFirst(current)
            current = current.parentId?.let { byId[it] }
        }
        return path.toList()
    }

    /** The newest message reachable by always following the latest child. */
    fun deepestUnder(all: List<MessageEntity>, startId: Long): Long {
        var current = startId
        var steps = 0
        while (steps++ <= all.size) {
            val child = all.filter { it.parentId == current }.maxByOrNull { it.id } ?: return current
            current = child.id
        }
        return current
    }
}
