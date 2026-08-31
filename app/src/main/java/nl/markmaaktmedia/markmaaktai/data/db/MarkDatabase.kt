package nl.markmaaktmedia.markmaaktai.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        CapturedNotificationEntity::class,
        NotificationFtsEntity::class,
        SummaryEntity::class,
        ScreenshotEntity::class,
        ScreenshotFtsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MarkDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun notificationDao(): NotificationDao
    abstract fun summaryDao(): SummaryDao
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        const val NAME = "markmaaktai.db"
    }
}
