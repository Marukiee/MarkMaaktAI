package nl.markmaaktmedia.markmaaktai.service.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ai.AiOrchestrator
import nl.markmaaktmedia.markmaaktai.data.repository.NotificationRepository
import javax.inject.Inject

/**
 * Handles the buttons on a summary notification.
 *
 * Drafting asks the model for one sentence and shows it for approval. Nothing is
 * ever sent without a tap: a wrong reply going out on its own would be far worse
 * than no reply at all, so the draft is a suggestion with a send button and a field
 * to reword it first.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var orchestrator: AiOrchestrator

    @Inject lateinit var repository: NotificationRepository

    @Inject lateinit var presenter: NotificationPresenter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val summaryId = intent.getLongExtra(EXTRA_SUMMARY_ID, -1L)
        val replyKey = intent.getStringExtra(EXTRA_REPLY_KEY)

        when (intent.action) {
            ACTION_DRAFT_REPLY -> {
                if (summaryId < 0 || replyKey == null) return
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        draftReply(context, summaryId, replyKey)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_SEND_REPLY -> {
                val typed = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_REPLY_TEXT)
                    ?.toString()
                val draft = intent.getStringExtra(EXTRA_DRAFT_TEXT).orEmpty()
                val text = typed?.takeIf { it.isNotBlank() } ?: draft
                sendReply(context, summaryId, replyKey, text)
            }
        }
    }

    private suspend fun draftReply(context: Context, summaryId: Long, replyKey: String) {
        val reply = ReplyBridge.get(replyKey) ?: run {
            presenter.postTransient(context.getString(R.string.notification_reply_failed))
            return
        }
        val sources = repository.sourcesFor(summaryId)
        if (sources.isEmpty()) return

        val lines = sources.map { "${it.title.ifBlank { it.appLabel }}: ${it.body.take(300)}" }
        val draft = orchestrator.draftReply(reply.appLabel, lines).getOrNull()

        if (draft.isNullOrBlank()) {
            presenter.postTransient(context.getString(R.string.generic_error))
            return
        }
        presenter.postReplyDraft(summaryId, reply.appLabel, draft, replyKey)
    }

    /**
     * Fires the reply action the source app published, filling in its own result key.
     * The key comes from that app's RemoteInput, so it has to be read back rather
     * than assumed.
     */
    private fun sendReply(context: Context, summaryId: Long, replyKey: String?, text: String) {
        if (text.isBlank()) return
        val reply = ReplyBridge.get(replyKey) ?: run {
            presenter.postTransient(context.getString(R.string.notification_reply_failed))
            return
        }

        val results = Bundle().apply { putCharSequence(reply.resultKey, text) }
        val fillIn = Intent().apply {
            RemoteInput.addResultsToIntent(
                reply.remoteInputs.map { native ->
                    RemoteInput.Builder(native.resultKey)
                        .setLabel(native.label)
                        .setAllowFreeFormInput(native.allowFreeFormInput)
                        .build()
                }.toTypedArray(),
                this,
                results,
            )
        }

        runCatching { reply.pendingIntent.send(context, 0, fillIn) }
            .onSuccess {
                presenter.cancel(NotificationPresenter.draftRequestCode(summaryId))
                presenter.postTransient(context.getString(R.string.notification_reply_sent))
                ReplyBridge.forget(replyKey)
            }
            .onFailure { error ->
                Log.w(TAG, "Sending the reply failed", error)
                presenter.postTransient(context.getString(R.string.notification_reply_failed))
            }
    }

    companion object {
        const val ACTION_DRAFT_REPLY = "nl.markmaaktmedia.markmaaktai.DRAFT_REPLY"
        const val ACTION_SEND_REPLY = "nl.markmaaktmedia.markmaaktai.SEND_REPLY"

        const val EXTRA_SUMMARY_ID = "summary_id"
        const val EXTRA_REPLY_KEY = "reply_key"
        const val EXTRA_DRAFT_TEXT = "draft_text"
        const val KEY_REPLY_TEXT = "reply_text"

        private const val TAG = "NotificationAction"
    }
}
