package nl.markmaaktmedia.markmaaktai.ui.assist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ui.components.EdgeGlow
import nl.markmaaktmedia.markmaaktai.ui.components.MarkIconButton
import nl.markmaaktmedia.markmaaktai.ui.components.PillBadge
import nl.markmaaktmedia.markmaaktai.ui.components.PillMark
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
    val hasScreenContext: Boolean = false,
    val error: String? = null,
)

/**
 * The assistant sheet.
 *
 * It has one job and about three seconds to do it. The microphone opens on its own
 * the moment it appears, so pressing the power button and talking is the whole
 * interaction and nothing has to be tapped first.
 *
 * The light around the edges of the screen is the state. It comes up as the sheet
 * arrives, holds while the microphone is open or the model is working, and drops away
 * once there is an answer to read. Everything outside the sheet stays see-through:
 * the screen being asked about is the context, and covering it would defeat the point.
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

    val busy = state.isAnswering || state.isListening

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.42f else 0f,
        animationSpec = tween(durationMillis = 320, easing = LinearEasing),
        label = "scrim",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .bouncyClickable(role = null, pressedScale = 1f, onClick = onClose),
    ) {
        // The glow sits above the scrim and below the sheet, so it lights the edges of
        // whatever app is behind rather than the sheet itself.
        EdgeGlow(
            active = visible,
            intensity = if (busy) 1f else 0.5f,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = MarkMotion.spatial()) { it } + fadeIn(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .clip(SquircleShape(38.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                AssistHeader(
                    busy = state.isAnswering,
                    hasScreenContext = state.hasScreenContext,
                    onOpenApp = onOpenApp,
                    onClose = onClose,
                )

                AnimatedVisibility(
                    visible = state.askedQuestion.isNotBlank(),
                    enter = expandVertically(animationSpec = MarkMotion.sizeSpring()) + fadeIn(),
                    exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) + fadeOut(),
                ) {
                    Column {
                        VSpace(16)
                        Text(
                            text = state.askedQuestion,
                            style = MaterialTheme.typography.titleMedium,
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
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }

                VSpace(18)
                AssistInput(
                    state = state,
                    onQueryChange = onQueryChange,
                    onAsk = onAsk,
                    onDictate = onDictate,
                )
            }
        }
    }
}

@Composable
private fun AssistHeader(
    busy: Boolean,
    hasScreenContext: Boolean,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) PillSpinner(size = 26.dp) else PillMark(size = 26.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.assist_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AnimatedVisibility(visible = hasScreenContext) {
                Text(
                    text = stringResource(R.string.assist_screen_context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        MarkIconButton(
            icon = MarkIcons.OpenInNew,
            contentDescription = stringResource(R.string.assist_open_app),
            onClick = onOpenApp,
            size = 38,
            iconSize = 18,
        )
        MarkIconButton(
            icon = MarkIcons.Close,
            contentDescription = stringResource(R.string.assist_close),
            onClick = onClose,
            size = 38,
            iconSize = 18,
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
                    ListeningWave()
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
 * Each bar runs on its own period, and the periods are picked so no two line up for a
 * long time. Bars that rise and fall in step read as a progress animation; bars that
 * drift out of phase read as something responding to sound.
 */
@Composable
private fun ListeningWave(modifier: Modifier = Modifier) {
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

    Canvas(
        modifier = modifier
            .width(96.dp)
            .height(24.dp),
    ) {
        val barWidth = size.width / (periods.size * 2f)
        phases.forEachIndexed { index, phase ->
            val level = (sin(phase.value * 2f * Math.PI.toFloat()) + 1f) / 2f
            val barHeight = size.height * (0.25f + level * 0.75f)
            val x = index * barWidth * 2f
            drawRoundRect(
                color = colour.copy(alpha = 0.55f + level * 0.45f),
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
