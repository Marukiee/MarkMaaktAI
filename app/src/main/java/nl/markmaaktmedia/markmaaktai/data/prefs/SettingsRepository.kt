package nl.markmaaktmedia.markmaaktai.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "markmaaktai_settings")

@Singleton
class SettingsRepository @Inject constructor(
    private val context: Context,
) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val pureBlack = booleanPreferencesKey("pure_black")

        val temperature = floatPreferencesKey("temperature")
        val maxTokens = intPreferencesKey("max_tokens")
        val useGpu = booleanPreferencesKey("use_gpu")

        val webSearchEnabled = booleanPreferencesKey("web_search_enabled")
        val searxngUrl = stringPreferencesKey("searxng_url")
        val braveApiKey = stringPreferencesKey("brave_api_key")
        val searchResultCount = intPreferencesKey("search_result_count")

        val notificationIntelligence = booleanPreferencesKey("notification_intelligence")
        val minWordCount = intPreferencesKey("min_word_count")
        val clusterSize = intPreferencesKey("cluster_size")
        val clusterWindow = intPreferencesKey("cluster_window_minutes")
        val longEmailWordCount = intPreferencesKey("long_email_word_count")
        val excludedPackages = stringSetPreferencesKey("excluded_packages")
        val urgentAlerts = booleanPreferencesKey("urgent_alerts")
        val retentionDays = intPreferencesKey("retention_days")

        val textModelPath = stringPreferencesKey("text_model_path")
        val visionModelPath = stringPreferencesKey("vision_model_path")
        val speechModelPath = stringPreferencesKey("speech_model_path")

        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val skippedUpdateTag = stringPreferencesKey("skipped_update_tag")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            themeMode = prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
            pureBlack = prefs[Keys.pureBlack] ?: defaults.pureBlack,
            temperature = prefs[Keys.temperature] ?: defaults.temperature,
            maxTokens = prefs[Keys.maxTokens] ?: defaults.maxTokens,
            useGpu = prefs[Keys.useGpu] ?: defaults.useGpu,
            webSearchEnabled = prefs[Keys.webSearchEnabled] ?: defaults.webSearchEnabled,
            searxngUrl = prefs[Keys.searxngUrl] ?: defaults.searxngUrl,
            braveApiKey = prefs[Keys.braveApiKey] ?: defaults.braveApiKey,
            searchResultCount = prefs[Keys.searchResultCount] ?: defaults.searchResultCount,
            notificationIntelligence = prefs[Keys.notificationIntelligence] ?: defaults.notificationIntelligence,
            minWordCount = prefs[Keys.minWordCount] ?: defaults.minWordCount,
            clusterSize = prefs[Keys.clusterSize] ?: defaults.clusterSize,
            clusterWindowMinutes = prefs[Keys.clusterWindow] ?: defaults.clusterWindowMinutes,
            longEmailWordCount = prefs[Keys.longEmailWordCount] ?: defaults.longEmailWordCount,
            excludedPackages = prefs[Keys.excludedPackages] ?: defaults.excludedPackages,
            urgentAlerts = prefs[Keys.urgentAlerts] ?: defaults.urgentAlerts,
            retentionDays = prefs[Keys.retentionDays] ?: defaults.retentionDays,
            textModelPath = prefs[Keys.textModelPath] ?: defaults.textModelPath,
            visionModelPath = prefs[Keys.visionModelPath] ?: defaults.visionModelPath,
            speechModelPath = prefs[Keys.speechModelPath] ?: defaults.speechModelPath,
            onboardingDone = prefs[Keys.onboardingDone] ?: defaults.onboardingDone,
            lastUpdateCheck = prefs[Keys.lastUpdateCheck] ?: defaults.lastUpdateCheck,
            skippedUpdateTag = prefs[Keys.skippedUpdateTag] ?: defaults.skippedUpdateTag,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = put { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = put { it[Keys.dynamicColor] = enabled }
    suspend fun setPureBlack(enabled: Boolean) = put { it[Keys.pureBlack] = enabled }

    suspend fun setTemperature(value: Float) = put { it[Keys.temperature] = value }
    suspend fun setMaxTokens(value: Int) = put { it[Keys.maxTokens] = value }
    suspend fun setUseGpu(enabled: Boolean) = put { it[Keys.useGpu] = enabled }

    suspend fun setWebSearchEnabled(enabled: Boolean) = put { it[Keys.webSearchEnabled] = enabled }
    suspend fun setSearxngUrl(url: String) = put { it[Keys.searxngUrl] = url }
    suspend fun setBraveApiKey(key: String) = put { it[Keys.braveApiKey] = key }
    suspend fun setSearchResultCount(count: Int) = put { it[Keys.searchResultCount] = count }

    suspend fun setNotificationIntelligence(enabled: Boolean) = put { it[Keys.notificationIntelligence] = enabled }
    suspend fun setMinWordCount(value: Int) = put { it[Keys.minWordCount] = value }
    suspend fun setClusterSize(value: Int) = put { it[Keys.clusterSize] = value }
    suspend fun setClusterWindowMinutes(value: Int) = put { it[Keys.clusterWindow] = value }
    suspend fun setUrgentAlerts(enabled: Boolean) = put { it[Keys.urgentAlerts] = enabled }
    suspend fun setRetentionDays(value: Int) = put { it[Keys.retentionDays] = value }

    suspend fun toggleExcludedPackage(packageName: String) = put { prefs ->
        val current = prefs[Keys.excludedPackages] ?: emptySet()
        prefs[Keys.excludedPackages] =
            if (packageName in current) current - packageName else current + packageName
    }

    suspend fun setTextModelPath(path: String) = put { it[Keys.textModelPath] = path }
    suspend fun setVisionModelPath(path: String) = put { it[Keys.visionModelPath] = path }
    suspend fun setSpeechModelPath(path: String) = put { it[Keys.speechModelPath] = path }

    suspend fun setOnboardingDone(done: Boolean) = put { it[Keys.onboardingDone] = done }
    suspend fun setLastUpdateCheck(at: Long) = put { it[Keys.lastUpdateCheck] = at }
    suspend fun setSkippedUpdateTag(tag: String) = put { it[Keys.skippedUpdateTag] = tag }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
