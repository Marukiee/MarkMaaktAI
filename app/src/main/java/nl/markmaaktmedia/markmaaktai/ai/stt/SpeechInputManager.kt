package nl.markmaaktmedia.markmaaktai.ai.stt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
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
            emit(SpeechEvent.Failed(NO_BACKEND))
        }
    }

    /**
     * Vosk, driven from our own microphone loop.
     *
     * Vosk ships a SpeechService that owns the AudioRecord and hands back nothing but
     * text. That cost two things worth having: there was no sound level to drive the
     * waveform, so the bars moved whether or not anyone was speaking, and the service
     * kept listening after the first sentence, so the assistant carried on recording
     * long after the question had been asked. Reading the microphone here gives both:
     * a real level per buffer, and an end as soon as one utterance is complete.
     */
    private fun listenWithVosk(modelPath: String): Flow<SpeechEvent> = callbackFlow {
        val worker = launch(Dispatchers.IO) {
            var model: Model? = null
            var recogniser: Recognizer? = null
            var record: AudioRecord? = null
            try {
                model = Model(modelPath)
                recogniser = Recognizer(model, SAMPLE_RATE)

                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE.toInt(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                if (minBuffer <= 0) {
                    trySend(SpeechEvent.Failed(MIC_UNAVAILABLE))
                    return@launch
                }
                val frame = maxOf(minBuffer / 2, SAMPLE_RATE.toInt() / 10)
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE.toInt(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, frame * 2) * 2,
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    trySend(SpeechEvent.Failed(MIC_UNAVAILABLE))
                    return@launch
                }

                record.startRecording()
                trySend(SpeechEvent.Ready)

                val buffer = ShortArray(frame)
                var heardAnything = false
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue

                    trySend(SpeechEvent.Level(levelOf(buffer, read)))

                    if (recogniser.acceptWaveForm(buffer, read)) {
                        val text = readField(recogniser.result, "text")
                        if (!text.isNullOrBlank()) {
                            trySend(SpeechEvent.Final(text))
                            return@launch
                        }
                        // Silence between words, not the end of the question.
                        if (heardAnything) continue
                    } else {
                        val partial = readField(recogniser.partialResult, "partial")
                        if (!partial.isNullOrBlank()) {
                            heardAnything = true
                            trySend(SpeechEvent.Partial(partial))
                        }
                    }
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w(TAG, "Vosk could not start", error)
                trySend(SpeechEvent.Failed(NO_BACKEND))
            } finally {
                runCatching { record?.stop() }
                runCatching { record?.release() }
                runCatching { recogniser?.close() }
                runCatching { model?.close() }
            }
        }

        worker.invokeOnCompletion { close() }
        awaitClose { worker.cancel() }
    }

    /**
     * How loud this buffer was, from silent to as loud as the microphone goes.
     *
     * Root mean square rather than peak, because a single click should not throw the
     * waveform to full height, and on a curve rather than straight, because speech
     * spends most of its time in the quiet part of the range and a linear reading
     * barely moves.
     */
    private fun levelOf(buffer: ShortArray, read: Int): Float {
        var sum = 0.0
        for (index in 0 until read) {
            val sample = buffer[index].toDouble()
            sum += sample * sample
        }
        val rms = kotlin.math.sqrt(sum / read) / Short.MAX_VALUE
        return (kotlin.math.sqrt(rms) * 1.6).coerceIn(0.0, 1.0).toFloat()
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
                // The platform reports decibels, roughly minus two to ten. The rest of
                // the app works in nothing to full, so it is converted here and every
                // reader gets one scale.
                trySend(SpeechEvent.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
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
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> NO_BACKEND
        SpeechRecognizer.ERROR_NO_MATCH -> "Nothing was understood"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nothing was said"
        else -> NO_BACKEND
    }

    private companion object {
        const val TAG = "SpeechInputManager"
        const val SAMPLE_RATE = 16000.0f

        /**
         * Said instead of "dictation failed", which is true but useless. The phone
         * has nothing to listen with until a speech model is downloaded, and that
         * is the one thing the user can actually do about it.
         */
        /** The microphone is there but the system would not hand it over. */
        const val MIC_UNAVAILABLE = "The microphone could not be opened"

        const val NO_BACKEND =
            "Speaking needs a speech model. Download one under Settings, Models."
    }
}
