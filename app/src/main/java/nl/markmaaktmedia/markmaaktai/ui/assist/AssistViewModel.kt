package nl.markmaaktmedia.markmaaktai.ui.assist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.ai.AiOrchestrator
import nl.markmaaktmedia.markmaaktai.ai.InferenceEvent
import nl.markmaaktmedia.markmaaktai.ai.prompt.PromptTurn
import nl.markmaaktmedia.markmaaktai.ai.stt.SpeechEvent
import nl.markmaaktmedia.markmaaktai.ai.stt.SpeechInputManager
import javax.inject.Inject

/**
 * Drives the assist sheet when it is opened as a normal activity.
 *
 * This is the path taken on a phone where MarkMaaktAI is not the default assistant,
 * or where the assist gesture routes through ACTION_ASSIST instead. There is no
 * screenshot and no assist structure on this route, so the sheet is a quick question
 * box and says nothing about reading the screen.
 */
@HiltViewModel
class AssistViewModel @Inject constructor(
    private val orchestrator: AiOrchestrator,
    private val speechInput: SpeechInputManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistUiState())
    val state: StateFlow<AssistUiState> = _state.asStateFlow()

    /** The exchange so far, so a follow-up knows what it is following up on. */
    private val turns = mutableListOf<PromptTurn>()

    private var answerJob: Job? = null
    private var dictationJob: Job? = null

    init {
        // Same reason as the voice session: the sheet is opened to say something, so
        // it starts listening. Only when there is something to listen with, though,
        // or the first thing the user sees is a failure they did not cause.
        viewModelScope.launch {
            val canListen = speechInput.hasMicrophonePermission() &&
                (speechInput.hasOfflineModel() || speechInput.hasSystemRecogniser())
            if (canListen) toggleDictation()
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value, error = null) }
    }

    fun ask() {
        val question = _state.value.query.trim()
        if (question.isEmpty() || _state.value.isAnswering) return

        answerJob?.cancel()
        answerJob = viewModelScope.launch {
            _state.update {
                it.copy(isAnswering = true, answer = "", askedQuestion = question, query = "", error = null)
            }
            val builder = StringBuilder()
            // Carries the exchange so far, so a follow-up in the sheet is a follow-up
            // and not a fresh question about nothing.
            val history = turns.toList()
            try {
                orchestrator.chat(history = history, question = question).collect { event ->
                    when (event) {
                        is InferenceEvent.Token -> {
                            builder.append(event.text)
                            _state.update { it.copy(answer = builder.toString()) }
                        }

                        is InferenceEvent.Completed ->
                            _state.update { it.copy(answer = event.text.ifBlank { builder.toString() }) }

                        is InferenceEvent.Failed -> _state.update { it.copy(error = event.message) }
                        else -> Unit
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "Answering failed", error)
                _state.update { it.copy(error = error.message ?: "Something went wrong") }
            } finally {
                val answer = _state.value.answer
                if (answer.isNotBlank()) {
                    turns += PromptTurn(PromptTurn.Role.USER, question)
                    turns += PromptTurn(PromptTurn.Role.ASSISTANT, answer)
                    // Two exchanges of memory. The sheet is for a question and a
                    // follow-up; anything longer belongs in the app, where the drag
                    // handle puts it.
                    while (turns.size > MAX_TURNS) turns.removeAt(0)
                }
                _state.update { it.copy(isAnswering = false) }
            }
        }
    }

    fun beginClose() {
        _state.update { it.copy(closing = true) }
    }

    fun toggleDictation() {
        if (_state.value.isListening) {
            dictationJob?.cancel()
            _state.update { it.copy(isListening = false) }
            return
        }
        dictationJob = viewModelScope.launch {
            _state.update { it.copy(isListening = true) }
            speechInput.listen().collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> _state.update { it.copy(query = event.text) }
                    is SpeechEvent.Final -> {
                        _state.update { it.copy(query = event.text, isListening = false) }
                        ask()
                    }

                    is SpeechEvent.Failed ->
                        _state.update { it.copy(error = event.reason, isListening = false) }

                    else -> Unit
                }
            }
            _state.update { it.copy(isListening = false) }
        }
    }

    private companion object {
        const val TAG = "AssistViewModel"
        const val MAX_TURNS = 4
    }

    override fun onCleared() {
        answerJob?.cancel()
        dictationJob?.cancel()
        super.onCleared()
    }
}
