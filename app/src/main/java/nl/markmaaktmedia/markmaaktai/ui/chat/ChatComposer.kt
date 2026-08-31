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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ui.components.PillSpinner
import nl.markmaaktmedia.markmaaktai.ui.components.ambientGlowBehind
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.ChipSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
import nl.markmaaktmedia.markmaaktai.ui.theme.SquircleShape

/**
 * The input bar.
 *
 * The glow behind it is the app's one piece of ambient motion, and it is tied to real
 * work: it appears while the model is running and is completely still otherwise. An
 * animation that plays all the time stops carrying information, so this one only ever
 * means "something is happening".
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
            .ambientGlowBehind(
                active = state.isGenerating || state.isListening,
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary,
                ),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                icon = Icons.Rounded.Public,
                label = stringResource(R.string.chat_web_search),
                active = state.webSearchEnabled,
                onClick = onToggleWebSearch,
            )
            ComposerToggle(
                icon = Icons.Rounded.PhoneAndroid,
                label = stringResource(R.string.chat_phone_context),
                active = state.phoneContextEnabled,
                onClick = onTogglePhoneContext,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleShape(30.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = 1.dp,
                    color = if (state.isGenerating) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = SquircleShape(30.dp),
                )
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ComposerIconButton(
                icon = Icons.Rounded.AddPhotoAlternate,
                contentDescription = stringResource(R.string.chat_attach_photo),
                onClick = onAttach,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
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
                        .heightIn(max = 160.dp),
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.primary
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Default,
                    ),
                    enabled = !state.isListening,
                )
            }

            ComposerIconButton(
                icon = Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.chat_voice_input),
                onClick = onDictate,
                tint = if (state.isListening) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
 * Two buttons that swap places would move the target out from under a thumb that is
 * already on its way down. This one stays put and changes what it is, with the icon
 * cross fading and the colour following the state.
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
        targetValue = if (active) 1f else 0.9f,
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
                imageVector = if (isGenerating) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(
                    if (isGenerating) R.string.chat_stop else R.string.chat_send
                ),
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ComposerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(PillShape)
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

/** A small switch that says what it does rather than being an unlabelled icon. */
@Composable
private fun ComposerToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MarkMotion.colourSpec(),
        label = "toggleContainer",
    )
    val content by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MarkMotion.colourSpec(),
        label = "toggleContent",
    )

    Row(
        modifier = Modifier
            .clip(ChipSquircle)
            .background(container)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
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
            .clip(SquircleShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        AsyncImage(
            model = path,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                .bouncyClickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.chat_remove_attachment),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

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
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
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
