package nl.markmaaktmedia.markmaaktai.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nl.markmaaktmedia.markmaaktai.ai.InferenceEngine
import nl.markmaaktmedia.markmaaktai.ai.engine.LiteRtInferenceEngine
import nl.markmaaktmedia.markmaaktai.ai.engine.LlamaCppInferenceEngine
import nl.markmaaktmedia.markmaaktai.data.db.ConversationDao
import nl.markmaaktmedia.markmaaktai.data.db.MarkDatabase
import nl.markmaaktmedia.markmaaktai.data.db.MessageDao
import nl.markmaaktmedia.markmaaktai.data.db.NotificationDao
import nl.markmaaktmedia.markmaaktai.data.db.ScreenshotDao
import nl.markmaaktmedia.markmaaktai.data.db.SummaryDao
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Handed out unqualified so repositories can take a plain Context in their
     * constructor. They are all singletons, so this is always the application one.
     */
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    /**
     * One client for the three things that touch the network: search, model
     * downloads and the update check. Read timeouts are generous because a model is
     * a multi gigabyte file on whatever connection the phone happens to have.
     */
    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * The runtime the app talks to. LiteRT today, with the llama.cpp engine built
     * alongside it so the swap is this one line once the native side ships.
     */
    @Provides
    @Singleton
    fun provideInferenceEngine(@ApplicationContext context: Context): InferenceEngine {
        val llamaCpp = LlamaCppInferenceEngine()
        return if (llamaCpp.isAvailable()) llamaCpp else LiteRtInferenceEngine(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MarkDatabase =
        androidx.room.Room
            .databaseBuilder(context, MarkDatabase::class.java, MarkDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideConversationDao(db: MarkDatabase): ConversationDao = db.conversationDao()

    @Provides fun provideMessageDao(db: MarkDatabase): MessageDao = db.messageDao()

    @Provides fun provideNotificationDao(db: MarkDatabase): NotificationDao = db.notificationDao()

    @Provides fun provideSummaryDao(db: MarkDatabase): SummaryDao = db.summaryDao()

    @Provides fun provideScreenshotDao(db: MarkDatabase): ScreenshotDao = db.screenshotDao()
}
