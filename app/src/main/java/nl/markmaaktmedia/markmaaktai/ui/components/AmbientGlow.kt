package nl.markmaaktmedia.markmaaktai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The glow that says the model is working.
 *
 * Three coloured blobs drift on their own periods behind a blurred layer. The periods
 * are deliberately not multiples of each other, so the pattern never visibly repeats
 * and it reads as something alive rather than a looping animation. It fades in and
 * out with the work, so an idle screen is completely still.
 *
 * The blur is real, using the platform effect available from Android 12, which is the
 * minimum this app supports anyway. That is what keeps the blobs reading as light
 * rather than as three coloured circles.
 */
@Composable
fun AmbientGlow(
    active: Boolean,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    blurRadius: androidx.compose.ui.unit.Dp = 48.dp,
    colors: List<Color> = defaultGlowColors(),
) {
    val transition = rememberInfiniteTransition(label = "ambientGlow")

    val presence by animateFloatAsState(
        targetValue = if (active) intensity else 0f,
        animationSpec = tween(durationMillis = 620, easing = LinearEasing),
        label = "glowPresence",
    )

    val phaseA by transition.driftPhase(durationMillis = 5200, label = "phaseA")
    val phaseB by transition.driftPhase(durationMillis = 7100, label = "phaseB")
    val phaseC by transition.driftPhase(durationMillis = 9300, label = "phaseC")

    if (presence <= 0.001f) return

    Box(
        modifier = modifier
            .blur(blurRadius)
            .drawBehind {
                drawBlob(colors[0], phaseA, radiusRatio = 0.62f, alpha = 0.55f * presence, swing = 0.30f)
                drawBlob(colors[1], phaseB, radiusRatio = 0.52f, alpha = 0.48f * presence, swing = 0.36f)
                drawBlob(colors[2], phaseC, radiusRatio = 0.44f, alpha = 0.42f * presence, swing = 0.42f)
            }
    )
}

/**
 * The same glow as a modifier, for the composer bar: it has to sit behind the input
 * without the input becoming a child of the glow.
 */
fun Modifier.ambientGlowBehind(
    active: Boolean,
    colors: List<Color>,
    intensity: Float = 1f,
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "glowModifier")
    val presence by animateFloatAsState(
        targetValue = if (active) intensity else 0f,
        animationSpec = tween(durationMillis = 620, easing = LinearEasing),
        label = "glowModifierPresence",
    )
    val phaseA by transition.driftPhase(4700, "modPhaseA")
    val phaseB by transition.driftPhase(6900, "modPhaseB")

    drawBehind {
        if (presence <= 0.001f) return@drawBehind
        drawBlob(colors[0], phaseA, radiusRatio = 0.9f, alpha = 0.30f * presence, swing = 0.34f)
        drawBlob(
            colors.getOrElse(1) { colors[0] },
            phaseB,
            radiusRatio = 0.7f,
            alpha = 0.26f * presence,
            swing = 0.40f,
        )
    }
}

@Composable
fun GlowContainer(
    active: Boolean,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    colors: List<Color> = defaultGlowColors(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        AmbientGlow(
            active = active,
            modifier = Modifier.matchParentSize(),
            intensity = intensity,
            colors = colors,
        )
        content()
    }
}

@Composable
fun defaultGlowColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.secondary,
)

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.driftPhase(
    durationMillis: Int,
    label: String,
) = animateFloat(
    initialValue = 0f,
    targetValue = (2 * Math.PI).toFloat(),
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = durationMillis, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
    ),
    label = label,
)

/**
 * One blob, moving on a Lissajous path. Two different frequencies on the two axes
 * trace an open curve instead of a circle, which is what stops the eye from locking
 * on to the loop.
 */
private fun DrawScope.drawBlob(
    color: Color,
    phase: Float,
    radiusRatio: Float,
    alpha: Float,
    swing: Float,
) {
    val radius = size.minDimension * radiusRatio
    val centre = Offset(
        x = size.width * (0.5f + swing * cos(phase)),
        y = size.height * (0.5f + swing * 0.6f * sin(phase * 1.4f)),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}
