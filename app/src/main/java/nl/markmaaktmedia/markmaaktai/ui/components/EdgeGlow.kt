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
            val depth = 0.13f + 0.07f * wave(reach + phase)
            val alpha = (0.30f + 0.22f * wave(brightness + phase * 1.7f)) * presence
            val colour = colors.cycleAt(hue + phase)
            drawEdge(edge, colour, depth * edge.reachScale(), alpha)
        }
    }
}

/** The palette the glow uses when a caller does not supply one. */
@Composable
fun defaultEdgeColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.primary,
)

/** How far into the screen each side is allowed to reach. */
private fun Edge.reachScale(): Float = when (this) {
    Edge.Bottom -> 1.35f
    Edge.Top -> 0.85f
    else -> 1f
}

private fun DrawScope.drawEdge(edge: Edge, colour: Color, depth: Float, alpha: Float) {
    val tinted = colour.copy(alpha = alpha)
    val transparent = colour.copy(alpha = 0f)

    when (edge) {
        Edge.Top -> {
            val height = size.height * depth
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(tinted, transparent),
                    startY = 0f,
                    endY = height,
                ),
                size = Size(size.width, height),
            )
        }

        Edge.Bottom -> {
            val height = size.height * depth
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(transparent, tinted),
                    startY = size.height - height,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - height),
                size = Size(size.width, height),
            )
        }

        Edge.Start -> {
            val width = size.width * depth
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(tinted, transparent),
                    startX = 0f,
                    endX = width,
                ),
                size = Size(width, size.height),
            )
        }

        Edge.End -> {
            val width = size.width * depth
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(transparent, tinted),
                    startX = size.width - width,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - width, 0f),
                size = Size(width, size.height),
            )
        }
    }
}

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
