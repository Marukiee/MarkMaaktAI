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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Light travelling around the edge of the screen.
 *
 * This is the assistant's whole visual identity, so it is worth saying what it is and
 * what it is not. It is a handful of soft coloured lights that drift around the frame
 * at different speeds, each one sitting just off the edge so only its inner falloff
 * reaches the screen. Where two overlap the colours mix, which is what makes it look
 * like light rather than like decoration.
 *
 * It used to be one band per side, drawn as a run of slices so the colour could change
 * along an edge. That left two faults with no good fix: the slices met in visible
 * lines, and a band measured as a fraction of the side it sits on is thinner top and
 * bottom than left and right on a tall screen. Round lights have neither problem. They
 * are sized against the short side of the display, so the reach is the same wherever
 * they are, and a radial falloff has nothing to seam against.
 *
 * Nothing here is a real blur. A blur pass the size of the display every frame is
 * expensive, and at this softness a radial gradient cannot be told apart from one.
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

    // One slow ride around the frame, one faster one, and a breath that belongs to
    // neither. Periods with no common multiple, so the pattern never repeats itself.
    val drift by transition.cycle(durationMillis = 19000, label = "drift")
    val counter by transition.cycle(durationMillis = 12300, label = "counter")
    val breath by transition.cycle(durationMillis = 5100, label = "breath")

    if (presence <= 0.001f || colors.isEmpty()) return

    Canvas(modifier = modifier) {
        val short = min(size.width, size.height)

        Lights.forEachIndexed { index, light ->
            // Half the lights ride the slow phase, half the faster one, and each keeps
            // its own head start. Nothing ever queues up behind anything else.
            val phase = if (index % 2 == 0) drift else -counter
            val travel = phase * light.speed + light.offset

            val radius = short * light.radius * (0.88f + 0.12f * wave(breath + light.offset))
            val alpha = presence * light.alpha * (0.72f + 0.28f * wave(breath * 1.6f + light.offset))
            val colour = colors.cycleAt(travel * 0.5f + light.offset)

            drawLight(perimeterPoint(travel, radius), radius, colour, alpha)
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

/**
 * One light: how big, how bright, how fast it goes round, and where it starts.
 *
 * Written out rather than generated so the arrangement can be looked at and judged.
 * Two large slow ones carry the colour, three smaller quicker ones cross them.
 */
private class Light(
    val radius: Float,
    val alpha: Float,
    val speed: Float,
    val offset: Float,
)

private val Lights = listOf(
    Light(radius = 0.95f, alpha = 0.50f, speed = 1.0f, offset = 0.00f),
    Light(radius = 0.80f, alpha = 0.46f, speed = 1.0f, offset = 0.52f),
    Light(radius = 0.62f, alpha = 0.42f, speed = 1.6f, offset = 0.27f),
    Light(radius = 0.55f, alpha = 0.38f, speed = 1.6f, offset = 0.71f),
    Light(radius = 0.48f, alpha = 0.34f, speed = 2.3f, offset = 0.88f),
)

/**
 * A point on the border of the screen at [travel], with the light pushed outwards so
 * only its inner half is on screen. Without that push the brightest part of every
 * light sits in the middle of an edge and the effect reads as five coloured spots.
 */
private fun DrawScope.perimeterPoint(travel: Float, radius: Float): Offset {
    val t = ((travel % 1f) + 1f) % 1f
    val width = size.width
    val height = size.height
    val out = radius * PushOut

    // Perimeter split by side length, so a light keeps its speed round the corners
    // instead of racing along the short sides.
    val total = 2f * (width + height)
    val distance = t * total

    return when {
        distance < width -> Offset(distance, -out)
        distance < width + height -> Offset(width + out, distance - width)
        distance < 2f * width + height -> Offset(2f * width + height - distance, height + out)
        else -> Offset(-out, total - distance)
    }
}

private fun DrawScope.drawLight(centre: Offset, radius: Float, colour: Color, alpha: Float) {
    if (radius <= 0f || alpha <= 0.002f) return
    drawCircle(
        brush = Brush.radialGradient(
            // Three stops, not two. A straight fade from full to nothing leaves a
            // visible ring where the falloff starts; holding most of the colour for
            // the first half and letting go slowly does not.
            colorStops = arrayOf(
                0.0f to colour.copy(alpha = alpha),
                0.45f to colour.copy(alpha = alpha * 0.55f),
                1.0f to colour.copy(alpha = 0f),
            ),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

/** How far past the edge a light sits, as a share of its own radius. */
private const val PushOut = 0.55f

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
