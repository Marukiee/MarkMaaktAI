package nl.markmaaktmedia.markmaaktai.service.screenshots

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import nl.markmaaktmedia.markmaaktai.data.repository.ScreenshotRepository
import java.util.concurrent.TimeUnit

/**
 * Keeps the screenshot index up to date in the background.
 *
 * Runs a few times a day rather than watching the gallery live. A screenshot is not
 * urgent, and a content observer that wakes the app for every image the camera writes
 * is a battery problem on a phone that takes a lot of photos.
 *
 * OCR runs on every new shot. The model pass only runs while charging, which is the
 * difference between a feature that quietly improves overnight and one that eats a
 * fifth of the battery reading old screenshots.
 */
@HiltWorker
class ScreenshotIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: ScreenshotRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!repository.hasMediaPermission()) return Result.success()

        runCatching { repository.indexNew() }
            .onFailure { return Result.retry() }

        runCatching { repository.pruneMissing() }

        if (inputData.getBoolean(KEY_WITH_MODEL, false)) {
            runCatching { repository.enrichWithModel() }
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "screenshot-index"
        private const val ONE_SHOT_NAME = "screenshot-index-now"
        private const val KEY_WITH_MODEL = "with_model"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScreenshotIndexWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInputData(
                    androidx.work.Data.Builder().putBoolean(KEY_WITH_MODEL, false).build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** The overnight pass that gives the backlog its titles. */
        fun scheduleModelPass(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScreenshotIndexWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInputData(
                    androidx.work.Data.Builder().putBoolean(KEY_WITH_MODEL, true).build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "$PERIODIC_NAME-model",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Fired when the user pulls to refresh, or right after granting access. */
        fun runNow(context: Context, withModel: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<ScreenshotIndexWorker>()
                .setInputData(
                    androidx.work.Data.Builder().putBoolean(KEY_WITH_MODEL, withModel).build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
