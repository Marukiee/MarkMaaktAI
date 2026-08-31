package nl.markmaaktmedia.markmaaktai.ui.digest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.data.db.CapturedNotificationEntity
import nl.markmaaktmedia.markmaaktai.data.db.SummaryEntity
import nl.markmaaktmedia.markmaaktai.data.repository.NotificationRepository
import javax.inject.Inject

enum class DigestFilter { All, Urgent, Unread }

data class DigestUiState(
    val filter: DigestFilter = DigestFilter.All,
    val expandedId: Long? = null,
    val sources: List<CapturedNotificationEntity> = emptyList(),
    val lastDeleted: SummaryEntity? = null,
)

@HiltViewModel
class DigestViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DigestUiState())
    val uiState: StateFlow<DigestUiState> = _uiState.asStateFlow()

    val summaries: StateFlow<List<SummaryEntity>> =
        combine(repository.observeSummaries(), _uiState) { all, state ->
            when (state.filter) {
                DigestFilter.All -> all
                DigestFilter.Urgent -> all.filter { it.isUrgent }
                DigestFilter.Unread -> all.filter { !it.isRead }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> = repository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setFilter(filter: DigestFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    /** Opening a card marks it read and pulls in the messages it was built from. */
    fun toggleExpanded(summaryId: Long) {
        val alreadyOpen = _uiState.value.expandedId == summaryId
        if (alreadyOpen) {
            _uiState.update { it.copy(expandedId = null, sources = emptyList()) }
            return
        }
        _uiState.update { it.copy(expandedId = summaryId, sources = emptyList()) }
        viewModelScope.launch {
            repository.markRead(summaryId)
            val sources = repository.sourcesFor(summaryId)
            if (_uiState.value.expandedId == summaryId) {
                _uiState.update { it.copy(sources = sources) }
            }
        }
    }

    fun delete(summary: SummaryEntity) {
        viewModelScope.launch {
            repository.deleteSummary(summary.id)
            _uiState.update { it.copy(lastDeleted = summary) }
        }
    }

    fun undoDelete() {
        val summary = _uiState.value.lastDeleted ?: return
        viewModelScope.launch {
            repository.restoreSummary(summary)
            _uiState.update { it.copy(lastDeleted = null) }
        }
    }

    fun clearUndo() {
        _uiState.update { it.copy(lastDeleted = null) }
    }
}
