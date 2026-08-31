package nl.markmaaktmedia.markmaaktai.ai

/**
 * A model the app knows about. Files can also come in through the file picker, in
 * which case only the file name and the role are known.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val role: ModelRole,
    val fileName: String,
    val sizeBytes: Long,
    /** One line, shown under the name. Says what it is good at and what it costs. */
    val summary: String,
    val downloadUrl: String? = null,
    val engineKind: EngineKind = EngineKind.LITE_RT,
    /** The one offered on the onboarding screen and preselected in settings. */
    val isRecommended: Boolean = false,
    /** Marked as not fully proven, so the UI can say so instead of the user finding out. */
    val isExperimental: Boolean = false,
)

/**
 * The models offered in the app.
 *
 * Every entry downloads straight from a public link with no account, no token and no
 * licence click-through. That rules out the Gemma and Llama builds, which look like
 * the obvious picks until you find they answer a download with 401 unless you have
 * agreed to their terms while signed in to Hugging Face. Sending someone off to make
 * an account before the app does anything is the opposite of installing an APK and
 * being done, so those are simply not on the list.
 *
 * Sizes are the real byte counts from the download links, so the progress bar and
 * the "this needs X GB free" warning are honest before the first byte arrives.
 */
object ModelCatalog {

    val textModels = listOf(
        ModelSpec(
            id = "qwen2.5-1.5b-instruct-q8",
            displayName = "Qwen 2.5 1.5B",
            role = ModelRole.TEXT,
            fileName = "qwen2.5-1.5b-instruct-q8.task",
            sizeBytes = 1_597_913_616L,
            summary = "The best all rounder here, and good at Dutch. Around 1.5 GB.",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            isRecommended = true,
        ),
        ModelSpec(
            id = "qwen2.5-0.5b-instruct-q8",
            displayName = "Qwen 2.5 0.5B",
            role = ModelRole.TEXT,
            fileName = "qwen2.5-0.5b-instruct-q8.task",
            sizeBytes = 546_660_344L,
            summary = "Half a gigabyte and quick to answer. Fine for notification summaries.",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        ),
        ModelSpec(
            id = "deepseek-r1-distill-qwen-1.5b",
            displayName = "DeepSeek R1 Distill 1.5B",
            role = ModelRole.TEXT,
            fileName = "deepseek-r1-distill-qwen-1.5b-q8.task",
            sizeBytes = 1_861_094_737L,
            summary = "Reasons its way to an answer, so it is slower and more thorough.",
            downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task",
        ),
        ModelSpec(
            id = "smollm2-135m-instruct",
            displayName = "SmolLM2 135M",
            role = ModelRole.TEXT,
            fileName = "smollm2-135m-instruct.litertlm",
            sizeBytes = 142_819_328L,
            summary = "Tiny and instant, but it is a 135M model. Good on a very tight phone.",
            downloadUrl = "https://huggingface.co/litert-community/SmolLM2-135M-Instruct/resolve/main/SmolLM2_135M_Instruct.litertlm",
        ),
    )

    val visionModels = listOf(
        ModelSpec(
            id = "fastvlm-0.5b",
            displayName = "FastVLM 0.5B",
            role = ModelRole.VISION,
            fileName = "fastvlm-0.5b.litertlm",
            sizeBytes = 1_156_342_768L,
            summary = "Describes what is in a photo. Text on screenshots is read by OCR either way.",
            downloadUrl = "https://huggingface.co/litert-community/FastVLM-0.5B/resolve/main/FastVLM-0.5B.litertlm",
            isExperimental = true,
        ),
    )

    val speechModels = listOf(
        ModelSpec(
            id = "vosk-nl-small",
            displayName = "Dutch speech",
            role = ModelRole.SPEECH,
            fileName = "vosk-model-small-nl-0.22.zip",
            sizeBytes = 39_000_000L,
            summary = "Offline Dutch dictation. Unpacked after downloading.",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip",
        ),
        ModelSpec(
            id = "vosk-en-small",
            displayName = "English speech",
            role = ModelRole.SPEECH,
            fileName = "vosk-model-small-en-us-0.15.zip",
            sizeBytes = 40_000_000L,
            summary = "Offline English dictation. Unpacked after downloading.",
            downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        ),
    )

    /** What the onboarding offers behind a single button. */
    val recommendedText: ModelSpec = textModels.first { it.isRecommended }

    fun forRole(role: ModelRole): List<ModelSpec> = when (role) {
        ModelRole.TEXT -> textModels
        ModelRole.VISION -> visionModels
        ModelRole.SPEECH -> speechModels
    }

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }

    val all: List<ModelSpec> = textModels + visionModels + speechModels
}
