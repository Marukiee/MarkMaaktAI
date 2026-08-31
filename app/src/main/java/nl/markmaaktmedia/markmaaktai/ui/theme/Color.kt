package nl.markmaaktmedia.markmaaktai.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The palette used when the user turns wallpaper colours off, or on a phone that
 * cannot provide them.
 *
 * Built around one idea: a cool indigo that reads as "thinking", with a warm rose as
 * the only other voice in the room. Two hues and nothing else, so the accent means
 * something when it does show up. The tilted capsule in the launcher icon is drawn in
 * the same indigo, which is what ties the icon to the app it opens.
 */
object MarkPalette {
    val Indigo10 = Color(0xFF0B0A2B)
    val Indigo20 = Color(0xFF1B1A4A)
    val Indigo30 = Color(0xFF2E2C6B)
    val Indigo40 = Color(0xFF454391)
    val Indigo50 = Color(0xFF5B5BD6)
    val Indigo70 = Color(0xFF9E9DF0)
    val Indigo80 = Color(0xFFBEBDF8)
    val Indigo90 = Color(0xFFE2E1FF)
    val Indigo95 = Color(0xFFF1F0FF)

    val Rose10 = Color(0xFF3A0720)
    val Rose20 = Color(0xFF561433)
    val Rose30 = Color(0xFF742C4B)
    val Rose40 = Color(0xFF934464)
    val Rose80 = Color(0xFFFFB0CB)
    val Rose90 = Color(0xFFFFD9E4)

    val Neutral0 = Color(0xFF000000)
    val Neutral6 = Color(0xFF0E0E13)
    val Neutral10 = Color(0xFF15141B)
    val Neutral12 = Color(0xFF1A1922)
    val Neutral17 = Color(0xFF232230)
    val Neutral22 = Color(0xFF2C2B3A)
    val Neutral24 = Color(0xFF302F3F)
    val Neutral80 = Color(0xFFC8C5D5)
    val Neutral90 = Color(0xFFE5E1F0)
    val Neutral95 = Color(0xFFF3F0FB)
    val Neutral98 = Color(0xFFFCFAFF)
    val Neutral100 = Color(0xFFFFFFFF)

    val Error40 = Color(0xFFBA1A1A)
    val Error80 = Color(0xFFFFB4AB)
    val Error90 = Color(0xFFFFDAD6)
    val Error10 = Color(0xFF410002)

    /** Reserved for the urgent badge, so nothing else is allowed to be this loud. */
    val Urgent = Color(0xFFE8613C)
    val UrgentContainerLight = Color(0xFFFFE3DA)
    val UrgentContainerDark = Color(0xFF5A2113)
}

val MarkLightColors = lightColorScheme(
    primary = MarkPalette.Indigo50,
    onPrimary = MarkPalette.Neutral100,
    primaryContainer = MarkPalette.Indigo90,
    onPrimaryContainer = MarkPalette.Indigo20,
    secondary = MarkPalette.Indigo40,
    onSecondary = MarkPalette.Neutral100,
    secondaryContainer = MarkPalette.Indigo95,
    onSecondaryContainer = MarkPalette.Indigo30,
    tertiary = MarkPalette.Rose40,
    onTertiary = MarkPalette.Neutral100,
    tertiaryContainer = MarkPalette.Rose90,
    onTertiaryContainer = MarkPalette.Rose20,
    background = MarkPalette.Neutral98,
    onBackground = MarkPalette.Neutral10,
    surface = MarkPalette.Neutral98,
    onSurface = MarkPalette.Neutral10,
    surfaceVariant = MarkPalette.Neutral95,
    onSurfaceVariant = Color(0xFF49475A),
    surfaceContainerLowest = MarkPalette.Neutral100,
    surfaceContainerLow = Color(0xFFFAF7FF),
    surfaceContainer = Color(0xFFF4F1FC),
    surfaceContainerHigh = Color(0xFFEEEBF7),
    surfaceContainerHighest = Color(0xFFE8E5F2),
    outline = Color(0xFF7B7890),
    outlineVariant = Color(0xFFCBC7DA),
    error = MarkPalette.Error40,
    onError = MarkPalette.Neutral100,
    errorContainer = MarkPalette.Error90,
    onErrorContainer = MarkPalette.Error10,
    inverseSurface = MarkPalette.Neutral17,
    inverseOnSurface = MarkPalette.Neutral95,
    inversePrimary = MarkPalette.Indigo80,
)

val MarkDarkColors = darkColorScheme(
    primary = MarkPalette.Indigo80,
    onPrimary = MarkPalette.Indigo20,
    primaryContainer = MarkPalette.Indigo30,
    onPrimaryContainer = MarkPalette.Indigo90,
    secondary = MarkPalette.Indigo70,
    onSecondary = MarkPalette.Indigo20,
    secondaryContainer = MarkPalette.Indigo30,
    onSecondaryContainer = MarkPalette.Indigo90,
    tertiary = MarkPalette.Rose80,
    onTertiary = MarkPalette.Rose20,
    tertiaryContainer = MarkPalette.Rose30,
    onTertiaryContainer = MarkPalette.Rose90,
    background = MarkPalette.Neutral10,
    onBackground = MarkPalette.Neutral90,
    surface = MarkPalette.Neutral10,
    onSurface = MarkPalette.Neutral90,
    surfaceVariant = MarkPalette.Neutral22,
    onSurfaceVariant = MarkPalette.Neutral80,
    surfaceContainerLowest = MarkPalette.Neutral6,
    surfaceContainerLow = MarkPalette.Neutral12,
    surfaceContainer = MarkPalette.Neutral17,
    surfaceContainerHigh = MarkPalette.Neutral22,
    surfaceContainerHighest = MarkPalette.Neutral24,
    outline = Color(0xFF908DA3),
    outlineVariant = Color(0xFF49475A),
    error = MarkPalette.Error80,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = MarkPalette.Error90,
    inverseSurface = MarkPalette.Neutral90,
    inverseOnSurface = MarkPalette.Neutral17,
    inversePrimary = MarkPalette.Indigo50,
)
