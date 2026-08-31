package nl.markmaaktmedia.markmaaktai.service.notifications

import android.app.PendingIntent
import android.app.RemoteInput
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds on to the reply action of a notification we are still working on.
 *
 * A reply cannot be recreated later: it is a [PendingIntent] the source app handed
 * out, tied to the notification it came with. So when a notification arrives with a
 * reply action, the action is kept here under the notification key, and the entry is
 * dropped as soon as that notification goes away.
 *
 * In memory on purpose. These intents stop working once the source app has moved on,
 * so persisting them would only produce sends that silently fail.
 */
object ReplyBridge {

    data class PendingReply(
        val packageName: String,
        val appLabel: String,
        val pendingIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>,
        val resultKey: String,
    ) {
        // Arrays make the generated equals and hashCode useless for a data class, and
        // this type is only ever compared by identity in practice.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private val replies = ConcurrentHashMap<String, PendingReply>()

    fun remember(key: String, reply: PendingReply) {
        if (replies.size > MAX_ENTRIES) replies.clear()
        replies[key] = reply
    }

    fun get(key: String?): PendingReply? = key?.let { replies[it] }

    fun forget(key: String?) {
        key?.let { replies.remove(it) }
    }

    fun clear() = replies.clear()

    /** A burst of group chats can pile these up, and none of them stay valid long. */
    private const val MAX_ENTRIES = 200
}
