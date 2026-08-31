package nl.markmaaktmedia.markmaaktai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin

/** The four sides light can come in from. */
private enum class Edge { Top, Bottom, Start, End }

/**
 * Light bleeding in from the edges of the screen.
 *
 * This is the assistant's whole visual identity, so it is worth saying what it is
 * doing. Each edge gets its own band of colour fading inward, and three things move
 * independently: how deep the band reaches, how bright it is, and which colour it
 * currently holds. The three run on periods that share no common multiple, so the
 * pattern never lands back where it started and it reads as light rather than as a
 * loop.
 *
 * Colour is interpolated between palette stops rather than switched, because a hard
 * change between two hues at this size is visible as a flicker. The bottom edge is
 * given the most reach: the sheet lives down there, so that is where the light should
 * look like it is coming from.
 *
 * Drawn as gradients rather than a blurred shape on purpose. A real blur over a full
 * screen surface costs a pass the size of the display every frame, and at this
 * softness a gradient is indistinguishable from one.
 */
@Composable
fun EdgeGlow(
    active: Boolean,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    colors: List<Color> = defaultEdgeColors(),
) {
    val transition = rememberInfiniteTransition(label = "edgeGlow")

    val presence by animateFloatAsState(
        targetValue = if (active) intensity else 0f,
        animationSpec = tween(durationMillis = 700, easing = LinearEasing),
        label = "edgePresence",
    )

    val reach by transition.cycle(durationMillis = 5300, label = "reach")
    val brightness by transition.cycle(durationMillis = 3700, label = "brightness")
    val hue by transition.cycle(durationMillis = 11000, label = "hue")

    if (presence <= 0.001f || colors.isEmpty()) return

    Canvas(modifier = modifier) {
        Edge.entries.forEachIndexed { index, edge ->
            // Quarter turn of phase per edge, so the light travels around the frame
            // instead of every side breathing together.
            val phase = index * 0.25f
            val depth = 0.16f + 0.09f * wave(reach + phase)
            val alpha = (0.42f + 0.26f * wave(brightness + phase * 1.7f)) * presence
            drawEdge(
                edge = edge,
                colours = colors,
                position = hue + phase,
                depth = depth * edge.reachScale(),
                alpha = alpha,
            )
        }
    }
}

/** The palette the glow uses when a caller does not supply one. */
@Composable
fun defaultEdgeColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.primaryContainer,
    MaterialTheme.colorScheme.tertiaryContainer,
    MaterialTheme.colorScheme.primary,
)

/** How far into the screen each side is allowed to reach. */
private fun Edge.reachScale(): Float = when (this) {
    Edge.Bottom -> 1.35f
    Edge.Top -> 0.85f
    else -> 1f
}

/**
 * One side of the frame, lit.
 *
 * The band fades inward, and it also changes colour along its length, which is the
 * part that makes this read as light through glass rather than as a coloured border.
 * Two gradients at right angles cannot be expressed as one brush, so the edge is drawn
 * as a run of narrow slices, each fading inward on its own colour. At this alpha the
 * seams between neighbouring slices are not visible, and forty small rectangles cost
 * nothing next to a real blur.
 */
private fun DrawScope.drawEdge(
    edge: Edge,
    colours: List<Color>,
    position: Float,
    depth: Float,
    alpha: Float,
) {
    val vertical = edge == Edge.Top || edge == Edge.Bottom
    val along = if (vertical) size.width else size.height
    val band = (if (vertical) size.height else size.width) * depth
    if (along <= 0f || band <= 0f) return

    val slice = along / Slices
    for (index in 0 until Slices) {
        val travel = index / Slices.toFloat()
        val colour = colours.cycleAt(position + travel * SpreadAcrossEdge)
        // A touch brighter in the middle of each side, so the corners recede and the
        // light looks like it has a source rather than an outline.
        val shaped = alpha * (0.55f + 0.45f * wave(travel * 0.5f - 0.25f))
        val tinted = colour.copy(alpha = shaped)
        val clear = colour.copy(alpha = 0f)
        val start = index * slice
        // Overdrawn by a pixel, or a seam of background shows between slices.
        val width = slice + 1f

        when (edge) {
            Edge.Top -> drawRect(
                brush = Brush.verticalGradient(listOf(tinted, clear), startY = 0f, endY = band),
                topLeft = Offset(start, 0f),
                size = Size(width, band),
            )

            Edge.Bottom -> drawRect(
                brush = Brush.verticalGradient(
                    listOf(clear, tinted),
                    startY = size.height - band,
                    endY = size.height,
                ),
                topLeft = Offset(start, size.height - band),
                size = Size(width, band),
            )

            Edge.Start -> drawRect(
                brush = Brush.horizontalGradient(listOf(tinted, clear), startX = 0f, endX = band),
                topLeft = Offset(0f, start),
                size = Size(band, width),
            )

            Edge.End -> drawRect(
                brush = Brush.horizontalGradient(
                    listOf(clear, tinted),
                    startX = size.width - band,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - band, start),
                size = Size(band, width),
            )
        }
    }
}

/** Slices per side. Enough that the colour change along an edge looks continuous. */
private const val Slices = 12

/** How much of the palette one side travels through, end to end. */
private const val SpreadAcrossEdge = 0.5f

/** A 0 to 1 ramp that restarts, used as the phase for everything that drifts. */
@Composable
private fun androidx.compose.animation.core.InfiniteTransition.cycle(
    durationMillis: Int,
    label: String,
) = animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = durationMillis, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
    ),
    label = label,
)

/** Turns a restarting ramp into a smooth 0 to 1 breath with no seam at the wrap. */
private fun wave(phase: Float): Float = (sin(phase * 2f * PI.toFloat()) + 1f) / 2f

/** Reads a colour from the list at a fractional position, blending between stops. */
private fun List<Color>.cycleAt(position: Float): Color {
    if (size == 1) return first()
    val wrapped = ((position % 1f) + 1f) % 1f
    val scaled = wrapped * size
    val index = scaled.toInt() % size
    val next = (index + 1) % size
    return lerp(this[index], this[next], scaled - scaled.toInt())
}
