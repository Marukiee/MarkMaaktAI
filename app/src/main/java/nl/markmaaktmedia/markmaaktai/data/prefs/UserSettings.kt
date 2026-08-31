package nl.markmaaktmedia.markmaaktai.data.prefs

/** Which of the three theme modes the user picked. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Everything the user can tune. Defaults are chosen so a fresh install behaves
 * well on a phone with no model yet and no network permission granted.
 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = true,

    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val useGpu: Boolean = true,

    val webSearchEnabled: Boolean = false,
    val searxngUrl: String = "",
    val braveApiKey: String = "",
    val searchResultCount: Int = 4,

    val notificationIntelligence: Boolean = true,
    val minWordCount: Int = 5,
    val clusterSize: Int = 3,
    val clusterWindowMinutes: Int = 3,
    val longEmailWordCount: Int = 60,
    val excludedPackages: Set<String> = emptySet(),
    val urgentAlerts: Boolean = true,
    val retentionDays: Int = 30,

    val textModelPath: String = "",
    val visionModelPath: String = "",
    val speechModelPath: String = "",

    val onboardingDone: Boolean = false,
    val lastUpdateCheck: Long = 0L,
    val skippedUpdateTag: String = "",
)
