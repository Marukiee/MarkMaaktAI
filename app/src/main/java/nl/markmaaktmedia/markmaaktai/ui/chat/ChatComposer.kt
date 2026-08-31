package nl.markmaaktmedia.markmaaktai.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ui.components.MarkIconButton
import nl.markmaaktmedia.markmaaktai.ui.components.PillSpinner
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
import nl.markmaaktmedia.markmaaktai.ui.theme.SquircleShape

/**
 * The input bar.
 *
 * Everything in the row is centred on the same axis: each control sits in an
 * identical 44dp box and the text sits in a box of the same height, so a single line
 * of text lines up with the icons beside it instead of riding low. The field only
 * starts growing once there is more than one line to show.
 *
 * State lives in the outline. While the model is working the border takes the accent
 * colour and thickens slightly. An earlier version put a coloured glow behind the
 * whole bar, which bled up over the last message in the transcript and read as a
 * rendering fault rather than as a status.
 */
@Composable
fun ChatComposer(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onTogglePhoneContext: () -> Unit,
    onDictate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = state.input.isNotBlank() || state.attachmentPath != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(
            visible = state.attachmentPath != null,
            enter = expandVertically(animationSpec = MarkMotion.sizeSpring()) + fadeIn(),
            exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) + fadeOut(),
        ) {
            AttachmentPreview(
                path = state.attachmentPath.orEmpty(),
                onRemove = onRemoveAttachment,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerToggle(
                icon = MarkIcons.Web,
                label = stringResource(R.string.chat_web_search),
                active = state.webSearchEnabled,
                onClick = onToggleWebSearch,
            )
            ComposerToggle(
                icon = MarkIcons.Phone,
                label = stringResource(R.string.chat_phone_context),
                active = state.phoneContextEnabled,
                onClick = onTogglePhoneContext,
            )
        }

        val busy = state.isGenerating || state.isListening
        val outline by animateColorAsState(
            targetValue = if (busy) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
            animationSpec = MarkMotion.colourSpec(),
            label = "composerOutline",
        )
        val outlineWidth by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (busy) 2.dp else 1.dp,
            animationSpec = MarkMotion.springy(),
            label = "composerOutlineWidth",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ComposerShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(width = outlineWidth, color = outline, shape = ComposerShape)
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MarkIconButton(
                icon = MarkIcons.AddPhoto,
                contentDescription = stringResource(R.string.chat_attach_photo),
                onClick = onAttach,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.input.isEmpty() && state.partialSpeech.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_input_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = if (state.isListening && state.partialSpeech.isNotEmpty()) {
                        state.partialSpeech
                    } else {
                        state.input
                    },
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    enabled = !state.isListening,
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

            SendButton(
                generating = state.isGenerating,
                enabled = canSend,
                onSend = onSend,
                onStop = onStop,
            )
        }
    }
}

/**
 * One button that is both send and stop.
 *
 * Two buttons that swap places would move the target out from under a thumb already
 * on its way down. This one stays put and changes what it is, with the icon cross
 * fading and the colour following the state.
 */
@Composable
private fun SendButton(
    generating: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val active = generating || enabled
    val container by animateColorAsState(
        targetValue = when {
            generating -> MaterialTheme.colorScheme.errorContainer
            enabled -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = MarkMotion.colourSpec(),
        label = "sendContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            generating -> MaterialTheme.colorScheme.onErrorContainer
            enabled -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MarkMotion.colourSpec(),
        label = "sendContent",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.88f,
        animationSpec = MarkMotion.springy(),
        label = "sendScale",
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(PillShape)
            .background(container)
            .bouncyClickable(enabled = active) { if (generating) onStop() else onSend() },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = generating,
            transitionSpec = {
                (scaleIn(animationSpec = MarkMotion.springy()) + fadeIn())
                    .togetherWith(scaleOut(targetScale = 0.7f) + fadeOut())
            },
            label = "sendIcon",
        ) { isGenerating ->
            Icon(
                painter = if (isGenerating) MarkIcons.Stop else MarkIcons.Send,
                contentDescription = stringResource(
                    if (isGenerating) R.string.chat_stop else R.string.chat_send
                ),
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A small switch that says what it does rather than being an unlabelled icon. */
@Composable
private fun ComposerToggle(
    icon: Painter,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MarkMotion.colourSpec(),
        label = "toggleContainer",
    )
    val content by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MarkMotion.colourSpec(),
        label = "toggleContent",
    )

    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(container)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
private fun AttachmentPreview(path: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(SquircleShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        AsyncImage(
            model = path,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            contentScale = ContentScale.Crop,
        )
        MarkIconButton(
            icon = MarkIcons.Close,
            contentDescription = stringResource(R.string.chat_remove_attachment),
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp),
            size = 26,
            iconSize = 14,
            tint = Color.White,
            background = Color.Black.copy(alpha = 0.55f),
        )
    }
}

/**
 * A pill while it holds one line, and only rounds down to a squircle once the text
 * has grown tall enough that a full pill would bow out at the sides.
 */
private val ComposerShape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)

/** The line above the composer that says what the model is busy with. */
@Composable
fun WorkStatusLine(state: ChatUiState, modifier: Modifier = Modifier) {
    val label = when {
        state.isListening -> stringResource(R.string.chat_listening)
        state.stage == WorkStage.Searching -> stringResource(R.string.chat_searching_web)
        state.stage == WorkStage.ReadingPhone -> stringResource(R.string.chat_reading_notifications)
        state.stage == WorkStage.LoadingModel -> stringResource(R.string.chat_thinking)
        state.stage == WorkStage.Thinking -> stringResource(R.string.chat_thinking)
        else -> null
    }

    AnimatedVisibility(
        visible = label != null,
        enter = fadeIn() + expandVertically(animationSpec = MarkMotion.sizeSpring()),
        exit = fadeOut() + shrinkVertically(animationSpec = MarkMotion.sizeSpring()),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillSpinner(size = 18.dp)
            Text(
                text = label.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
