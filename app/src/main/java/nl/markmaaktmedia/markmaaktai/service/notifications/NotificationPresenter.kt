package nl.markmaaktmedia.markmaaktai.service.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import nl.markmaaktmedia.markmaaktai.MainActivity
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.data.db.SummaryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything this app puts in the shade.
 *
 * Three channels, split by how much they are allowed to interrupt. Summaries land
 * silently, because the point of the feature is to make the phone quieter, not to
 * add a second buzz on top of the one that already happened. Only something the
 * model marked urgent gets to make a sound.
 */
@Singleton
class NotificationPresenter @Inject constructor(
    private val context: Context,
) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val summaries = NotificationChannel(
            CHANNEL_SUMMARIES,
            context.getString(R.string.notification_channel_summaries),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_summaries_desc)
            setShowBadge(true)
        }

        val urgent = NotificationChannel(
            CHANNEL_URGENT,
            context.getString(R.string.notification_channel_urgent),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_urgent_desc)
            enableVibration(true)
        }

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.notification_channel_service_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(summaries, urgent, service))
    }

    fun postSummary(summary: SummaryEntity, summaryId: Long, replyKey: String?) {
        val channel = if (summary.isUrgent) CHANNEL_URGENT else CHANNEL_SUMMARIES
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle(summary.appLabel)
            .setContentText(summary.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildBody(summary)))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(if (summary.isUrgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openDigestIntent(summaryId))
            .setGroup(GROUP_SUMMARIES)

        if (replyKey != null && ReplyBridge.get(replyKey) != null) {
            builder.addAction(
                R.drawable.ic_capsule,
                context.getString(R.string.action_reply_draft),
                broadcast(
                    requestCode = summaryId.toInt(),
                    intent = Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_DRAFT_REPLY
                        putExtra(NotificationActionReceiver.EXTRA_SUMMARY_ID, summaryId)
                        putExtra(NotificationActionReceiver.EXTRA_REPLY_KEY, replyKey)
                    },
                ),
            )
        }

        if (summary.isUrgent || summary.category == "calendar" || summary.actionItems.isNotEmpty()) {
            builder.addAction(
                R.drawable.ic_capsule,
                context.getString(R.string.action_add_to_calendar),
                calendarIntent(summary, summaryId),
            )
        }

        notify(summaryId.toInt(), builder.build())
    }

    /** Shows a draft with a send button and a field to change the wording first. */
    fun postReplyDraft(summaryId: Long, appLabel: String, draft: String, replyKey: String) {
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.action_reply_draft))
            .build()

        val sendIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SEND_REPLY
            putExtra(NotificationActionReceiver.EXTRA_SUMMARY_ID, summaryId)
            putExtra(NotificationActionReceiver.EXTRA_REPLY_KEY, replyKey)
            putExtra(NotificationActionReceiver.EXTRA_DRAFT_TEXT, draft)
        }

        val editAction = NotificationCompat.Action.Builder(
            R.drawable.ic_capsule,
            context.getString(R.string.action_reply_draft),
            broadcast(draftRequestCode(summaryId), sendIntent),
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARIES)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle(appLabel)
            .setContentText(draft)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(draft + "\n\n" + context.getString(R.string.notification_reply_ready))
            )
            .setAutoCancel(true)
            .addAction(editAction)
            .setContentIntent(openDigestIntent(summaryId))
            .build()

        notify(draftRequestCode(summaryId), notification)
    }

    /**
     * A quiet, ongoing notification with a progress bar for a model download.
     *
     * A model is well over a gigabyte, which is minutes of waiting that the user is
     * expected to spend somewhere other than this app. Without this the download is
     * invisible the moment the screen is left, and the only way to know whether it is
     * still going is to come back and look.
     *
     * On the lowest importance channel and marked silent: it is a progress readout,
     * not an event, and it should never make a sound or push anything else down.
     */
    fun postDownloadProgress(title: String, percent: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.models_downloading, percent))
            .setProgress(100, percent.coerceIn(0, 100), false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .build()
        notify(DOWNLOAD_ID, notification)
    }

    /** Replaces the progress bar with a one line result, then lets it time out. */
    fun postDownloadFinished(title: String, success: Boolean) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle(title)
            .setContentText(
                context.getString(
                    if (success) R.string.models_download_done else R.string.models_download_failed
                )
            )
            .setAutoCancel(true)
            .setSilent(true)
            .setTimeoutAfter(TRANSIENT_TIMEOUT_MS)
            .setContentIntent(openAppIntent())
            .build()
        notify(DOWNLOAD_ID, notification)
    }

    fun cancelDownloadProgress() = manager.cancel(DOWNLOAD_ID)

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun postTransient(message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentText(message)
            .setTimeoutAfter(TRANSIENT_TIMEOUT_MS)
            .setAutoCancel(true)
            .build()
        notify(TRANSIENT_ID, notification)
    }

    /** The notification a foreground worker has to show while the model is running. */
    fun workingNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle(context.getString(R.string.notification_working))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun cancel(id: Int) = manager.cancel(id)

    private fun buildBody(summary: SummaryEntity): String = buildString {
        append(summary.summary)
        if (summary.actionItems.isNotEmpty()) {
            append("\n\n")
            append(context.getString(R.string.digest_action_items))
            summary.actionItems.forEach { append("\n- ").append(it) }
        }
    }

    private fun openDigestIntent(summaryId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_SUMMARY_ID, summaryId)
        }
        return PendingIntent.getActivity(
            context,
            summaryId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * An activity PendingIntent rather than a broadcast that starts one: a broadcast
     * receiver is background, and Android 12 and up refuses to let background code
     * launch an activity. Straight from the notification it is allowed.
     */
    private fun calendarIntent(summary: SummaryEntity, summaryId: Long): PendingIntent {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, summary.summary.take(80))
            .putExtra(
                CalendarContract.Events.DESCRIPTION,
                buildString {
                    appendLine(summary.summary)
                    if (summary.actionItems.isNotEmpty()) {
                        appendLine()
                        summary.actionItems.forEach { appendLine("- $it") }
                    }
                }.trim(),
            )
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, summary.createdAt)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return PendingIntent.getActivity(
            context,
            calendarRequestCode(summaryId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcast(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            // Mutable because a RemoteInput reply has to write the typed text into it.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    private fun notify(id: Int, notification: Notification) {
        runCatching { manager.notify(id, notification) }
    }

    companion object {
        const val CHANNEL_SUMMARIES = "summaries"
        const val CHANNEL_URGENT = "urgent"
        const val CHANNEL_SERVICE = "service"
        const val GROUP_SUMMARIES = "nl.markmaaktmedia.markmaaktai.SUMMARIES"

        const val FOREGROUND_ID = 4711
        private const val TRANSIENT_ID = 4712
        private const val DOWNLOAD_ID = 4713
        private const val TRANSIENT_TIMEOUT_MS = 6_000L

        fun draftRequestCode(summaryId: Long): Int = (summaryId.toInt() * 31) + 1
        fun calendarRequestCode(summaryId: Long): Int = (summaryId.toInt() * 31) + 2
    }
}
