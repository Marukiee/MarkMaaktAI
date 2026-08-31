package nl.markmaaktmedia.markmaaktai.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
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
import nl.markmaaktmedia.markmaaktai.ui.components.PillMark
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.ChipSquircle
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
    if (fromUser) {
        UserMessage(message, modifier)
    } else {
        AssistantMessage(message, isStreaming, onOpenSource, modifier)
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
            AsyncImage(
                model = message.imagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .sizeIn(maxWidth = 220.dp, maxHeight = 260.dp)
                    .clip(SquircleShape(22.dp))
                    .padding(bottom = 6.dp),
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillMark(
            size = 22.dp,
            modifier = Modifier.padding(top = 3.dp),
            color = if (message.isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (message.content.isBlank() && isStreaming) {
                ThinkingDots()
            } else {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }

            if (message.sources.isNotEmpty()) {
                SourceRow(sources = message.sources, onOpenSource = onOpenSource)
            }

            if (!isStreaming && message.content.isNotBlank() && !message.isError) {
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
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sources.forEachIndexed { index, source ->
                Row(
                    modifier = Modifier
                        .clip(ChipSquircle)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .bouncyClickable { onOpenSource(source.url) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .width(180.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${index + 1}. ${source.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
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
            imageVector = Icons.Rounded.ContentCopy,
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
