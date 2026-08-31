package nl.markmaaktmedia.markmaaktai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import nl.markmaaktmedia.markmaaktai.data.prefs.ThemeMode

/** Extra colours Material does not have a slot for. */
data class MarkExtraColors(
    val urgent: Color,
    val urgentContainer: Color,
    val onUrgentContainer: Color,
    /** True when the pure black surface is in use, so a component can skip its elevation tint. */
    val isPureBlack: Boolean,
)

val LocalMarkExtraColors = staticCompositionLocalOf {
    MarkExtraColors(
        urgent = MarkPalette.Urgent,
        urgentContainer = MarkPalette.UrgentContainerLight,
        onUrgentContainer = MarkPalette.Neutral10,
        isPureBlack = false,
    )
}

@Composable
fun MarkTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    pureBlack: Boolean = true,
    /** The assist overlay draws over another app, so it never paints system bars. */
    applySystemBarStyle: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> MarkDarkColors
        else -> MarkLightColors
    }

    val usePureBlack = dark && pureBlack
    val targetScheme = if (usePureBlack) baseScheme.toPureBlack() else baseScheme

    // Every role is animated, so switching theme, flipping pure black or changing the
    // wallpaper crossfades the whole app instead of swapping it in one frame. This is
    // the single change that makes the light and dark switch feel considered.
    val scheme = targetScheme.animated()

    val extras = MarkExtraColors(
        urgent = if (dark) MarkPalette.Urgent else MarkPalette.Urgent,
        urgentContainer = if (dark) MarkPalette.UrgentContainerDark else MarkPalette.UrgentContainerLight,
        onUrgentContainer = if (dark) MarkPalette.Neutral95 else MarkPalette.Neutral10,
        isPureBlack = usePureBlack,
    )

    if (applySystemBarStyle) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    CompositionLocalProvider(LocalMarkExtraColors provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MarkTypography,
            shapes = MarkShapes,
            content = content,
        )
    }
}

/**
 * Pushes the dark scheme down to true black.
 *
 * Not a blanket black: only the backdrop goes to #000000, and the container roles are
 * kept as a short ladder of very dark greys. An OLED screen wins on the large flat
 * areas, and cards still have an edge you can see, which is the part most pure black
 * modes get wrong by painting everything the same colour and losing all depth.
 */
private fun ColorScheme.toPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0D),
    surfaceContainer = Color(0xFF121216),
    surfaceContainerHigh = Color(0xFF1A1A20),
    surfaceContainerHighest = Color(0xFF23232A),
    surfaceVariant = Color(0xFF1A1A20),
    outlineVariant = Color(0xFF33333D),
)

@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = MarkMotion.colourSpec<Color>()

    @Composable
    fun animate(target: Color, label: String) =
        animateColorAsState(targetValue = target, animationSpec = spec, label = label).value

    return copy(
        primary = animate(primary, "primary"),
        onPrimary = animate(onPrimary, "onPrimary"),
        primaryContainer = animate(primaryContainer, "primaryContainer"),
        onPrimaryContainer = animate(onPrimaryContainer, "onPrimaryContainer"),
        secondary = animate(secondary, "secondary"),
        onSecondary = animate(onSecondary, "onSecondary"),
        secondaryContainer = animate(secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = animate(onSecondaryContainer, "onSecondaryContainer"),
        tertiary = animate(tertiary, "tertiary"),
        onTertiary = animate(onTertiary, "onTertiary"),
        tertiaryContainer = animate(tertiaryContainer, "tertiaryContainer"),
        onTertiaryContainer = animate(onTertiaryContainer, "onTertiaryContainer"),
        background = animate(background, "background"),
        onBackground = animate(onBackground, "onBackground"),
        surface = animate(surface, "surface"),
        onSurface = animate(onSurface, "onSurface"),
        surfaceVariant = animate(surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = animate(onSurfaceVariant, "onSurfaceVariant"),
        surfaceContainerLowest = animate(surfaceContainerLowest, "containerLowest"),
        surfaceContainerLow = animate(surfaceContainerLow, "containerLow"),
        surfaceContainer = animate(surfaceContainer, "container"),
        surfaceContainerHigh = animate(surfaceContainerHigh, "containerHigh"),
        surfaceContainerHighest = animate(surfaceContainerHighest, "containerHighest"),
        outline = animate(outline, "outline"),
        outlineVariant = animate(outlineVariant, "outlineVariant"),
        inverseSurface = animate(inverseSurface, "inverseSurface"),
        inverseOnSurface = animate(inverseOnSurface, "inverseOnSurface"),
        inversePrimary = animate(inversePrimary, "inversePrimary"),
        error = animate(error, "error"),
        onError = animate(onError, "onError"),
        errorContainer = animate(errorContainer, "errorContainer"),
        onErrorContainer = animate(onErrorContainer, "onErrorContainer"),
    )
}
