package nl.markmaaktmedia.markmaaktai.service.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.data.db.CapturedNotificationEntity
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import nl.markmaaktmedia.markmaaktai.data.repository.NotificationRepository
import javax.inject.Inject

/**
 * Reads incoming notifications, stores them, and decides when a burst is worth
 * waking the model for.
 *
 * The decision is the whole feature. Running a language model on every notification
 * would drain a battery for no benefit, so almost everything is filtered out before
 * inference is even considered:
 *
 * - anything under the word threshold, because "ok" needs no summary
 * - the collapsed "3 new messages" parent, which would be counted twice
 * - ongoing and foreground service notifications, which are not messages
 * - apps the user put on the skip list, and this app itself
 *
 * What survives is stored and counted. Only a cluster of messages inside a short
 * window, or one genuinely long mail, schedules a summary. That work is scheduled
 * with a delay and replaces its own pending copy, so a conversation that is still
 * going produces one summary at the end rather than one per message.
 */
@AndroidEntryPoint
class MarkNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var repository: NotificationRepository

    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        ReplyBridge.clear()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        scope.launch {
            runCatching { handle(statusBarNotification) }
                .onFailure { Log.w(TAG, "Could not handle a notification", it) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        ReplyBridge.forget(sbn?.key)
    }

    private suspend fun handle(sbn: StatusBarNotification) {
        val prefs = settings.current()
        if (!prefs.notificationIntelligence) return
        if (sbn.packageName == packageName) return
        if (sbn.packageName in prefs.excludedPackages) return

        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        if (isNotAMessage(notification)) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val body = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        ).maxByOrNull { it.length }.orEmpty().trim()

        if (body.isBlank()) return

        val wordCount = body.split(WHITESPACE).count { it.isNotBlank() }
        // A one word reply is not worth a database row either.
        if (wordCount < MIN_STORED_WORDS) return

        val appLabel = appLabel(sbn.packageName)
        val clusterKey = clusterKey(sbn)
        val replyAction = findReplyAction(notification)

        if (replyAction != null) {
            ReplyBridge.remember(
                key = sbn.key,
                reply = ReplyBridge.PendingReply(
                    packageName = sbn.packageName,
                    appLabel = appLabel,
                    pendingIntent = replyAction.actionIntent,
                    remoteInputs = replyAction.remoteInputs ?: emptyArray(),
                    resultKey = replyAction.remoteInputs?.firstOrNull()?.resultKey.orEmpty(),
                ),
            )
        }

        repository.record(
            CapturedNotificationEntity(
                packageName = sbn.packageName,
                appLabel = appLabel,
                title = title,
                body = body,
                clusterKey = clusterKey,
                postedAt = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                wordCount = wordCount,
                category = guessCategory(sbn.packageName, notification),
                canReply = replyAction != null,
                systemKey = sbn.key,
            )
        )

        if (wordCount < prefs.minWordCount) return

        val isLongMail = wordCount >= prefs.longEmailWordCount
        val pending = repository.pendingCountInWindow(clusterKey, prefs.clusterWindowMinutes)
        if (isLongMail || pending >= prefs.clusterSize) {
            NotificationSummaryWorker.schedule(applicationContext, clusterKey, sbn.key)
        }
    }

    /**
     * Filters out everything that is posted as a notification but is not a message.
     *
     * Media players are the loud case: they keep a permanent notification alive with a
     * MediaSession attached, and it changes on every track. Progress notifications,
     * downloads and background services are the same shape of problem. None of them
     * are worth storing, let alone waking a language model for.
     */
    private fun isNotAMessage(notification: Notification): Boolean {
        val extras = notification.extras
        val hasMediaSession = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
        val isProgress = (extras?.getInt(Notification.EXTRA_PROGRESS_MAX) ?: 0) > 0 ||
            extras?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) == true

        return hasMediaSession ||
            isProgress ||
            notification.category in IGNORED_CATEGORIES ||
            notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
    }

    /**
     * Groups by conversation where the app tells us one, and by channel otherwise.
     * Falling back to the package alone would fold three different chats into one
     * summary, which reads as nonsense.
     */
    private fun clusterKey(sbn: StatusBarNotification): String {
        val extras = sbn.notification?.extras
        val conversation = extras?.getString(NotificationCompat.EXTRA_CONVERSATION_TITLE)
            ?: extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sbn.notification?.channelId
        } else {
            null
        }
        val discriminator = sbn.notification?.group
            ?: conversation
            ?: channel
            ?: DEFAULT_CLUSTER
        return "${sbn.packageName}#$discriminator"
    }

    private fun findReplyAction(notification: Notification): Notification.Action? =
        notification.actions?.firstOrNull { action ->
            action.remoteInputs?.any { it.allowFreeFormInput } == true
        }

    private fun appLabel(packageName: String): String = runCatching {
        val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    /**
     * A rough bucket, used for the icon and the filter chips. The model is asked for
     * a category too, but this one exists before any inference has run, and for a
     * notification that never reaches the model it is the only one there is.
     */
    private fun guessCategory(packageName: String, notification: Notification): String {
        val category = notification.category
        val lower = packageName.lowercase()
        return when {
            category == Notification.CATEGORY_EMAIL -> "email"
            category == Notification.CATEGORY_EVENT || category == Notification.CATEGORY_REMINDER -> "calendar"
            category == Notification.CATEGORY_TRANSPORT -> "delivery"
            lower.contains("mail") || lower.contains("outlook") || lower.contains("thunderbird") -> "email"
            lower.contains("calendar") || lower.contains("agenda") -> "calendar"
            lower.contains("bank") || lower.contains("ing") || lower.contains("rabo") ||
                lower.contains("paypal") || lower.contains("tikkie") -> "finance"
            lower.contains("postnl") || lower.contains("dhl") || lower.contains("track") -> "delivery"
            lower.contains("instagram") || lower.contains("mastodon") || lower.contains("reddit") ||
                lower.contains("linkedin") -> "social"
            category == Notification.CATEGORY_MESSAGE -> "message"
            lower.contains("whatsapp") || lower.contains("signal") || lower.contains("telegram") ||
                lower.contains("messenger") -> "message"
            else -> "other"
        }
    }

    private companion object {
        const val TAG = "NotificationListener"
        const val DEFAULT_CLUSTER = "default"

        /** Below this a notification is noise, whatever the user's summary threshold is. */
        const val MIN_STORED_WORDS = 2

        val WHITESPACE = Regex("\\s+")

        /** Categories that are never a message someone sent to the user. */
        val IGNORED_CATEGORIES = setOf(
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_STATUS,
        )
    }
}
