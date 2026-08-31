package nl.markmaaktmedia.markmaaktai.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import nl.markmaaktmedia.markmaaktai.update.UpdateRepository
import nl.markmaaktmedia.markmaaktai.update.UpdateState
import javax.inject.Inject

@HiltViewModel
class UpdateBannerViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<UpdateState> = updateRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateState.Idle)

    /** The version the user said no to, so the banner does not ask again for it. */
    val dismissedTag: StateFlow<String> = settings.settings
        .map { it.skippedUpdateTag }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val currentVersion: String get() = updateRepository.currentVersion

    /**
     * Whether Android will let this app hand an APK to the installer. Checked so the
     * banner can say what is missing up front, rather than the download finishing and
     * nothing appearing to happen.
     */
    val canInstall: Boolean get() = updateRepository.canRequestInstalls()

    fun downloadAndInstall() {
        val release = when (val current = state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.ReadyToInstall -> current.release
            else -> null
        } ?: return

        if (!updateRepository.canRequestInstalls()) {
            updateRepository.openInstallPermissionSettings()
            return
        }
        viewModelScope.launch { updateRepository.downloadAndInstall(release) }
    }

    fun install(filePath: String) = updateRepository.install(filePath)

    fun dismiss(tag: String) {
        viewModelScope.launch {
            settings.setSkippedUpdateTag(tag)
            updateRepository.reset()
        }
    }
}
