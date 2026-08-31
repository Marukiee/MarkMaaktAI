package nl.markmaaktmedia.markmaaktai

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import nl.markmaaktmedia.markmaaktai.service.notifications.NotificationPresenter
import nl.markmaaktmedia.markmaaktai.service.screenshots.ScreenshotIndexWorker
import javax.inject.Inject

@HiltAndroidApp
class MarkMaaktAiApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var notificationPresenter: NotificationPresenter

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationPresenter.ensureChannels()
        ScreenshotIndexWorker.schedulePeriodic(this)
        ScreenshotIndexWorker.scheduleModelPass(this)
    }
}
