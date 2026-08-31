package nl.markmaaktmedia.markmaaktai.ui.shots

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.data.db.ScreenshotEntity
import nl.markmaaktmedia.markmaaktai.data.repository.IndexProgress
import nl.markmaaktmedia.markmaaktai.data.repository.ScreenshotRepository
import nl.markmaaktmedia.markmaaktai.service.screenshots.ScreenshotIndexWorker
import javax.inject.Inject

data class ShotsUiState(
    val query: String = "",
    val category: String? = null,
    val hasPermission: Boolean = false,
    val isIndexing: Boolean = false,
    val progress: IndexProgress = IndexProgress(0, 0),
    val searchResults: List<ScreenshotEntity>? = null,
    val openedId: Long? = null,
)

@HiltViewModel
class ShotsViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
    private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShotsUiState(hasPermission = repository.hasMediaPermission()))
    val uiState: StateFlow<ShotsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /** Everything indexed, filtered by the category chip. Search replaces this list. */
    val shots: StateFlow<List<ScreenshotEntity>> =
        combine(repository.observeAll(), _uiState) { all, state ->
            state.searchResults ?: state.category?.let { category ->
                all.filter { it.category == category }
            } ?: all
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val indexedCount: StateFlow<Int> = repository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) scan()
    }

    fun refreshPermission() {
        _uiState.update { it.copy(hasPermission = repository.hasMediaPermission()) }
    }

    /**
     * Runs the scan in the foreground so the user watching the screen sees progress,
     * and hands the model pass to the worker so it can wait for a charger.
     */
    fun scan() {
        if (_uiState.value.isIndexing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isIndexing = true) }
            repository.indexNew { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
            repository.pruneMissing()
            _uiState.update { it.copy(isIndexing = false, progress = IndexProgress(0, 0)) }
            ScreenshotIndexWorker.runNow(context, withModel = true)
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        if (value.isBlank()) {
            _uiState.update { it.copy(searchResults = null) }
            return
        }
        searchJob = viewModelScope.launch {
            // Short pause so a fast typist does not run a query per keystroke.
            kotlinx.coroutines.delay(220)
            val results = repository.search(value)
            _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun setCategory(category: String?) {
        _uiState.update { it.copy(category = category) }
    }

    fun open(id: Long?) {
        _uiState.update { it.copy(openedId = id) }
    }

    fun toggleFavourite(shot: ScreenshotEntity) {
        viewModelScope.launch { repository.setFavourite(shot.id, !shot.isFavourite) }
    }

    fun forget(shot: ScreenshotEntity) {
        viewModelScope.launch { repository.forget(shot.id) }
    }
}
