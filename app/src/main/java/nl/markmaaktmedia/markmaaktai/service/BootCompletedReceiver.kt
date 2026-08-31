package nl.markmaaktmedia.markmaaktai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nl.markmaaktmedia.markmaaktai.service.screenshots.ScreenshotIndexWorker

/**
 * Puts the periodic work back after a reboot or an update.
 *
 * WorkManager already survives both, but a phone that has been off for a while comes
 * back with a backlog, and re-enqueueing with KEEP is cheap and makes the first scan
 * happen without waiting for the next window.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ScreenshotIndexWorker.schedulePeriodic(context)
                ScreenshotIndexWorker.scheduleModelPass(context)
            }
        }
    }
}
