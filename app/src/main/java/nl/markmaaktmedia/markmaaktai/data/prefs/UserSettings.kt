package nl.markmaaktmedia.markmaaktai.data.prefs

/** Which of the three theme modes the user picked. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Mirrors the palette style and seed choices from the theme layer.
 *
 * Kept as their own enums here so the preferences layer does not depend on anything
 * in the UI package, which is what lets a background worker read settings without
 * dragging Compose in with it.
 */
enum class PaletteStyleSetting(val storageKey: String) {
    TONAL_SPOT("tonal_spot"),
    VIBRANT("vibrant"),
    EXPRESSIVE("expressive"),
    FRUIT_SALAD("fruit_salad"),
    FIDELITY("fidelity"),
    MONOCHROME("monochrome");

    companion object {
        fun fromKey(value: String?): PaletteStyleSetting =
            entries.firstOrNull { it.storageKey == value } ?: TONAL_SPOT
    }
}

enum class ColourSeedSetting(val storageKey: String) {
    WALLPAPER("wallpaper"),
    INDIGO("indigo"),
    OCEAN("ocean"),
    FOREST("forest"),
    AMBER("amber"),
    ROSE("rose"),
    PLUM("plum"),
    SLATE("slate");

    companion object {
        fun fromKey(value: String?): ColourSeedSetting =
            entries.firstOrNull { it.storageKey == value } ?: WALLPAPER
    }
}

/**
 * Everything the user can tune. Defaults are chosen so a fresh install behaves
 * well on a phone with no model yet and no network permission granted.
 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = true,
    val paletteStyle: PaletteStyleSetting = PaletteStyleSetting.TONAL_SPOT,
    val colourSeed: ColourSeedSetting = ColourSeedSetting.WALLPAPER,

    val temperature: Float = 0.7f,
    /** Answer budget. Capped against the model's own KV cache when it is loaded. */
    val maxTokens: Int = 640,
    val useGpu: Boolean = true,

    val webSearchEnabled: Boolean = false,
    val searxngUrl: String = "",
    val braveApiKey: String = "",
    val searchResultCount: Int = 4,

    /**
     * Look up where a photo was taken, using the coordinates it carries.
     *
     * Only the coordinates leave the phone, and only when a photo has them. It is on
     * by default because "where is this?" is the first thing anyone asks a photo, and
     * the alternative is a small model guessing at a place name.
     */
    val photoPlaceLookup: Boolean = true,

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
