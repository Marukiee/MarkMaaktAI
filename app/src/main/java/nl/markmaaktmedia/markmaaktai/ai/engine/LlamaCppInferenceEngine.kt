package nl.markmaaktmedia.markmaaktai.ai.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import nl.markmaaktmedia.markmaaktai.ai.EngineKind
import nl.markmaaktmedia.markmaaktai.ai.InferenceEngine
import nl.markmaaktmedia.markmaaktai.ai.InferenceEvent
import nl.markmaaktmedia.markmaaktai.ai.InferenceParams
import nl.markmaaktmedia.markmaaktai.ai.InferenceRequest

/**
 * Placeholder for a GGUF runtime.
 *
 * The plan is a thin JNI layer over llama.cpp built with the Vulkan and OpenCL
 * backends, which is what gets GGUF text and mtmd vision models running with
 * hardware acceleration on Snapdragon and Tensor parts. That needs the NDK in the
 * build, so it is kept out of the shipping app until the native side is in place.
 *
 * It lives here rather than in a branch on purpose: the rest of the app only ever
 * talks to [InferenceEngine], so turning this on later is a binding change in
 * `AiModule` and nothing else. [isAvailable] returning false keeps it out of the
 * engine picker in the meantime.
 */
class LlamaCppInferenceEngine : InferenceEngine {

    override val kind: EngineKind = EngineKind.LLAMA_CPP

    override val supportsVision: Boolean = false

    override val loadedModelPath: String? = null

    override fun isAvailable(): Boolean = NATIVE_LIBRARY_PRESENT

    override suspend fun load(modelPath: String, params: InferenceParams, withVision: Boolean) {
        error(UNAVAILABLE)
    }

    override fun generate(request: InferenceRequest): Flow<InferenceEvent> = flow {
        emit(InferenceEvent.Failed(UNAVAILABLE))
    }

    override suspend fun unload() = Unit

    private companion object {
        /**
         * Flipped on once `libmarkllama.so` ships in the APK. Checked instead of a
         * hard coded false so a local NDK build can enable the engine without
         * touching call sites.
         */
        val NATIVE_LIBRARY_PRESENT: Boolean = runCatching {
            System.loadLibrary("markllama")
            true
        }.getOrDefault(false)

        const val UNAVAILABLE = "The llama.cpp runtime is not part of this build yet"
    }
}
