package nl.markmaaktmedia.markmaaktai.ai.stt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener as SystemRecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What dictation reports back while it is running. */
sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Level(val rms: Float) : SpeechEvent
    data class Failed(val reason: String) : SpeechEvent
}

/**
 * Offline dictation.
 *
 * Vosk first, because it runs entirely on the phone with a model the app downloaded
 * itself, which is the only version of this that works on a de-Googled ROM. The
 * platform recogniser is the fallback for a phone that has one, and on a GrapheneOS
 * install without Play services there simply is none, so the failure says that
 * plainly rather than looking broken.
 */
@Singleton
class SpeechInputManager @Inject constructor(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    fun hasMicrophonePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    suspend fun hasOfflineModel(): Boolean {
        val path = settings.current().speechModelPath
        return path.isNotBlank() && File(path).isDirectory
    }

    fun hasSystemRecogniser(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    /** Picks whichever backend is available and streams its results. */
    fun listen(): Flow<SpeechEvent> = flow {
        if (!hasMicrophonePermission()) {
            emit(SpeechEvent.Failed("Microphone permission has not been granted"))
            return@flow
        }
        val modelPath = settings.current().speechModelPath
        if (modelPath.isNotBlank() && File(modelPath).isDirectory) {
            emitAll(listenWithVosk(modelPath))
        } else if (hasSystemRecogniser()) {
            emitAll(listenWithSystem())
        } else {
            emit(SpeechEvent.Failed("No speech model installed and this phone has no recogniser"))
        }
    }

    private fun listenWithVosk(modelPath: String): Flow<SpeechEvent> = callbackFlow {
        var speechService: SpeechService? = null
        var model: Model? = null

        val started = runCatching {
            val loaded = withContext(Dispatchers.IO) { Model(modelPath) }
            model = loaded
            val recogniser = Recognizer(loaded, SAMPLE_RATE)
            val service = SpeechService(recogniser, SAMPLE_RATE)
            speechService = service

            service.startListening(object : VoskRecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    readField(hypothesis, "partial")?.let { trySend(SpeechEvent.Partial(it)) }
                }

                override fun onResult(hypothesis: String?) {
                    readField(hypothesis, "text")?.let { trySend(SpeechEvent.Final(it)) }
                }

                override fun onFinalResult(hypothesis: String?) {
                    readField(hypothesis, "text")?.let { trySend(SpeechEvent.Final(it)) }
                    close()
                }

                override fun onError(exception: Exception?) {
                    trySend(SpeechEvent.Failed(exception?.message ?: "Dictation failed"))
                    close()
                }

                override fun onTimeout() {
                    close()
                }
            })
            true
        }.getOrElse { error ->
            Log.w(TAG, "Vosk could not start", error)
            trySend(SpeechEvent.Failed(error.message ?: "The speech model could not be loaded"))
            close()
            false
        }

        if (started) trySend(SpeechEvent.Ready)

        awaitClose {
            runCatching { speechService?.stop() }
            runCatching { speechService?.shutdown() }
            runCatching { model?.close() }
        }
    }

    private fun listenWithSystem(): Flow<SpeechEvent> = callbackFlow {
        val recogniser = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Ask the platform to stay offline. It is only a request, which is one more
            // reason Vosk is preferred when a model is installed.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        recogniser.setRecognitionListener(object : SystemRecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechEvent.Ready)
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(SpeechEvent.Level(rmsdB))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { trySend(SpeechEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                firstResult(results)?.let { trySend(SpeechEvent.Final(it)) }
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechEvent.Failed(describeSystemError(error)))
                close()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        runCatching { recogniser.startListening(intent) }
            .onFailure {
                trySend(SpeechEvent.Failed(it.message ?: "Dictation could not start"))
                close()
            }

        awaitClose {
            runCatching { recogniser.stopListening() }
            runCatching { recogniser.destroy() }
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun readField(json: String?, field: String): String? = runCatching {
        JSONObject(json.orEmpty()).optString(field).takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun describeSystemError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "The microphone could not be read"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission has not been granted"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "This recogniser wanted the network. Install the offline speech model instead."
        SpeechRecognizer.ERROR_NO_MATCH -> "Nothing was understood"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nothing was said"
        else -> "Dictation failed"
    }

    private companion object {
        const val TAG = "SpeechInputManager"
        const val SAMPLE_RATE = 16000.0f
    }
}
