package nl.markmaaktmedia.markmaaktai.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries a question from one tab to the chat.
 *
 * "Ask about this" on a summary or a screenshot has to switch tab and arrive with the
 * question already in hand. Passing it as a navigation argument would put a paragraph
 * of screenshot text in the back stack, so it is handed over here and cleared as soon
 * as the chat has taken it.
 */
@Singleton
class ChatHandoff @Inject constructor() {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(question: String) {
        _pending.value = question
    }

    fun consume(): String? = _pending.value?.also { _pending.value = null }

    fun clear() {
        _pending.value = null
    }
}
