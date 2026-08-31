package nl.markmaaktmedia.markmaaktai.util

import android.content.Context
import android.os.Build
import nl.markmaaktmedia.markmaaktai.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the stack trace of a crash to disk so it can be read after the restart.
 *
 * Android shows "app keeps crashing" and takes the reason with it. Without a device
 * plugged into a laptop there is no way to see what actually happened, which makes a
 * crash report from a user a description of the screen they were on and nothing else.
 *
 * The handler stores the trace and then hands control to whatever was installed
 * before it, so the process still dies the way Android expects. Anything else risks
 * leaving a half dead app running.
 *
 * The file stays on the phone and is never sent anywhere. It is shown on the next
 * launch with a button to copy it.
 */
@Singleton
class CrashReporter @Inject constructor(
    private val context: Context,
) {

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last stored crash, or null when the app has not crashed since it was cleared. */
    fun lastCrash(): String? = runCatching {
        crashFile().takeIf { it.exists() && it.length() > 0 }?.readText()
    }.getOrNull()

    fun clear() {
        runCatching { crashFile().delete() }
    }

    private fun write(thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("MarkMaaktAI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(timestamp.format(Date()))
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("thread: ${thread.name}")
            appendLine()
            append(trace)
        }
        crashFile().parentFile?.mkdirs()
        crashFile().writeText(report)
    }

    private fun crashFile(): File = File(File(context.filesDir, "crash"), "last_crash.txt")

    private companion object {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK)
    }
}
