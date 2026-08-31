package nl.markmaaktmedia.markmaaktai.ui.models

import android.net.Uri
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.ai.ModelCatalog
import nl.markmaaktmedia.markmaaktai.ai.ModelRole
import nl.markmaaktmedia.markmaaktai.ai.ModelSpec
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import nl.markmaaktmedia.markmaaktai.data.repository.DownloadProgress
import nl.markmaaktmedia.markmaaktai.data.repository.InstalledModel
import nl.markmaaktmedia.markmaaktai.data.repository.ModelRepository
import javax.inject.Inject

data class ModelsUiState(
    val installed: List<InstalledModel> = emptyList(),
    val activeTextPath: String = "",
    val activeVisionPath: String = "",
    val activeSpeechPath: String = "",
    val freeBytes: Long = 0L,
    val message: String? = null,
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val repository: ModelRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    val downloads: StateFlow<Map<String, DownloadProgress>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val textCatalog: List<ModelSpec> = ModelCatalog.textModels
    val visionCatalog: List<ModelSpec> = ModelCatalog.visionModels
    val speechCatalog: List<ModelSpec> = ModelCatalog.speechModels

    init {
        refresh()
        viewModelScope.launch {
            settings.settings.collect { prefs ->
                _uiState.update {
                    it.copy(
                        activeTextPath = prefs.textModelPath,
                        activeVisionPath = prefs.visionModelPath,
                        activeSpeechPath = prefs.speechModelPath,
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(installed = repository.installed(), freeBytes = freeSpace())
            }
        }
    }

    /**
     * Downloads a model and makes it the active one for its role straight away.
     *
     * Downloading a model and then having to go and select it is a step nobody wants,
     * and the only reason to have downloaded it was to use it.
     */
    fun download(spec: ModelSpec) {
        // Handed to the repository so the transfer outlives this screen. The user
        // starting a download in onboarding and immediately pressing Next is the
        // normal case, not an edge one.
        repository.startDownload(spec) { result ->
            result.onSuccess { file ->
                repository.activate(
                    InstalledModel(
                        fileName = file.name,
                        path = file.absolutePath,
                        sizeBytes = file.length(),
                        role = spec.role,
                        isActive = true,
                    ),
                    spec.role,
                )
            }
            refresh()
        }
    }

    fun cancelDownload(spec: ModelSpec) {
        repository.cancelDownload(spec.id)
        refresh()
    }

    fun importFromUri(uri: Uri, role: ModelRole) {
        viewModelScope.launch {
            repository.importFromUri(uri, role)
                .onSuccess { model ->
                    repository.activate(model, role)
                    refresh()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message) }
                }
        }
    }

    fun activate(model: InstalledModel, role: ModelRole) {
        viewModelScope.launch {
            repository.activate(model, role)
            refresh()
        }
    }

    fun delete(model: InstalledModel) {
        viewModelScope.launch {
            repository.delete(model.path)
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun isInstalled(spec: ModelSpec): Boolean = repository.isInstalled(spec)

    /** Free space on the volume the models live on, so the warning is about the right disk. */
    private fun freeSpace(): Long = runCatching {
        val stat = StatFs(repository.modelsDir().absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(0L)
}
