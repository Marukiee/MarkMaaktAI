package nl.markmaaktmedia.markmaaktai.ai

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.markmaaktmedia.markmaaktai.ai.prompt.PromptBudget
import nl.markmaaktmedia.markmaaktai.ai.prompt.PromptBuilder
import nl.markmaaktmedia.markmaaktai.ai.prompt.PromptContext
import nl.markmaaktmedia.markmaaktai.ai.prompt.PromptTurn
import nl.markmaaktmedia.markmaaktai.ai.prompt.StructuredSummary
import nl.markmaaktmedia.markmaaktai.ai.prompt.SummaryParser
import nl.markmaaktmedia.markmaaktai.ai.prompt.UrgencyRules
import nl.markmaaktmedia.markmaaktai.ai.vision.ImageTextExtractor
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the runtime is doing right now, so the UI can say so honestly. */
sealed interface EngineState {
    data object NoModel : EngineState
    data class Loading(val role: ModelRole) : EngineState
    data class Ready(val role: ModelRole, val modelName: String) : EngineState
    data class Failed(val reason: String) : EngineState
}

/**
 * Decides which model runs which job, and keeps exactly one of them in memory.
 *
 * A phone has room for one of these at a time, so the small text model and the
 * larger vision model take turns. Swapping is not free, which is why the rule is
 * simple and predictable: text work stays on the text model, an image pulls in the
 * vision model, and when no vision model is installed the image is read with OCR
 * and the extracted text is handed to the text model instead. That fallback is
 * usually the better trade anyway for a screenshot, which is mostly words.
 */
@Singleton
class AiOrchestrator @Inject constructor(
    private val engine: InferenceEngine,
    private val settings: SettingsRepository,
    private val imageTextExtractor: ImageTextExtractor,
) {

    private val loadLock = Mutex()

    private val _state = MutableStateFlow<EngineState>(EngineState.NoModel)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    suspend fun hasTextModel(): Boolean = settings.current().textModelPath.isModelFile()

    suspend fun hasVisionModel(): Boolean = settings.current().visionModelPath.isModelFile()

    /**
     * Streams an answer for a chat turn. Any images are read first, because the
     * recognised text is useful to both paths: it is the whole input for the OCR
     * fallback, and it keeps a small vision model honest about what a screenshot
     * actually says.
     */
    fun chat(
        history: List<PromptTurn>,
        question: String,
        images: List<Bitmap> = emptyList(),
        context: PromptContext = PromptContext(),
    ): Flow<InferenceEvent> = flow {
        val prefs = settings.current()
        val wantsVision = images.isNotEmpty() && prefs.visionModelPath.isModelFile()
        val role = if (wantsVision) ModelRole.VISION else ModelRole.TEXT

        val imageText = if (images.isNotEmpty()) {
            runCatching { imageTextExtractor.extractStructured(images.first()) }.getOrDefault("")
        } else {
            ""
        }

        when (val prepared = prepare(role, prefs.toParams())) {
            is PrepareResult.Failed -> {
                emit(InferenceEvent.Failed(prepared.reason))
                return@flow
            }

            is PrepareResult.Ready -> Unit
        }

        // Measured against the graph that is now open, not against the setting. The
        // setting is a wish; the KV cache the .task file was exported with is the law.
        val budget = PromptBudget.forContext(engine.contextTokens, prefs.maxTokens)

        val prompt = PromptBuilder.buildChat(
            history = history,
            question = question,
            context = context.copy(imageText = imageText.ifBlank { context.imageText }),
            budget = budget,
        )

        val request = InferenceRequest(
            prompt = prompt,
            images = if (wantsVision) images else emptyList(),
            params = prefs.toParams().copy(maxTokens = budget.answerTokens),
        )
        emitAll(engine.generate(request))
    }

    /**
     * Asks the vision model what is actually in a picture, in a form that can be
     * searched for.
     *
     * The point is the second step, not this one. A small vision model cannot name a
     * landmark, but it can list what makes it recognisable: the number of towers, the
     * material, the shape of a roof, any words on a sign. Those words are a far better
     * search query than "where is this?", and the web can do the naming.
     *
     * Deliberately not streamed and deliberately short. Nobody reads this; it exists
     * to be turned into a query.
     */
    suspend fun describeImage(image: Bitmap): Result<String> {
        val prefs = settings.current()
        if (!prefs.visionModelPath.isModelFile()) {
            return Result.failure(NoModelLoadedException("No vision model is installed"))
        }

        val params = prefs.toParams().copy(maxTokens = DESCRIPTION_TOKENS)
        when (val prepared = prepare(ModelRole.VISION, params)) {
            is PrepareResult.Failed -> return Result.failure(NoModelLoadedException(prepared.reason))
            is PrepareResult.Ready -> Unit
        }

        val builder = StringBuilder()
        var failure: String? = null
        engine.generate(
            InferenceRequest(
                prompt = PromptBuilder.buildImageDescription(),
                images = listOf(image),
                params = params,
            )
        )
            .onEach { event ->
                when (event) {
                    is InferenceEvent.Token -> builder.append(event.text)
                    is InferenceEvent.Failed -> failure = event.message
                    else -> Unit
                }
            }
            .collectSafely()

        return failure?.let { Result.failure(IllegalStateException(it)) }
            ?: Result.success(builder.toString().trim())
    }

    /**
     * One shot completion used by the background work: summaries, reply drafts and
     * conversation titles. Always runs on the small text model, which is what keeps
     * notification handling cheap enough to do on every burst.
     */
    suspend fun complete(prompt: String, maxTokens: Int = 320): Result<String> {
        val prefs = settings.current()
        val params = prefs.toParams().copy(maxTokens = maxTokens)
        when (val prepared = prepare(ModelRole.TEXT, params)) {
            is PrepareResult.Failed -> return Result.failure(NoModelLoadedException(prepared.reason))
            is PrepareResult.Ready -> Unit
        }

        val trimmed = prompt.trimToBudget(params.maxTokens)

        val builder = StringBuilder()
        var failure: String? = null
        engine.generate(InferenceRequest(prompt = trimmed, params = params))
            .onEach { event ->
                when (event) {
                    is InferenceEvent.Token -> builder.append(event.text)
                    is InferenceEvent.Failed -> failure = event.message
                    else -> Unit
                }
            }
            .collectSafely()

        return failure?.let { Result.failure(IllegalStateException(it)) }
            ?: Result.success(builder.toString().trim())
    }

    /**
     * Summarises a burst, then checks the verdict rather than trusting it.
     *
     * The model may confirm that something is urgent, never decide it on its own.
     * Left to itself it marks roughly everything urgent, and a badge that is always
     * on is the same as no badge at all.
     */
    suspend fun summarise(appLabel: String, messages: List<String>): Result<StructuredSummary> =
        complete(PromptBuilder.buildSummary(appLabel, messages), maxTokens = SUMMARY_TOKENS)
            .map { SummaryParser.parse(it) }

    /** Applies the urgency rule to a parsed summary, with the source text to check against. */
    fun confirmUrgency(
        summary: StructuredSummary,
        packageName: String,
        messages: List<String>,
    ): StructuredSummary = summary.copy(
        isUrgent = UrgencyRules.isUrgent(
            modelSaidUrgent = summary.isUrgent,
            packageName = packageName,
            category = summary.category,
            text = (messages + summary.summary).joinToString(" "),
        ),
    )

    suspend fun draftReply(appLabel: String, messages: List<String>): Result<String> =
        complete(PromptBuilder.buildReplyDraft(appLabel, messages), maxTokens = 96)
            .map { it.lines().firstOrNull { line -> line.isNotBlank() }.orEmpty().trim('"', ' ') }

    suspend fun suggestTitle(firstMessage: String): Result<String> =
        complete(PromptBuilder.buildTitle(firstMessage), maxTokens = 24)
            .map { raw ->
                raw.lines().firstOrNull { it.isNotBlank() }.orEmpty()
                    .trim('"', '.', ' ')
                    .take(48)
            }

    suspend fun release() {
        loadLock.withLock {
            runCatching { engine.unload() }
            _state.value = EngineState.NoModel
        }
    }

    private sealed interface PrepareResult {
        data object Ready : PrepareResult
        data class Failed(val reason: String) : PrepareResult
    }

    private suspend fun prepare(role: ModelRole, params: InferenceParams): PrepareResult =
        loadLock.withLock {
            val prefs = settings.current()
            val path = when (role) {
                ModelRole.VISION -> prefs.visionModelPath
                else -> prefs.textModelPath
            }
            if (!path.isModelFile()) {
                _state.value = EngineState.NoModel
                return@withLock PrepareResult.Failed(NO_MODEL_MESSAGE)
            }
            if (engine.loadedModelPath == path) {
                _state.value = EngineState.Ready(role, File(path).name)
                return@withLock PrepareResult.Ready
            }

            _state.value = EngineState.Loading(role)
            runCatching { engine.load(path, params, withVision = role == ModelRole.VISION) }
                .fold(
                    onSuccess = {
                        _state.value = EngineState.Ready(role, File(path).name)
                        PrepareResult.Ready
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Loading the model failed", error)
                        val reason = error.message ?: "The model could not be loaded"
                        _state.value = EngineState.Failed(reason)
                        PrepareResult.Failed(reason)
                    },
                )
        }

    /**
     * Last line of defence for the one shot prompts.
     *
     * The chat path budgets its context properly. The background prompts are built
     * from OCR text and notification bodies whose length nobody controls, so they are
     * cut here rather than coming back empty from a full context.
     */
    private fun String.trimToBudget(answerTokens: Int): String {
        val budget = PromptBudget.forContext(engine.contextTokens, answerTokens)
        if (length <= budget.promptChars) return this
        // Keep the head, which holds the instructions, and the tail, which holds the
        // cue the model answers after.
        val head = (budget.promptChars * 3) / 4
        val tail = budget.promptChars - head - ELLIPSIS.length
        return take(head) + ELLIPSIS + takeLast(tail.coerceAtLeast(0))
    }

    private fun String.isModelFile(): Boolean =
        isNotBlank() && File(this).let { it.exists() && it.length() > 0 }

    private fun nl.markmaaktmedia.markmaaktai.data.prefs.UserSettings.toParams() = InferenceParams(
        temperature = temperature,
        maxTokens = maxTokens,
        useGpu = useGpu,
    )

    private suspend fun Flow<InferenceEvent>.collectSafely() {
        runCatching { collect { } }.onFailure { Log.w(TAG, "Generation ended early", it) }
    }

    private companion object {
        const val TAG = "AiOrchestrator"
        const val NO_MODEL_MESSAGE = "No model has been picked yet"

        /** Room for the JSON object and its fields, and not a word more. */
        const val SUMMARY_TOKENS = 260

        const val ELLIPSIS = "\n...\n"
    }

    /** Enough for a line of visible detail, not an essay. */
    private val DESCRIPTION_TOKENS = 96
}
