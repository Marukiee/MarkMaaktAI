package nl.markmaaktmedia.markmaaktai.ai

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/** What a model file is meant to be used for. */
enum class ModelRole { TEXT, VISION, SPEECH }

/** Which runtime is behind an engine. Shown in the UI so it is never a mystery. */
enum class EngineKind { LITE_RT, LLAMA_CPP }

data class InferenceParams(
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxTokens: Int = 1024,
    val useGpu: Boolean = true,
)

/** One streamed step of a generation. */
sealed interface InferenceEvent {
    data object Started : InferenceEvent
    data class Token(val text: String) : InferenceEvent
    data class Completed(val text: String) : InferenceEvent
    data class Failed(val message: String, val cause: Throwable? = null) : InferenceEvent
}

/** A single prompt, already flattened by [nl.markmaaktmedia.markmaaktai.ai.prompt.PromptBuilder]. */
data class InferenceRequest(
    val prompt: String,
    val images: List<Bitmap> = emptyList(),
    val params: InferenceParams = InferenceParams(),
)

/**
 * The one thing every runtime has to provide. LiteRT is what ships today. The
 * llama.cpp implementation sits behind the same interface so swapping runtimes
 * is a binding change and nothing else.
 */
interface InferenceEngine {

    val kind: EngineKind

    /** True when this engine can take images alongside the prompt. */
    val supportsVision: Boolean

    /** Path of the model file currently held open, or null. */
    val loadedModelPath: String?

    /**
     * How many tokens the loaded graph holds in total, prompt and answer together.
     *
     * Zero when nothing is loaded. A caller that hands over a longer prompt than this
     * gets an empty answer rather than an error, so every prompt is trimmed against
     * it first.
     */
    val contextTokens: Int

    /** Whether this build can actually run the engine on this device. */
    fun isAvailable(): Boolean

    suspend fun load(modelPath: String, params: InferenceParams, withVision: Boolean)

    fun generate(request: InferenceRequest): Flow<InferenceEvent>

    suspend fun unload()
}

/** Raised when a caller asks for generation before a model file was picked. */
class NoModelLoadedException(message: String = "No model loaded") : IllegalStateException(message)
