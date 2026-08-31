package nl.markmaaktmedia.markmaaktai.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.data.db.MessageEntity
import nl.markmaaktmedia.markmaaktai.data.db.WebSource
import nl.markmaaktmedia.markmaaktai.data.repository.ChatRepository
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.ChipSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.SquircleShape

/**
 * A message.
 *
 * The two sides are shaped differently on purpose. What you said is a contained
 * bubble pushed to the right, because it is a thing you handed over. The answer is
 * set as plain text on the background with the app mark beside it, because it is the
 * app talking, and boxing it would make a long answer read as a wall.
 */
@Composable
fun MessageBubble(
    message: MessageEntity,
    isStreaming: Boolean,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fromUser = message.role == ChatRepository.ROLE_USER
    // Anything the model wrote is worth being able to select and copy out, and so is
    // what you asked. The copy button stays for the whole answer in one tap.
    androidx.compose.foundation.text.selection.SelectionContainer {
        if (fromUser) {
            UserMessage(message, modifier)
        } else {
            AssistantMessage(message, isStreaming, onOpenSource, modifier)
        }
    }
}

@Composable
private fun UserMessage(message: MessageEntity, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (message.imagePath != null) {
            // Padding after the clip trims the picture instead of spacing it, which
            // is what was shaving the bottom edge off every attachment.
            AsyncImage(
                model = message.imagePath,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .sizeIn(maxWidth = 240.dp, maxHeight = 300.dp)
                    .clip(SquircleShape(22.dp)),
            )
        }
        if (message.content.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(UserBubbleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AssistantMessage(
    message: MessageEntity,
    isStreaming: Boolean,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (message.content.isBlank() && isStreaming) {
                ThinkingDots()
            } else {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (message.sources.isNotEmpty()) {
                SourceRow(sources = message.sources, onOpenSource = onOpenSource)
            }

            if (!isStreaming && message.content.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MessageAction(
                        label = stringResource(R.string.chat_copy),
                        onClick = { clipboard.setText(AnnotatedString(message.content)) },
                    )
                }
            }
        }
    }
}

/**
 * Three dots that rise in sequence while the first token is still coming.
 *
 * Deliberately not the capsule spinner. The spinner means the app is busy with
 * something; this means a specific answer is on its way, and keeping the two apart
 * means neither has to be explained.
 */
@Composable
private fun ThinkingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "thinkingDots")

    Row(
        modifier = modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 560,
                        delayMillis = index * 130,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        translationY = -offset * 5f
                        alpha = 0.45f + offset * 0.55f
                    }
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun SourceRow(sources: List<WebSource>, onOpenSource: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.chat_sources),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources) { source ->
                Row(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .bouncyClickable { onOpenSource(source.url) }
                        .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                        .widthIn(max = 200.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painter = MarkIcons.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = source.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageAction(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(ChipSquircle)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            painter = MarkIcons.Copy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Rounded on three corners, tucked in on the one nearest the sender. */
private val UserBubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomStart = 24.dp,
    bottomEnd = 8.dp,
)
