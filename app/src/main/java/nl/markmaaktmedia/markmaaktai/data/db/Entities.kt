package nl.markmaaktmedia.markmaaktai.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A chat thread. Threads are cheap, one is created the first time you send something. */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    /**
     * The tip of the branch currently on screen.
     *
     * A thread is a tree once a message can be edited, and every edit adds a sibling
     * rather than replacing what was there. This records which way through the tree
     * the user is looking, so switching between two versions of a question brings back
     * the whole conversation that followed each one.
     */
    val activeLeafId: Long? = null,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("conversationId"), Index("createdAt"), Index("parentId")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    /**
     * The message this one follows, or null for the first in a thread.
     *
     * Two messages sharing a parent are alternative versions of the same turn. That
     * is the whole of the branching model: no separate branch table, no copying of
     * the messages that came before.
     */
    val parentId: Long? = null,
    /** "user", "assistant" or "system". */
    val role: String,
    val content: String,
    val createdAt: Long,
    /** Absolute path of an attached image inside app storage, if any. */
    val imagePath: String? = null,
    /** Web results that were folded into the prompt, stored as JSON. */
    val sources: List<WebSource> = emptyList(),
    val isError: Boolean = false,
)

data class WebSource(
    val title: String,
    val url: String,
    val snippet: String,
)

/**
 * One notification as it came in. Kept verbatim so the model can be asked about it
 * later ("what did Tom send about the planning this afternoon").
 */
@Entity(
    tableName = "captured_notifications",
    indices = [Index("postedAt"), Index("packageName"), Index("clusterKey"), Index("summaryId")],
)
data class CapturedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val body: String,
    /** Package plus conversation or channel, used to group a burst of messages. */
    val clusterKey: String,
    val postedAt: Long,
    val wordCount: Int,
    val category: String,
    /** Set once this notification has been folded into a summary. */
    val summaryId: Long? = null,
    /** True for the collapsed "3 new messages" parent notification, which we skip. */
    val isGroupSummary: Boolean = false,
    val canReply: Boolean = false,
    val systemKey: String? = null,
)

/**
 * Standalone FTS4 index over the text of a notification. Kept next to the typed
 * table instead of using an external content table, so numeric columns keep their
 * affinity and time range queries stay correct.
 */
@Fts4
@Entity(tableName = "notification_fts")
data class NotificationFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val body: String,
    val appLabel: String,
)

@Entity(tableName = "summaries", indices = [Index("createdAt"), Index("isUrgent")])
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val summary: String,
    val isUrgent: Boolean,
    val category: String,
    val actionItems: List<String>,
    val packageName: String,
    val appLabel: String,
    val clusterKey: String,
    val messageCount: Int,
    val isRead: Boolean = false,
)
