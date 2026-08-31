package nl.markmaaktmedia.markmaaktai.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException

/**
 * How far a back gesture has been dragged, and from which edge.
 *
 * Kept as state rather than being applied directly, so one gesture can drive the
 * screen that is leaving and anything else that wants to react to it.
 */
class PredictiveBackState internal constructor() {
    var progress by mutableFloatStateOf(0f)
        internal set

    /** True when the finger came from the left edge, which is the common case. */
    var fromLeftEdge by mutableStateOf(true)
        internal set

    /**
     * True while the screen is easing back after an abandoned swipe.
     *
     * The modifier reads this to decide whether to follow the value exactly, which is
     * what a finger on the glass needs, or to animate towards it, which is what a
     * released gesture needs.
     */
    var settling by mutableStateOf(false)
        internal set
}

/**
 * The system back gesture, with the screen following the finger.
 *
 * Android 13 and up reports the gesture continuously before it commits, so back can
 * show where it is going rather than only arriving there. This wires that up: the
 * screen being left shrinks and slides toward the edge the finger came from,
 * revealing what is underneath, and springs back if the gesture is abandoned.
 *
 * The commit path runs [onBack] once, after the collection finishes. Cancellation is
 * a normal outcome here, not an error: it is what a released, unfinished swipe looks
 * like, so it only resets the progress.
 *
 * On a device or a setting where predictive back is off, this behaves exactly like a
 * plain back handler, because the system simply sends one final event.
 */
@Composable
fun rememberPredictiveBack(
    enabled: Boolean,
    onBack: () -> Unit,
): PredictiveBackState {
    val state = remember { PredictiveBackState() }

    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            state.settling = false
            events.collect { event: BackEventCompat ->
                state.progress = event.progress
                state.fromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT
            }
            state.progress = 0f
            onBack()
        } catch (cancelled: CancellationException) {
            /*
             * Let go without finishing.
             *
             * Nothing suspending can run here: this coroutine has already been
             * cancelled, so an animation started in this block would be cancelled on
             * its first frame. The flag hands the easing to the modifier, which is
             * composed in a scope that is still alive.
             */
            state.settling = true
            state.progress = 0f
            throw cancelled
        }
    }

    return state
}

/**
 * Applies a predictive back gesture to whatever is leaving.
 *
 * The shape of the movement is Android's own: a small scale down, a slide toward the
 * edge the finger came from, and rounded corners appearing as the surface lifts. It
 * is deliberately modest, because the point is to show the layer underneath, not to
 * perform.
 */
fun Modifier.predictiveBack(state: PredictiveBackState): Modifier = composed {
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        // Follow the finger exactly while it is down, ease back once it is not.
        animationSpec = if (state.settling) {
            nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion.spatial()
        } else {
            androidx.compose.animation.core.snap()
        },
        label = "predictiveBack",
    )
    if (progress <= 0.001f) return@composed this

    val direction = if (state.fromLeftEdge) 1f else -1f
    graphicsLayer {
        val scale = 1f - (MaxScaleDrop * progress)
        scaleX = scale
        scaleY = scale
        translationX = direction * progress * MaxSlide.toPx()
        transformOrigin = TransformOrigin(if (state.fromLeftEdge) 1f else 0f, 0.5f)
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CornerRadius * progress)
        clip = true
    }
}

private const val MaxScaleDrop = 0.1f
private val MaxSlide = 24.dp
private val CornerRadius = 32.dp
