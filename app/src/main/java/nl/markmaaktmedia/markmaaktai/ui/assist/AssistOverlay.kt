package nl.markmaaktmedia.markmaaktai.ui.assist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ui.components.AmbientGlow
import nl.markmaaktmedia.markmaaktai.ui.components.PillBadge
import nl.markmaaktmedia.markmaaktai.ui.components.PillMark
import nl.markmaaktmedia.markmaaktai.ui.components.PillSpinner
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
import nl.markmaaktmedia.markmaaktai.ui.theme.SheetSquircle

/** What the assistant sheet is showing. */
data class AssistUiState(
    val query: String = "",
    val askedQuestion: String = "",
    val answer: String = "",
    val isAnswering: Boolean = false,
    val isListening: Boolean = false,
    val hasScreenContext: Boolean = false,
    val error: String? = null,
)

/**
 * The assistant sheet.
 *
 * It has one job and about three seconds to do it, so it opens straight onto an
 * input that is already focused, with the answer growing upward out of it. The glow
 * behind the sheet is the only thing that reports state: it comes up while the model
 * is thinking or the microphone is open, and it is gone the moment there is an answer
 * to read. Everything outside the sheet stays see-through, because the screen being
 * asked about is the context and hiding it would defeat the point.
 */
@Composable
fun AssistOverlay(
    state: AssistUiState,
    onQueryChange: (String) -> Unit,
    onAsk: () -> Unit,
    onDictate: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.55f else 0f,
        animationSpec = tween(durationMillis = 260, easing = LinearEasing),
        label = "scrim",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .bouncyClickable(role = null, pressedScale = 1f, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = MarkMotion.spatial()) { it } + fadeIn(),
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                AmbientGlow(
                    active = state.isAnswering || state.isListening,
                    intensity = 0.9f,
                    blurRadius = 64.dp,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(bottom = 24.dp),
                )

                Column(
                    modifier = Modifier
                        .padding(10.dp)
                        .clip(SheetSquircle)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(20.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (state.isAnswering) {
                            PillSpinner(size = 24.dp)
                        } else {
                            PillMark(size = 24.dp)
                        }
                        Text(
                            text = stringResource(R.string.assist_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.assist_open_app),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(PillShape)
                                .bouncyClickable(onClick = onOpenApp)
                                .padding(6.dp),
                        )
                    }

                    AnimatedVisibility(visible = state.hasScreenContext) {
                        Column {
                            VSpace(10)
                            PillBadge(text = stringResource(R.string.assist_screen_context))
                        }
                    }

                    AnimatedVisibility(
                        visible = state.askedQuestion.isNotBlank(),
                        enter = expandVertically(animationSpec = MarkMotion.sizeSpring()) + fadeIn(),
                        exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) + fadeOut(),
                    ) {
                        Column {
                            VSpace(14)
                            Text(
                                text = state.askedQuestion,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = state.answer.isNotBlank() || state.error != null,
                        enter = expandVertically(animationSpec = MarkMotion.sizeSpring()) + fadeIn(),
                        exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) + fadeOut(),
                    ) {
                        Column {
                            VSpace(10)
                            Text(
                                text = state.error ?: state.answer,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (state.error != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }

                    VSpace(16)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.query.isEmpty()) {
                                Text(
                                    text = stringResource(
                                        if (state.isListening) R.string.chat_listening
                                        else R.string.assist_hint
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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

                        MicButton(listening = state.isListening, onClick = onDictate)

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(PillShape)
                                .background(
                                    if (state.query.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                                .bouncyClickable(enabled = state.query.isNotBlank(), onClick = onAsk),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(R.string.chat_send),
                                tint = if (state.query.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The microphone, with rings that leave it while it is listening.
 *
 * Two rings on offset phases, each fading as it grows, so there is always one on its
 * way out. It is the clearest way to say "the microphone is open" without a line of
 * text, and it stops dead the moment listening ends, which matters more: nobody
 * should have to wonder whether their phone is still recording.
 */
@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mic")
    val ringA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringA",
    )
    val ringB by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringB",
    )

    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(PillShape)
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (listening) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                listOf(ringA, ringB % 1f).forEach { phase ->
                    val radius = size.minDimension / 2f * (0.5f + phase * 0.5f)
                    drawCircle(
                        color = accent.copy(alpha = (1f - phase) * 0.4f),
                        radius = radius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = stringResource(R.string.chat_voice_input),
            tint = if (listening) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp),
        )
    }
}
