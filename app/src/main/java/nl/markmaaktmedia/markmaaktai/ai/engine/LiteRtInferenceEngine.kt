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

    private val generationLock = Mutex()

    override val supportsVision: Boolean get() = visionEnabled

    override val loadedModelPath: String? get() = currentPath

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
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(totalTokenBudget(file.name, params.maxTokens))
                    .setPreferredBackend(
                        if (params.useGpu) LlmInference.Backend.GPU else LlmInference.Backend.CPU
                    )
                    .apply { if (withVision) setMaxNumImages(MAX_IMAGES) }
                    .build()
                inference = LlmInference.createFromOptions(context, options)
                currentPath = modelPath
                visionEnabled = withVision
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

                created.generateResponseAsync { partial, done ->
                    if (partial != null) {
                        builder.append(partial)
                        trySend(InferenceEvent.Token(partial))
                    }
                    if (done) {
                        trySend(InferenceEvent.Completed(builder.toString().trim()))
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
     * How many tokens the graph may be built for.
     *
     * A .task file is exported with a fixed KV cache, and asking for more than it was
     * built for fails at load with "Max number of tokens is larger than the maximum
     * cache size supported". The size is in the file name as `ekv1280` or `ekv4096`,
     * which is the only place it is readable without opening the model, so that is
     * where it is read from. Anything unrecognised falls back to the smallest cache
     * in the catalogue, because guessing low costs a shorter answer and guessing high
     * costs the whole session.
     */
    private fun totalTokenBudget(fileName: String, requestedAnswerTokens: Int): Int {
        val cacheSize = KV_CACHE_PATTERN.find(fileName)?.groupValues?.get(1)?.toIntOrNull()
            ?: DEFAULT_KV_CACHE
        val wanted = requestedAnswerTokens + PROMPT_TOKEN_BUDGET
        return wanted.coerceAtMost(cacheSize)
    }

    private fun closeHandle() {
        runCatching { inference?.close() }
            .onFailure { Log.w(TAG, "Closing the model handle failed", it) }
        inference = null
        currentPath = null
        visionEnabled = false
    }

    private companion object {
        const val TAG = "LiteRtEngine"

        /** Head room on top of the answer budget so a long prompt still fits. */
        const val PROMPT_TOKEN_BUDGET = 512

        /** Matches the `ekv1280` marker every LiteRT export carries in its name. */
        val KV_CACHE_PATTERN = Regex("ekv(\\d+)", RegexOption.IGNORE_CASE)

        /** The smallest cache anything in the catalogue was exported with. */
        const val DEFAULT_KV_CACHE = 1280

        const val MAX_IMAGES = 1
    }
}
