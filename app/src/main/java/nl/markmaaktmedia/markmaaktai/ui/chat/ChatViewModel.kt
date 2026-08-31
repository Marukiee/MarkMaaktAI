package nl.markmaaktmedia.markmaaktai.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.ai.AiOrchestrator
import nl.markmaaktmedia.markmaaktai.ai.EngineState
import nl.markmaaktmedia.markmaaktai.ai.InferenceEvent
import nl.markmaaktmedia.markmaaktai.ai.prompt.PromptContext
import nl.markmaaktmedia.markmaaktai.ai.prompt.PromptTurn
import nl.markmaaktmedia.markmaaktai.ai.stt.SpeechEvent
import nl.markmaaktmedia.markmaaktai.ai.stt.SpeechInputManager
import nl.markmaaktmedia.markmaaktai.data.db.ConversationEntity
import nl.markmaaktmedia.markmaaktai.data.db.MessageEntity
import nl.markmaaktmedia.markmaaktai.data.db.WebSource
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import nl.markmaaktmedia.markmaaktai.data.remote.SearchOutcome
import nl.markmaaktmedia.markmaaktai.data.remote.WebSearchClient
import nl.markmaaktmedia.markmaaktai.data.repository.ChatRepository
import nl.markmaaktmedia.markmaaktai.data.repository.NotificationRepository
import nl.markmaaktmedia.markmaaktai.data.repository.ScreenshotRepository
import javax.inject.Inject

/** What the composer and the transcript need to draw themselves. */
data class ChatUiState(
    val conversationId: Long = 0L,
    val input: String = "",
    val attachmentPath: String? = null,
    val isGenerating: Boolean = false,
    val stage: WorkStage = WorkStage.Idle,
    val webSearchEnabled: Boolean = false,
    val phoneContextEnabled: Boolean = true,
    val isListening: Boolean = false,
    val partialSpeech: String = "",
    val error: String? = null,
    val hasTextModel: Boolean = true,
    /** Kept so the error dialog can offer to run the same question again. */
    val lastQuestion: String = "",
    val engineState: EngineState = EngineState.NoModel,
)

/** The step the answer is on, so the status line can say something true. */
enum class WorkStage { Idle, Searching, ReadingPhone, LoadingModel, Thinking }

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val notificationRepository: NotificationRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val searchClient: WebSearchClient,
    private val orchestrator: AiOrchestrator,
    private val speechInput: SpeechInputManager,
    private val settings: SettingsRepository,
    private val handoff: nl.markmaaktmedia.markmaaktai.ui.navigation.ChatHandoff,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val conversationId = MutableStateFlow(0L)

    val messages: StateFlow<List<MessageEntity>> = conversationId
        .flatMapLatest { id ->
            if (id == 0L) kotlinx.coroutines.flow.flowOf(emptyList())
            else chatRepository.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = chatRepository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var generationJob: Job? = null
    private var dictationJob: Job? = null

    init {
        viewModelScope.launch {
            val prefs = settings.current()
            _uiState.update {
                it.copy(
                    webSearchEnabled = prefs.webSearchEnabled,
                    hasTextModel = orchestrator.hasTextModel(),
                )
            }
        }
        viewModelScope.launch {
            orchestrator.state.collect { state ->
                _uiState.update { it.copy(engineState = state) }
            }
        }
        // A question handed over from another tab arrives here and is asked straight
        // away, so "ask about this" lands on an answer rather than a filled in box.
        viewModelScope.launch {
            handoff.pending.collect { question ->
                if (!question.isNullOrBlank()) {
                    handoff.clear()
                    newConversation()
                    send(question)
                }
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, error = null) }
    }

    fun onAttachmentPicked(uri: Uri) {
        viewModelScope.launch {
            val path = chatRepository.saveAttachment(uri)
            _uiState.update { it.copy(attachmentPath = path) }
        }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(attachmentPath = null) }
    }

    fun toggleWebSearch() {
        val next = !_uiState.value.webSearchEnabled
        _uiState.update { it.copy(webSearchEnabled = next) }
        viewModelScope.launch { settings.setWebSearchEnabled(next) }
    }

    fun togglePhoneContext() {
        _uiState.update { it.copy(phoneContextEnabled = !it.phoneContextEnabled) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun newConversation() {
        stop()
        conversationId.value = 0L
        _uiState.update { it.copy(conversationId = 0L, input = "", attachmentPath = null) }
    }

    fun openConversation(id: Long) {
        stop()
        conversationId.value = id
        _uiState.update { it.copy(conversationId = id) }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (conversationId.value == id) newConversation()
        }
    }

    fun togglePin(conversation: ConversationEntity) {
        viewModelScope.launch {
            chatRepository.setPinned(conversation.id, !conversation.pinned)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { chatRepository.deleteMessage(messageId) }
    }

    /** Runs the last question again, straight from the error dialog. */
    fun retryLast() {
        val question = _uiState.value.lastQuestion
        if (question.isNotBlank()) send(question)
    }

    fun send(prefilled: String? = null) {
        val state = _uiState.value
        val question = (prefilled ?: state.input).trim()
        if (question.isEmpty() && state.attachmentPath == null) return
        if (state.isGenerating) return

        generationJob = viewModelScope.launch {
            runAnswer(question, state.attachmentPath)
        }
    }

    fun stop() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false, stage = WorkStage.Idle) }
    }

    private suspend fun runAnswer(question: String, attachmentPath: String?) {
        val id = conversationId.value.takeIf { it != 0L } ?: chatRepository.createConversation().also {
            conversationId.value = it
            _uiState.update { state -> state.copy(conversationId = it) }
        }

        _uiState.update {
            it.copy(
                input = "",
                attachmentPath = null,
                isGenerating = true,
                error = null,
                lastQuestion = question,
            )
        }
        chatRepository.addUserMessage(id, question, attachmentPath)

        val history = chatRepository.history(id)
            .dropLast(1)
            .filter { it.content.isNotBlank() }
            .map { entity ->
                PromptTurn(
                    role = if (entity.role == ChatRepository.ROLE_USER) PromptTurn.Role.USER
                    else PromptTurn.Role.ASSISTANT,
                    text = entity.content,
                )
            }

        var sources: List<WebSource> = emptyList()
        var promptContext = PromptContext()

        if (_uiState.value.webSearchEnabled && question.isNotBlank()) {
            _uiState.update { it.copy(stage = WorkStage.Searching) }
            val prefs = settings.current()
            val outcome = searchClient.search(
                query = question,
                limit = prefs.searchResultCount,
                searxngUrl = prefs.searxngUrl.ifBlank { nl.markmaaktmedia.markmaaktai.BuildConfig.DEFAULT_SEARXNG_URL },
                braveApiKey = prefs.braveApiKey,
            )
            when (outcome) {
                is SearchOutcome.Results -> {
                    sources = outcome.sources
                    promptContext = promptContext.copy(webResults = sources)
                }

                is SearchOutcome.Failed -> _uiState.update { it.copy(error = outcome.reason) }
            }
        }

        if (_uiState.value.phoneContextEnabled && question.isNotBlank()) {
            _uiState.update { it.copy(stage = WorkStage.ReadingPhone) }
            // Only pulled in when the question actually matches something. A question
            // about the weather should not drag yesterday's notifications along.
            val notificationLines = notificationRepository.search(question, limit = 12)
                .map { "${it.appLabel}, ${it.title.ifBlank { it.appLabel }}: ${it.body.take(280)}" }
            val screenshotLines = screenshotRepository.contextFor(question, limit = 4)
            val combined = notificationLines + screenshotLines
            if (combined.isNotEmpty()) {
                promptContext = promptContext.copy(notificationLines = combined)
            }
        }

        val bitmap = attachmentPath?.let { chatRepository.loadAttachment(it) }
        _uiState.update { it.copy(stage = WorkStage.LoadingModel) }

        val assistantId = chatRepository.startAssistantMessage(id)
        val builder = StringBuilder()
        var lastPersisted = 0
        var failed = false

        orchestrator.chat(
            history = history,
            question = question.ifBlank { DESCRIBE_IMAGE },
            images = listOfNotNull(bitmap),
            context = promptContext,
        ).collect { event ->
            when (event) {
                is InferenceEvent.Started -> _uiState.update { it.copy(stage = WorkStage.Thinking) }

                is InferenceEvent.Token -> {
                    builder.append(event.text)
                    // Persisting every token would write hundreds of rows a second.
                    // The transcript reads from the database, so it is updated often
                    // enough to look live and rarely enough to stay cheap.
                    if (builder.length - lastPersisted >= PERSIST_EVERY_CHARS) {
                        lastPersisted = builder.length
                        chatRepository.updateAssistantMessage(assistantId, builder.toString())
                    }
                }

                is InferenceEvent.Completed -> {
                    finish(id, assistantId, event.text.ifBlank { builder.toString() }, sources, false)
                }

                is InferenceEvent.Failed -> {
                    // The failure is not something the assistant said, so it does not
                    // go in the transcript. The empty bubble is removed and the reason
                    // is raised as a dialog instead.
                    failed = true
                    chatRepository.deleteMessage(assistantId)
                    _uiState.update {
                        it.copy(error = event.message, hasTextModel = orchestrator.hasTextModel())
                    }
                }
            }
        }

        // A cancelled generation still leaves whatever arrived, which is more useful
        // than throwing away half an answer.
        if (!failed && builder.isNotEmpty() && lastPersisted != builder.length) {
            chatRepository.updateAssistantMessage(assistantId, builder.toString())
        }
        if (!failed && builder.isEmpty()) {
            // Nothing came back at all, so the empty bubble would just sit there.
            chatRepository.deleteMessage(assistantId)
        }
        _uiState.update { it.copy(isGenerating = false, stage = WorkStage.Idle) }
        if (!failed) maybeTitle(id, question)
    }

    private suspend fun finish(
        conversation: Long,
        assistantId: Long,
        text: String,
        sources: List<WebSource>,
        isError: Boolean,
    ) {
        val message = chatRepository.messageById(conversation, assistantId) ?: return
        chatRepository.finishAssistantMessage(message, text, sources, isError)
    }

    /** Names a new thread once, after the first exchange, and never touches it again. */
    private suspend fun maybeTitle(conversationId: Long, firstMessage: String) {
        val conversation = chatRepository.conversation(conversationId) ?: return
        if (conversation.title != DEFAULT_TITLE) return
        if (firstMessage.isBlank()) return
        val title = orchestrator.suggestTitle(firstMessage).getOrNull()
        if (!title.isNullOrBlank()) {
            chatRepository.rename(conversationId, title)
        } else {
            chatRepository.rename(conversationId, firstMessage.take(40))
        }
    }

    fun startDictation() {
        if (_uiState.value.isListening) {
            stopDictation()
            return
        }
        dictationJob = viewModelScope.launch {
            _uiState.update { it.copy(isListening = true, partialSpeech = "") }
            speechInput.listen().collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> _uiState.update { it.copy(partialSpeech = event.text) }

                    is SpeechEvent.Final -> _uiState.update {
                        val combined = (it.input.trim() + " " + event.text).trim()
                        it.copy(input = combined, partialSpeech = "", isListening = false)
                    }

                    is SpeechEvent.Failed -> _uiState.update {
                        it.copy(error = event.reason, isListening = false, partialSpeech = "")
                    }

                    else -> Unit
                }
            }
            _uiState.update { it.copy(isListening = false) }
        }
    }

    fun stopDictation() {
        dictationJob?.cancel()
        dictationJob = null
        _uiState.update { it.copy(isListening = false, partialSpeech = "") }
    }

    override fun onCleared() {
        generationJob?.cancel()
        dictationJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val PERSIST_EVERY_CHARS = 24
        const val DEFAULT_TITLE = "New conversation"
        const val DESCRIBE_IMAGE = "Describe what is in this image."
    }
}
