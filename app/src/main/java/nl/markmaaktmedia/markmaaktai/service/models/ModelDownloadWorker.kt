package nl.markmaaktmedia.markmaaktai.service.models

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import nl.markmaaktmedia.markmaaktai.ai.ModelCatalog
import nl.markmaaktmedia.markmaaktai.data.repository.InstalledModel
import nl.markmaaktmedia.markmaaktai.data.repository.ModelRepository
import nl.markmaaktmedia.markmaaktai.service.notifications.NotificationPresenter
import java.util.concurrent.TimeUnit

/**
 * Downloads a model as scheduled work rather than as a coroutine the app owns.
 *
 * The reason is the failure this replaces: "Unable to resolve host huggingface.co".
 * That is not a broken URL, it is a phone with no working connection at the moment
 * the transfer started, which happens constantly on mobile data and after a screen
 * lock. A plain retry loop cannot fix it, because retrying DNS a second later on a
 * phone that is still offline fails the same way.
 *
 * WorkManager can. It holds the job until the network constraint is genuinely
 * satisfied, survives the app being killed, and backs off over minutes rather than
 * seconds. Running in the foreground with the progress notification keeps the system
 * from stopping it half way through a gigabyte.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: ModelRepository,
    private val presenter: NotificationPresenter,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val spec = spec()
        presenter.postDownloadProgress(spec?.displayName.orEmpty(), 0)
        val notification = presenter.downloadNotification(spec?.displayName.orEmpty(), 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationPresenter.DOWNLOAD_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NotificationPresenter.DOWNLOAD_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val spec = spec() ?: return Result.success()

        runCatching { setForeground(getForegroundInfo()) }

        val outcome = repository.download(spec)
        val file = outcome.getOrElse {
            // Let WorkManager wait for a better moment rather than telling the user
            // the download failed when the phone was simply offline.
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }

        repository.activate(
            InstalledModel(
                fileName = file.name,
                path = file.absolutePath,
                sizeBytes = file.length(),
                role = spec.role,
                isActive = true,
            ),
            spec.role,
        )
        return Result.success()
    }

    private fun spec() = inputData.getString(KEY_SPEC_ID)?.let(ModelCatalog::byId)

    companion object {
        private const val KEY_SPEC_ID = "spec_id"
        private const val NAME_PREFIX = "model-download-"
        private const val MAX_ATTEMPTS = 8

        fun enqueue(context: Context, specId: String) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(Data.Builder().putString(KEY_SPEC_ID, specId).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME_PREFIX + specId,
                // KEEP, not REPLACE: pressing download twice should not restart a
                // transfer that is already most of the way through.
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context, specId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME_PREFIX + specId)
        }

        const val TAG = "model-download"
    }
}
