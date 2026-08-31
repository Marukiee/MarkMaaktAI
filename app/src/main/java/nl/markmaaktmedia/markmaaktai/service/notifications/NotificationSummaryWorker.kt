package nl.markmaaktmedia.markmaaktai.service.notifications

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import nl.markmaaktmedia.markmaaktai.ai.AiOrchestrator
import nl.markmaaktmedia.markmaaktai.data.db.SummaryEntity
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import nl.markmaaktmedia.markmaaktai.data.repository.NotificationRepository
import java.util.concurrent.TimeUnit

/**
 * Turns one cluster of notifications into one summary.
 *
 * Scheduled with a delay and a unique name per cluster, replacing whatever was
 * already queued for it. That is the debounce: while a conversation is still going,
 * each new message pushes the work back, and the model runs once at the end instead
 * of once per message.
 */
@HiltWorker
class NotificationSummaryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: NotificationRepository,
    private val orchestrator: AiOrchestrator,
    private val presenter: NotificationPresenter,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = presenter.workingNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationPresenter.FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NotificationPresenter.FOREGROUND_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val clusterKey = inputData.getString(KEY_CLUSTER) ?: return Result.success()
        val replyKey = inputData.getString(KEY_REPLY)

        val prefs = settings.current()
        if (!prefs.notificationIntelligence) return Result.success()

        val pending = repository.pendingForCluster(clusterKey)
        if (pending.isEmpty()) return Result.success()

        // The trigger may have fired on a burst that has since been summarised, or on
        // a single long mail. Both are fine, an empty cluster is not worth a model load.
        val worthSummarising = pending.size >= prefs.clusterSize ||
            pending.any { it.wordCount >= prefs.longEmailWordCount }
        if (!worthSummarising) return Result.success()

        runCatching { setForeground(getForegroundInfo()) }

        val appLabel = pending.first().appLabel
        val lines = pending.map { entity ->
            val who = entity.title.ifBlank { entity.appLabel }
            "$who: ${entity.body.take(400)}"
        }

        val outcome = orchestrator.summarise(appLabel, lines)
        val structured = outcome.getOrElse {
            // No model yet, or it failed to load. Retrying in a loop would only drain
            // the battery, so the cluster is left pending for the next trigger.
            return Result.success()
        }
        if (structured.summary.isBlank()) return Result.success()

        val entity = SummaryEntity(
            createdAt = System.currentTimeMillis(),
            summary = structured.summary,
            isUrgent = structured.isUrgent && prefs.urgentAlerts,
            category = structured.category,
            actionItems = structured.actionItems,
            packageName = pending.first().packageName,
            appLabel = appLabel,
            clusterKey = clusterKey,
            messageCount = pending.size,
        )

        val id = repository.saveSummary(entity, pending.map { it.id })
        presenter.postSummary(entity.copy(id = id), id, replyKey)
        repository.purgeOld()
        return Result.success()
    }

    companion object {
        private const val NAME_PREFIX = "summary-"
        private const val KEY_CLUSTER = "cluster_key"
        private const val KEY_REPLY = "reply_key"

        /**
         * Long enough that a person typing three messages in a row produces one
         * summary, short enough that the result still feels like it belongs to what
         * just happened.
         */
        private const val DEBOUNCE_SECONDS = 25L

        fun schedule(context: Context, clusterKey: String, replyKey: String?) {
            val request = OneTimeWorkRequestBuilder<NotificationSummaryWorker>()
                .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_CLUSTER, clusterKey)
                        .putString(KEY_REPLY, replyKey)
                        .build()
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME_PREFIX + clusterKey,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        const val TAG = "notification-summary"
    }
}
