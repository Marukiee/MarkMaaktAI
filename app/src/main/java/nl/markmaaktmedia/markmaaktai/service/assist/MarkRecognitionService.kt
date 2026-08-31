package nl.markmaaktmedia.markmaaktai.service.assist

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.ai.stt.SpeechEvent
import nl.markmaaktmedia.markmaaktai.di.AssistEntryPoint

/**
 * A recognition service backed by the offline model.
 *
 * The assistant registration requires one to be named, and pointing at the platform
 * recogniser would be a lie on a phone that has none. This exposes the same Vosk
 * model the app uses itself, so the assistant keeps working on a de-Googled ROM, and
 * any other app that asks this one to listen gets an offline answer too.
 */
class MarkRecognitionService : RecognitionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listeningJob: Job? = null

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, AssistEntryPoint::class.java)
    }

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        val callback = listener ?: return
        listeningJob?.cancel()
        listeningJob = scope.launch {
            runCatching { callback.readyForSpeech(Bundle()) }
            entryPoint.speechInput().listen().collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> callback.partialResults(event.text.asResults())
                    is SpeechEvent.Final -> {
                        callback.results(event.text.asResults())
                        listeningJob?.cancel()
                    }

                    is SpeechEvent.Failed -> callback.error(SpeechRecognizer.ERROR_CLIENT)
                    else -> Unit
                }
            }
        }
    }

    override fun onStopListening(listener: Callback?) {
        listeningJob?.cancel()
        listeningJob = null
    }

    override fun onCancel(listener: Callback?) {
        listeningJob?.cancel()
        listeningJob = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun String.asResults(): Bundle = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(this@asResults))
    }
}
