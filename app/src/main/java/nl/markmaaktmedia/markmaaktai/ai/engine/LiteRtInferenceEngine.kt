package nl.markmaaktmedia.markmaaktai.ai.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.markmaaktmedia.markmaaktai.ai.EngineKind
import nl.markmaaktmedia.markmaaktai.ai.InferenceEngine
import nl.markmaaktmedia.markmaaktai.ai.InferenceEvent
import nl.markmaaktmedia.markmaaktai.ai.InferenceParams
import nl.markmaaktmedia.markmaaktai.ai.InferenceRequest
import nl.markmaaktmedia.markmaaktai.ai.NoModelLoadedException
import java.io.File

/**
 * LiteRT (MediaPipe GenAI) backed engine. Handles both the small text model used
 * for notification work and the larger vision model, which is why loading takes a
 * `withVision` flag: the graph has to be built for it up front.
 *
 * One [LlmInference] handle is expensive, so it is kept open across calls and only
 * rebuilt when the model path or the vision flag actually changes. Generation is
 * serialised through a mutex because the native handle is single threaded.
 */
class LiteRtInferenceEngine(
    private val context: Context,
) : InferenceEngine {

    override val kind: EngineKind = EngineKind.LITE_RT

    @Volatile
    private var visionEnabled: Boolean = false

    @Volatile
    private var inference: LlmInference? = null

    @Volatile
    private var currentPath: String? = null

    @Volatile
    private var loadedContextTokens: Int = 0

    private val generationLock = Mutex()

    override val supportsVision: Boolean get() = visionEnabled

    override val loadedModelPath: String? get() = currentPath

    override val contextTokens: Int get() = loadedContextTokens

    override fun isAvailable(): Boolean = true

    override suspend fun load(modelPath: String, params: InferenceParams, withVision: Boolean) {
        if (modelPath.isBlank()) throw NoModelLoadedException("Model path is empty")
        val file = File(modelPath)
        if (!file.exists() || file.length() == 0L) {
            throw NoModelLoadedException("Model file is missing: $modelPath")
        }
        if (currentPath == modelPath && visionEnabled == withVision && inference != null) return

        generationLock.withLock {
            withContext(Dispatchers.IO) {
                closeHandle()
                val budget = totalTokenBudget(file.name)
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(budget)
                    .setPreferredBackend(
                        if (params.useGpu) LlmInference.Backend.GPU else LlmInference.Backend.CPU
                    )
                    .apply { if (withVision) setMaxNumImages(MAX_IMAGES) }
                    .build()
                inference = LlmInference.createFromOptions(context, options)
                currentPath = modelPath
                visionEnabled = withVision
                loadedContextTokens = budget
            }
        }
    }

    override fun generate(request: InferenceRequest): Flow<InferenceEvent> = callbackFlow {
        val handle = inference ?: run {
            trySend(InferenceEvent.Failed("No model loaded"))
            close()
            return@callbackFlow
        }

        var session: LlmInferenceSession? = null
        val builder = StringBuilder()

        generationLock.withLock {
            try {
                send(InferenceEvent.Started)
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(request.params.temperature)
                    .setTopK(request.params.topK)
                    .setTopP(request.params.topP)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(visionEnabled && request.images.isNotEmpty())
                            .build()
                    )
                    .build()

                val created = withContext(Dispatchers.IO) {
                    LlmInferenceSession.createFromOptions(handle, sessionOptions)
                }
                session = created

                if (request.prompt.isNotBlank()) created.addQueryChunk(request.prompt)
                if (visionEnabled) {
                    request.images.take(MAX_IMAGES).forEach { bitmap ->
                        created.addImage(BitmapImageBuilder(bitmap).build())
                    }
                }

                // The callback runs on a native thread of MediaPipe's own. Anything
                // thrown in here would come up on a thread nobody is watching and take
                // the process down, so it never leaves this block.
                created.generateResponseAsync { partial, done ->
                    runCatching {
                        if (partial != null) {
                            builder.append(partial)
                            trySend(InferenceEvent.Token(partial))
                        }
                        if (done) {
                            val answer = builder.toString().trim()
                            if (answer.isEmpty()) {
                                trySend(InferenceEvent.Failed(EMPTY_ANSWER))
                            } else {
                                trySend(InferenceEvent.Completed(answer))
                            }
                            close()
                        }
                    }.onFailure { error ->
                        Log.e(TAG, "Streaming failed", error)
                        trySend(InferenceEvent.Failed(error.message ?: "Generation failed", error))
                        close()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "Generation failed", error)
                trySend(InferenceEvent.Failed(error.message ?: "Generation failed", error))
                close()
            }

            awaitClose {
                // Closing the session is what stops a running generation: the native
                // handle has no separate cancel, so the handle itself has to go.
                runCatching { session?.close() }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun unload() {
        generationLock.withLock {
            withContext(Dispatchers.IO) { closeHandle() }
        }
    }

    /**
     * How many tokens the graph is built for: all of them.
     *
     * A .task file is exported with a fixed KV cache, and asking for more than it was
     * built for fails at load with "Max number of tokens is larger than the maximum
     * cache size supported". The size is in the file name as `ekv1280` or `ekv4096`,
     * which is the only place it is readable without opening the model, so that is
     * where it is read from. Anything unrecognised falls back to the smallest cache
     * in the catalogue, because guessing low costs a shorter answer and guessing high
     * costs the whole session.
     *
     * It used to be built for the answer budget plus some head room, which was the
     * mistake behind half the blank answers. The handle is kept open across calls, so
     * whichever job ran first fixed the size for every job after it: a 260 token
     * summary at start-up left the chat with 772 tokens for a prompt that needed more,
     * and the runtime answers an overlong prompt with an empty string. The cache is
     * already paid for in memory the moment the model is loaded, so there is nothing
     * to save by building it smaller.
     */
    private fun totalTokenBudget(fileName: String): Int =
        KV_CACHE_PATTERN.find(fileName)?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_KV_CACHE

    private fun closeHandle() {
        runCatching { inference?.close() }
            .onFailure { Log.w(TAG, "Closing the model handle failed", it) }
        inference = null
        currentPath = null
        visionEnabled = false
        loadedContextTokens = 0
    }

    private companion object {
        const val TAG = "LiteRtEngine"

        /** Matches the `ekv1280` marker every LiteRT export carries in its name. */
        val KV_CACHE_PATTERN = Regex("ekv(\\d+)", RegexOption.IGNORE_CASE)

        /** The smallest cache anything in the catalogue was exported with. */
        const val DEFAULT_KV_CACHE = 1280

        const val MAX_IMAGES = 1

        /**
         * What the runtime hands back when the prompt filled the whole context: a
         * finished generation with nothing in it. Saying that plainly beats an empty
         * bubble the user has to guess at.
         */
        const val EMPTY_ANSWER =
            "The model returned nothing. The question and its context were probably too " +
                "long for this model. Ask something shorter, or switch off a source."
    }
}
