package nl.markmaaktmedia.markmaaktai.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
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
import nl.markmaaktmedia.markmaaktai.BuildConfig
import nl.markmaaktmedia.markmaaktai.data.prefs.ColourSeedSetting
import nl.markmaaktmedia.markmaaktai.data.prefs.PaletteStyleSetting
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import nl.markmaaktmedia.markmaaktai.data.prefs.ThemeMode
import nl.markmaaktmedia.markmaaktai.data.prefs.UserSettings
import nl.markmaaktmedia.markmaaktai.service.notifications.MarkNotificationListenerService
import nl.markmaaktmedia.markmaaktai.update.UpdateRepository
import nl.markmaaktmedia.markmaaktai.update.UpdateState
import nl.markmaaktmedia.markmaaktai.util.CrashReporter
import javax.inject.Inject

/** Which system level permissions are in place. Re-read every time the screen shows. */
data class SystemAccess(
    val notificationListener: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val isDefaultAssistant: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val context: Context,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    val userSettings: StateFlow<UserSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _access = MutableStateFlow(SystemAccess())
    val access: StateFlow<SystemAccess> = _access.asStateFlow()

    val updateState: StateFlow<UpdateState> = updateRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateState.Idle)

    val versionName: String = BuildConfig.VERSION_NAME

    private val _crashReport = MutableStateFlow<String?>(null)

    /** The stored crash, once the user asks to see it. */
    val crashReport: StateFlow<String?> = _crashReport.asStateFlow()

    val hasCrashReport: Boolean get() = crashReporter.lastCrash() != null

    fun showLastCrash() {
        _crashReport.value = crashReporter.lastCrash() ?: NO_CRASH
    }

    fun dismissCrashReport() {
        _crashReport.value = null
    }

    fun clearCrashReport() {
        crashReporter.clear()
        _crashReport.value = null
    }

    init {
        refreshAccess()
    }

    /**
     * Reads the three switches that live in system settings rather than in the app.
     * They can change while the app is in the background, so this runs on every
     * resume rather than being cached.
     */
    fun refreshAccess() {
        _access.value = SystemAccess(
            notificationListener = isNotificationListenerEnabled(),
            batteryUnrestricted = isIgnoringBatteryOptimisations(),
            isDefaultAssistant = isDefaultAssistant(),
        )
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { settings.setDynamicColor(on) }
    fun setPureBlack(on: Boolean) = viewModelScope.launch { settings.setPureBlack(on) }
    fun setPaletteStyle(style: PaletteStyleSetting) = viewModelScope.launch { settings.setPaletteStyle(style) }
    fun setColourSeed(seed: ColourSeedSetting) = viewModelScope.launch {
        settings.setColourSeed(seed)
        // The wallpaper swatch and the dynamic colour switch are the same decision,
        // so picking one keeps the other honest instead of leaving a switch that
        // claims to be on while a fixed seed is in use.
        settings.setDynamicColor(seed == ColourSeedSetting.WALLPAPER)
    }
    fun setTemperature(value: Float) = viewModelScope.launch { settings.setTemperature(value) }
    fun setMaxTokens(value: Int) = viewModelScope.launch { settings.setMaxTokens(value) }
    fun setUseGpu(on: Boolean) = viewModelScope.launch { settings.setUseGpu(on) }
    fun setSearxngUrl(url: String) = viewModelScope.launch { settings.setSearxngUrl(url) }
    fun setBraveApiKey(key: String) = viewModelScope.launch { settings.setBraveApiKey(key) }
    fun setSearchResultCount(count: Int) = viewModelScope.launch { settings.setSearchResultCount(count) }
    fun setNotificationIntelligence(on: Boolean) =
        viewModelScope.launch { settings.setNotificationIntelligence(on) }

    fun setMinWordCount(value: Int) = viewModelScope.launch { settings.setMinWordCount(value) }
    fun setClusterSize(value: Int) = viewModelScope.launch { settings.setClusterSize(value) }
    fun setClusterWindow(value: Int) = viewModelScope.launch { settings.setClusterWindowMinutes(value) }
    fun setUrgentAlerts(on: Boolean) = viewModelScope.launch { settings.setUrgentAlerts(on) }
    fun setRetentionDays(value: Int) = viewModelScope.launch { settings.setRetentionDays(value) }

    fun checkForUpdates() {
        viewModelScope.launch {
            updateRepository.check()
            settings.setLastUpdateCheck(System.currentTimeMillis())
        }
    }

    fun downloadUpdate() {
        val state = updateState.value
        val release = (state as? UpdateState.Available)?.release ?: return
        viewModelScope.launch { updateRepository.download(release) }
    }

    fun installUpdate() {
        val state = updateState.value
        if (state is UpdateState.ReadyToInstall) updateRepository.install(state.filePath)
    }

    fun dismissUpdate() = updateRepository.reset()

    val releasesUrl: String get() = updateRepository.releasesPageUrl

    val repositoryUrl: String
        get() = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}"

    /**
     * Checks once when the screen is opened, but only if nothing has been checked
     * recently. Without this the update row sits there saying nothing until the next
     * cold start, which reads as the feature not being wired up at all.
     */
    fun checkOnOpen() {
        viewModelScope.launch {
            val last = settings.current().lastUpdateCheck
            if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return@launch
            checkForUpdates()
        }
    }

    // Intents that open the system screens. Each one is wrapped because a hardened
    // ROM can simply not have the screen, and a crash there would be absurd.

    fun openNotificationAccess() = launchSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun openAssistantSettings() = launchSettings(Settings.ACTION_VOICE_INPUT_SETTINGS)

    /**
     * Asks to be left alone by the battery optimiser. Sony and GrapheneOS are strict
     * enough that the notification reader gets killed without it, which makes the
     * whole background feature look broken rather than restricted.
     */
    fun requestBatteryExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { launchSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun launchSettings(action: String) {
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val component = ComponentName(context, MarkNotificationListenerService::class.java)
        return flat.split(':').any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    private fun isIgnoringBatteryOptimisations(): Boolean {
        val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return manager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    private companion object {
        /** Six hours, so opening settings a few times in a row is not six requests. */
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val NO_CRASH = "No crash has been recorded."
    }

    private fun isDefaultAssistant(): Boolean {
        val assistant = Settings.Secure.getString(context.contentResolver, "assistant").orEmpty()
        return assistant.contains(context.packageName)
    }
}
