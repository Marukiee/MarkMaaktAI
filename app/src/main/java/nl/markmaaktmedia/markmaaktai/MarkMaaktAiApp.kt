package nl.markmaaktmedia.markmaaktai

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import nl.markmaaktmedia.markmaaktai.service.notifications.NotificationPresenter
import nl.markmaaktmedia.markmaaktai.service.screenshots.ScreenshotIndexWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.data.repository.ModelRepository
import nl.markmaaktmedia.markmaaktai.util.CrashReporter
import javax.inject.Inject

@HiltAndroidApp
class MarkMaaktAiApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var notificationPresenter: NotificationPresenter

    @Inject lateinit var modelRepository: ModelRepository

    @Inject lateinit var crashReporter: CrashReporter

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
        notificationPresenter.ensureChannels()
        ScreenshotIndexWorker.schedulePeriodic(this)
        ScreenshotIndexWorker.scheduleModelPass(this)

        // A model file can be present without being selected, after a restore or a
        // file copied in by hand. Looking here means the app never claims to have no
        // model while one is sitting in its own storage.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { modelRepository.adoptExistingModels() }
        }
    }
}
