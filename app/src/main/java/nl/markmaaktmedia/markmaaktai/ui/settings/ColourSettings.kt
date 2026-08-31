package nl.markmaaktmedia.markmaaktai.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.data.prefs.ColourSeedSetting
import nl.markmaaktmedia.markmaaktai.data.prefs.PaletteStyleSetting
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
import nl.markmaaktmedia.markmaaktai.ui.theme.buildColorScheme
import nl.markmaaktmedia.markmaaktai.ui.theme.seedColorFor

/**
 * Picking where the accent colour comes from.
 *
 * The wallpaper swatch is first and shows the colour the system actually reports, so
 * it is not a guess about what Material You will do. Every other swatch is drawn in
 * its own colour rather than labelled, because a row of names for colours is the one
 * thing worse than a row of colours.
 */
@Composable
fun ColourSeedRow(
    selected: ColourSeedSetting,
    onSelect: (ColourSeedSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(
            text = stringResource(R.string.settings_colour_source),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        VSpace(12)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColourSeedSetting.entries.forEach { option ->
                Swatch(
                    colour = if (option == ColourSeedSetting.WALLPAPER) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        seedColorFor(option)
                    },
                    selected = option == selected,
                    isWallpaper = option == ColourSeedSetting.WALLPAPER,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun Swatch(
    colour: Color,
    selected: Boolean,
    isWallpaper: Boolean,
    onClick: () -> Unit,
) {
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        animationSpec = MarkMotion.springy(),
        label = "swatchRing",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.88f,
        animationSpec = MarkMotion.springy(),
        label = "swatchScale",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .border(
                width = ringWidth,
                color = MaterialTheme.colorScheme.onSurface,
                shape = PillShape,
            )
            .padding(4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(PillShape)
            .background(colour)
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isWallpaper) {
            Icon(
                painter = MarkIcons.Palette,
                contentDescription = stringResource(R.string.settings_colour_wallpaper),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Picking how the seed is spread across the palette.
 *
 * Each option previews itself: the three dots are that style's own primary, secondary
 * and tertiary, generated from the seed currently in use. The difference between
 * Tonal Spot and Expressive is hard to put into a sentence and obvious the moment you
 * see the two side by side.
 */
@Composable
fun PaletteStyleRow(
    selected: PaletteStyleSetting,
    seed: ColourSeedSetting,
    dark: Boolean,
    onSelect: (PaletteStyleSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seedColour = if (seed == ColourSeedSetting.WALLPAPER) {
        MaterialTheme.colorScheme.primary
    } else {
        seedColorFor(seed)
    }

    Column(modifier = modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(
            text = stringResource(R.string.settings_palette_style),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        VSpace(12)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PaletteStyleSetting.entries.forEach { style ->
                PaletteStyleChip(
                    style = style,
                    label = paletteStyleLabel(style),
                    seed = seedColour,
                    dark = dark,
                    selected = style == selected,
                    onClick = { onSelect(style) },
                )
            }
        }
    }
}

@Composable
private fun PaletteStyleChip(
    style: PaletteStyleSetting,
    label: String,
    seed: Color,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val preview = buildColorScheme(
        seed = seed,
        style = style.toPreviewStyle(),
        dark = dark,
    )

    val border by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = MarkMotion.colourSpec(),
        label = "styleBorder",
    )

    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(width = 2.dp, color = border, shape = MaterialTheme.shapes.medium)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(preview.primary, preview.secondary, preview.tertiary).forEach { dot ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(PillShape)
                        .background(dot)
                )
            }
        }
        VSpace(8)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun PaletteStyleSetting.toPreviewStyle() = when (this) {
    PaletteStyleSetting.TONAL_SPOT -> nl.markmaaktmedia.markmaaktai.ui.theme.PaletteStyle.TONAL_SPOT
    PaletteStyleSetting.VIBRANT -> nl.markmaaktmedia.markmaaktai.ui.theme.PaletteStyle.VIBRANT
    PaletteStyleSetting.EXPRESSIVE -> nl.markmaaktmedia.markmaaktai.ui.theme.PaletteStyle.EXPRESSIVE
    PaletteStyleSetting.FRUIT_SALAD -> nl.markmaaktmedia.markmaaktai.ui.theme.PaletteStyle.FRUIT_SALAD
    PaletteStyleSetting.FIDELITY -> nl.markmaaktmedia.markmaaktai.ui.theme.PaletteStyle.FIDELITY
    PaletteStyleSetting.MONOCHROME -> nl.markmaaktmedia.markmaaktai.ui.theme.PaletteStyle.MONOCHROME
}

@Composable
private fun paletteStyleLabel(style: PaletteStyleSetting): String = stringResource(
    when (style) {
        PaletteStyleSetting.TONAL_SPOT -> R.string.palette_tonal_spot
        PaletteStyleSetting.VIBRANT -> R.string.palette_vibrant
        PaletteStyleSetting.EXPRESSIVE -> R.string.palette_expressive
        PaletteStyleSetting.FRUIT_SALAD -> R.string.palette_fruit_salad
        PaletteStyleSetting.FIDELITY -> R.string.palette_fidelity
        PaletteStyleSetting.MONOCHROME -> R.string.palette_monochrome
    }
)
