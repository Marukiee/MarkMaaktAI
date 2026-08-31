package nl.markmaaktmedia.markmaaktai.ui.assist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ui.components.EdgeGlow
import nl.markmaaktmedia.markmaaktai.ui.components.MarkIconButton
import nl.markmaaktmedia.markmaaktai.ui.components.PillSpinner
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
import nl.markmaaktmedia.markmaaktai.ui.theme.SquircleShape
import kotlin.math.sin

/** What the assistant sheet is showing. */
data class AssistUiState(
    val query: String = "",
    val askedQuestion: String = "",
    val answer: String = "",
    val isAnswering: Boolean = false,
    val isListening: Boolean = false,
    /** How loud the microphone is right now, from nothing to full. */
    val level: Float = 0f,
    val hasScreenContext: Boolean = false,
    val error: String? = null,
    /**
     * Bumped every time the sheet is summoned. The entry animation keys off it, so a
     * second summoning plays the same arrival as the first instead of reusing a
     * composition that is already on screen.
     */
    val showId: Int = 0,
    /** Set just before the window goes, so the sheet can slide out first. */
    val closing: Boolean = false,
)

/**
 * The assistant sheet.
 *
 * One job, about three seconds to do it. The microphone opens on its own where the
 * phone can listen, so pressing the power button and talking is the whole
 * interaction.
 *
 * The light around the edges of the screen carries the state, which is why the window
 * takes the full display: clipped to the sheet it would only glow along the bottom.
 * It holds while the microphone is open or the model is working and drops away once
 * there is an answer to read. Everything outside the sheet stays see-through, because
 * the screen being asked about is the context.
 *
 * Dragging the sheet upwards opens the full app. That replaced a small button in the
 * corner: the gesture is the one people try anyway, and the corner is better spent on
 * the single control that closes.
 */
@Composable
fun AssistOverlay(
    state: AssistUiState,
    onQueryChange: (String) -> Unit,
    onAsk: () -> Unit,
    onDictate: () -> Unit,
    onOpenApp: () -> Unit,
    onAskScreen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.showId) {
        // A frame between the two, or the sheet is composed already visible and the
        // arrival only plays the very first time.
        visible = false
        androidx.compose.runtime.withFrameNanos { }
        visible = true
    }
    LaunchedEffect(state.closing) {
        if (state.closing) visible = false
    }

    val busy = state.isAnswering || state.isListening

    /*
     * The arrival.
     *
     * Three values, not one, because a drop does not move as a single rigid thing.
     * The rise carries it up from the bottom edge on a loose spring, so it overshoots
     * and settles. The spread is a second, slower spring that widens it into the full
     * panel, and running behind the rise is what gives the shape: it arrives narrow,
     * then relaxes outwards. The badge above it has its own spring again, softer
     * still, so it trails the panel by a fraction rather than moving in lockstep with
     * it, which is what made the two read as one printed image sliding up.
     */
    val rise by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) {
            spring(dampingRatio = 0.52f, stiffness = 260f)
        } else {
            tween(200, easing = LinearEasing)
        },
        label = "assistRise",
    )
    val spread by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) {
            spring(dampingRatio = 0.62f, stiffness = 150f)
        } else {
            tween(180, easing = LinearEasing)
        },
        label = "assistSpread",
    )
    val badgeEntry by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) {
            spring(dampingRatio = 0.48f, stiffness = 110f)
        } else {
            tween(140, easing = LinearEasing)
        },
        label = "assistBadge",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .bouncyClickable(role = null, pressedScale = 1f, onClick = onClose),
    ) {
        EdgeGlow(
            active = visible,
            intensity = if (busy) 1f else 0.55f,
            modifier = Modifier.fillMaxSize(),
        )

        if (rise > 0.001f) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                if (state.hasScreenContext) {
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                val eased = badgeEntry.coerceIn(0f, 1.2f)
                                alpha = (eased * 1.6f).coerceIn(0f, 1f)
                                translationY = (1f - eased) * 34.dp.toPx()
                                scaleX = 0.7f + 0.3f * eased
                                scaleY = 0.7f + 0.3f * eased
                                transformOrigin = TransformOrigin(0.15f, 1f)
                            }
                            .padding(start = 8.dp, bottom = 10.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            // It reads as an offer, so it behaves as one. Without a
                            // handler of its own the tap fell through to the scrim
                            // behind it and closed the assistant instead.
                            .bouncyClickable(onClick = onAskScreen)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = MarkIcons.Sparkle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = stringResource(R.string.assist_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Box(
                    modifier = Modifier.graphicsLayer {
                        val up = rise.coerceIn(0f, 1.2f)
                        val wide = spread.coerceIn(0f, 1.2f)
                        alpha = (up * 2f).coerceIn(0f, 1f)
                        translationY = (1f - up) * 110.dp.toPx()
                        scaleX = 0.34f + 0.66f * wide
                        scaleY = 0.66f + 0.34f * up
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                ) {
                    AssistSheet(
                        state = state,
                        onQueryChange = onQueryChange,
                        onAsk = onAsk,
                        onDictate = onDictate,
                        onOpenApp = onOpenApp,
                        onClose = onClose,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistSheet(
    state: AssistUiState,
    onQueryChange: (String) -> Unit,
    onAsk: () -> Unit,
    onDictate: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    var dragged by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .clip(SquircleShape(30.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> dragged += delta },
                onDragStopped = {
                    // Up far enough means "give me the whole app". Anything else is a
                    // stray touch and is simply forgotten.
                    if (dragged < OpenAppThreshold) onOpenApp()
                    dragged = 0f
                },
            )
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        VSpace(12)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = state.isAnswering) {
                Row {
                    PillSpinner(size = 20.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                }
            }
            Text(
                text = state.askedQuestion.ifBlank { stringResource(R.string.assist_ready) },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.askedQuestion.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            MarkIconButton(
                icon = MarkIcons.Close,
                contentDescription = stringResource(R.string.assist_close),
                onClick = onClose,
                size = 36,
                iconSize = 17,
            )
        }

        AnimatedVisibility(
            visible = state.answer.isNotBlank() || state.error != null,
            enter = expandVertically(animationSpec = MarkMotion.sizeSpring()) + fadeIn(),
            exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                VSpace(10)
                Text(
                    text = state.error ?: state.answer,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }

        VSpace(16)
        AssistInput(
            state = state,
            onQueryChange = onQueryChange,
            onAsk = onAsk,
            onDictate = onDictate,
        )
    }
}

@Composable
private fun AssistInput(
    state: AssistUiState,
    onQueryChange: (String) -> Unit,
    onAsk: () -> Unit,
    onDictate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (state.query.isEmpty()) {
                if (state.isListening) {
                    ListeningWave(level = state.level)
                } else {
                    Text(
                        text = stringResource(R.string.assist_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        MarkIconButton(
            icon = MarkIcons.Mic,
            contentDescription = stringResource(R.string.chat_voice_input),
            onClick = onDictate,
            tint = if (state.isListening) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            background = if (state.isListening) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                Color.Transparent
            },
        )

        MarkIconButton(
            icon = MarkIcons.Send,
            contentDescription = stringResource(R.string.chat_send),
            onClick = onAsk,
            enabled = state.query.isNotBlank(),
            tint = if (state.query.isNotBlank()) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            background = if (state.query.isNotBlank()) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHighest,
            iconSize = 20,
        )
    }
}

/**
 * The bars that stand in for a voice while the microphone is open.
 *
 * Each bar runs on its own period, picked so no two line up for a long time. Bars
 * that rise in step read as a progress animation; bars that drift out of phase read
 * as something responding to sound.
 */
@Composable
private fun ListeningWave(level: Float, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "listening")
    val periods = listOf(720, 910, 640, 1050, 830, 960, 700)

    val phases = periods.mapIndexed { index, period ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = period, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wave$index",
        )
    }

    val colour = MaterialTheme.colorScheme.primary
    // Follows the microphone rather than running on its own. Smoothed, because a raw
    // reading per buffer flickers, and floored a little so the bars never look frozen.
    val loudness by animateFloatAsState(
        targetValue = 0.12f + level.coerceIn(0f, 1f) * 0.88f,
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "loudness",
    )

    Canvas(
        modifier = modifier
            .width(96.dp)
            .height(24.dp),
    ) {
        val barWidth = size.width / (periods.size * 2f)
        phases.forEachIndexed { index, phase ->
            val shape = (sin(phase.value * 2f * Math.PI.toFloat()) + 1f) / 2f
            val barHeight = size.height * (0.14f + shape * 0.86f * loudness)
            val x = index * barWidth * 2f
            drawRoundRect(
                color = colour.copy(alpha = 0.45f + shape * 0.55f * loudness),
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

/** Drag distance, in pixels, that counts as asking for the full app. */
private const val OpenAppThreshold = -120f
