package nl.markmaaktmedia.markmaaktai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byId(id: Long): ConversationEntity?

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE conversations SET activeLeafId = :leafId WHERE id = :id")
    suspend fun setActiveLeaf(id: Long, leafId: Long?)

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: Long): Flow<ConversationEntity?>

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeForConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun forConversation(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: Long)
}

@Dao
interface NotificationDao {

    /** Writes the row and its search index entry in one go, so they cannot drift apart. */
    @Transaction
    suspend fun insertWithIndex(notification: CapturedNotificationEntity): Long {
        val id = insert(notification)
        insertFts(
            NotificationFtsEntity(
                rowId = id,
                title = notification.title,
                body = notification.body,
                appLabel = notification.appLabel,
            )
        )
        return id
    }

    @Insert
    suspend fun insert(notification: CapturedNotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(row: NotificationFtsEntity)

    /**
     * Full text search over everything that came in. The FTS table only holds the text
     * columns, so the typed table keeps correct affinity for postedAt comparisons.
     */
    @Query(
        "SELECT * FROM captured_notifications " +
            "WHERE id IN (SELECT rowid FROM notification_fts WHERE notification_fts MATCH :query) " +
            "ORDER BY postedAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int): List<CapturedNotificationEntity>

    @Query("SELECT * FROM captured_notifications WHERE postedAt >= :since ORDER BY postedAt DESC LIMIT :limit")
    suspend fun since(since: Long, limit: Int): List<CapturedNotificationEntity>

    @Query("SELECT * FROM captured_notifications WHERE clusterKey = :clusterKey AND summaryId IS NULL ORDER BY postedAt ASC")
    suspend fun pendingForCluster(clusterKey: String): List<CapturedNotificationEntity>

    @Query("SELECT COUNT(*) FROM captured_notifications WHERE clusterKey = :clusterKey AND summaryId IS NULL AND postedAt >= :since")
    suspend fun pendingCountSince(clusterKey: String, since: Long): Int

    @Query("SELECT * FROM captured_notifications WHERE summaryId = :summaryId ORDER BY postedAt ASC")
    suspend fun forSummary(summaryId: Long): List<CapturedNotificationEntity>

    @Query("UPDATE captured_notifications SET summaryId = :summaryId WHERE id IN (:ids)")
    suspend fun attachToSummary(ids: List<Long>, summaryId: Long)

    @Query("SELECT DISTINCT packageName FROM captured_notifications ORDER BY packageName")
    fun observeKnownPackages(): Flow<List<String>>

    @Transaction
    suspend fun purgeOlderThan(cutoff: Long) {
        deleteFtsOlderThan(cutoff)
        deleteOlderThan(cutoff)
    }

    @Query("DELETE FROM notification_fts WHERE rowid IN (SELECT id FROM captured_notifications WHERE postedAt < :cutoff)")
    suspend fun deleteFtsOlderThan(cutoff: Long)

    @Query("DELETE FROM captured_notifications WHERE postedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface SummaryDao {

    @Query("SELECT * FROM summaries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SummaryEntity>>

    @Query("SELECT COUNT(*) FROM summaries WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT * FROM summaries WHERE id = :id")
    suspend fun byId(id: Long): SummaryEntity?

    @Query("SELECT * FROM summaries WHERE createdAt >= :since ORDER BY createdAt DESC LIMIT :limit")
    suspend fun since(since: Long, limit: Int): List<SummaryEntity>

    @Insert
    suspend fun insert(summary: SummaryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(summary: SummaryEntity): Long

    @Query("UPDATE summaries SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("DELETE FROM summaries WHERE id = :id")
    suspend fun delete(id: Long)
}
