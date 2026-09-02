package nl.markmaaktmedia.markmaaktai.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A screenshot that has been read and indexed.
 *
 * The image itself is never copied. Only the recognised text and what the model made
 * of it are stored, and the picture stays in the gallery where the user put it. That
 * keeps the database small and means deleting a screenshot in the gallery does not
 * leave a duplicate behind here.
 */
@Entity(
    tableName = "screenshots",
    indices = [
        Index(value = ["mediaId"], unique = true),
        Index("capturedAt"),
        Index("category"),
    ],
)
data class ScreenshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** MediaStore id, which is what makes a re-scan idempotent. */
    val mediaId: Long,
    val contentUri: String,
    val fileName: String,
    val capturedAt: Long,
    val indexedAt: Long,
    /** Everything OCR could read. The backbone of search. */
    val ocrText: String,
    /** Short label from the model, or the first readable line when there is no model. */
    val title: String,
    val summary: String,
    val category: String,
    /** True once a model has been over it, so a later pass can fill in the rest. */
    val aiProcessed: Boolean = false,
    val isFavourite: Boolean = false,
)

@Fts4
@Entity(tableName = "screenshot_fts")
data class ScreenshotFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val summary: String,
    val ocrText: String,
)

@Dao
interface ScreenshotDao {

    @Query("SELECT * FROM screenshots ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots WHERE category = :category ORDER BY capturedAt DESC")
    fun observeByCategory(category: String): Flow<List<ScreenshotEntity>>

    @Query("SELECT COUNT(*) FROM screenshots")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun byId(id: Long): ScreenshotEntity?

    @Query("SELECT mediaId FROM screenshots")
    suspend fun knownMediaIds(): List<Long>

    @Query("SELECT * FROM screenshots WHERE aiProcessed = 0 ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun awaitingModel(limit: Int): List<ScreenshotEntity>

    @Query(
        "SELECT * FROM screenshots " +
            "WHERE id IN (SELECT rowid FROM screenshot_fts WHERE screenshot_fts MATCH :query) " +
            "ORDER BY capturedAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int): List<ScreenshotEntity>

    /**
     * Every shot the indexer filed under one category.
     *
     * Used when a search names a kind of thing rather than a word in it. Searching
     * "vliegtickets" has to find the boarding pass whose text says gate and PNR and
     * nothing else, and the category is the only place that connection is written
     * down.
     */
    @Query("SELECT * FROM screenshots WHERE category = :category ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun byCategory(category: String, limit: Int): List<ScreenshotEntity>

    @Transaction
    suspend fun insertWithIndex(screenshot: ScreenshotEntity): Long {
        val id = insert(screenshot)
        insertFts(
            ScreenshotFtsEntity(
                rowId = id,
                title = screenshot.title,
                summary = screenshot.summary,
                ocrText = screenshot.ocrText,
            )
        )
        return id
    }

    @Transaction
    suspend fun updateWithIndex(screenshot: ScreenshotEntity) {
        update(screenshot)
        insertFts(
            ScreenshotFtsEntity(
                rowId = screenshot.id,
                title = screenshot.title,
                summary = screenshot.summary,
                ocrText = screenshot.ocrText,
            )
        )
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(screenshot: ScreenshotEntity): Long

    @androidx.room.Update
    suspend fun update(screenshot: ScreenshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(row: ScreenshotFtsEntity)

    @Query("UPDATE screenshots SET isFavourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Transaction
    suspend fun deleteWithIndex(id: Long) {
        deleteFts(id)
        delete(id)
    }

    @Query("DELETE FROM screenshot_fts WHERE rowid = :id")
    suspend fun deleteFts(id: Long)

    @Query("DELETE FROM screenshots WHERE id = :id")
    suspend fun delete(id: Long)

    /** Rows whose picture is gone from the gallery, cleaned up on the next scan. */
    @Query("SELECT * FROM screenshots WHERE mediaId NOT IN (:existingIds)")
    suspend fun orphans(existingIds: List<Long>): List<ScreenshotEntity>
}
