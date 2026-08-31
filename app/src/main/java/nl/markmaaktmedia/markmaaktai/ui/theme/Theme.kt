package nl.markmaaktmedia.markmaaktai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import nl.markmaaktmedia.markmaaktai.data.prefs.ColourSeedSetting
import nl.markmaaktmedia.markmaaktai.data.prefs.PaletteStyleSetting
import nl.markmaaktmedia.markmaaktai.data.prefs.ThemeMode

/** Extra colours Material does not have a slot for. */
data class MarkExtraColors(
    val urgent: Color,
    val urgentContainer: Color,
    val onUrgentContainer: Color,
    /** True when the pure black surface is in use, so a component can skip its tint. */
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
    paletteStyle: PaletteStyleSetting = PaletteStyleSetting.TONAL_SPOT,
    colourSeed: ColourSeedSetting = ColourSeedSetting.WALLPAPER,
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

    /*
     * The seed is read as a raw colour and the scheme is generated here rather than
     * taken from dynamicLightColorScheme. That call always produces Tonal Spot, so
     * going through the generator is what lets the palette style actually be a
     * setting instead of a label on a fixed palette.
     */
    val seed: Color = when {
        colourSeed != ColourSeedSetting.WALLPAPER ->
            seedColorFor(colourSeed)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            Color(context.getColor(android.R.color.system_accent1_500))

        else -> MarkPalette.Indigo50
    }

    val generated = buildColorScheme(
        seed = seed,
        style = paletteStyle.toPaletteStyle(),
        dark = dark,
    )

    val usePureBlack = dark && pureBlack
    val targetScheme = if (usePureBlack) generated.toPureBlack() else generated

    // Every role is animated, so switching theme, flipping pure black or picking a
    // different palette crossfades the whole app instead of swapping it in one frame.
    val scheme = targetScheme.animated()

    val extras = MarkExtraColors(
        urgent = MarkPalette.Urgent,
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

private fun PaletteStyleSetting.toPaletteStyle(): PaletteStyle = when (this) {
    PaletteStyleSetting.TONAL_SPOT -> PaletteStyle.TONAL_SPOT
    PaletteStyleSetting.VIBRANT -> PaletteStyle.VIBRANT
    PaletteStyleSetting.EXPRESSIVE -> PaletteStyle.EXPRESSIVE
    PaletteStyleSetting.FRUIT_SALAD -> PaletteStyle.FRUIT_SALAD
    PaletteStyleSetting.FIDELITY -> PaletteStyle.FIDELITY
    PaletteStyleSetting.MONOCHROME -> PaletteStyle.MONOCHROME
}

fun seedColorFor(setting: ColourSeedSetting): Color =
    ColourSeed.fromKey(setting.storageKey).seed

/**
 * Pushes the dark scheme down to true black.
 *
 * Not a blanket black: only the backdrop goes to #000000, and the container roles are
 * kept as a short ladder of very dark greys tinted towards the seed. An OLED screen
 * wins on the large flat areas, and cards still have an edge you can see, which is
 * what most pure black modes lose by painting everything the same colour.
 */
private fun ColorScheme.toPureBlack(): ColorScheme {
    fun tinted(alpha: Float) = surfaceContainerHighest.copy(alpha = alpha).compositeOverBlack()
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = tinted(0.05f),
        surfaceContainer = tinted(0.09f),
        surfaceContainerHigh = tinted(0.14f),
        surfaceContainerHighest = tinted(0.19f),
        surfaceVariant = tinted(0.12f),
        outlineVariant = tinted(0.26f),
    )
}

/** Flattens a translucent colour onto black, so the result is opaque. */
private fun Color.compositeOverBlack(): Color =
    Color(red = red * alpha, green = green * alpha, blue = blue * alpha, alpha = 1f)

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
