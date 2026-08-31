package nl.markmaaktmedia.markmaaktai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.markmaaktai.ui.theme.CardSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.ChipSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape

/** The small round count on a navigation icon. */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError,
) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(containerColor)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A pill label: category, state, count. */
@Composable
fun PillBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

/**
 * The question mark next to a setting that needs a sentence of explanation.
 *
 * Tapping it opens the sentence in place rather than a dialog, so reading it does not
 * cost you your place on the page. Settings that explain themselves do not get one:
 * an app where every row has a help button is an app that failed to name its rows.
 */
@Composable
fun HelpTip(
    text: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = MarkMotion.springy(),
        label = "helpTipRotation",
    )

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .bouncyClickable(onClickLabel = text) { open = !open }
                .graphicsLayer { rotationZ = rotation },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (open) Icons.Rounded.Close else Icons.Rounded.QuestionMark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.expandVertically(animationSpec = MarkMotion.sizeSpring()) +
                fadeIn(animationSpec = MarkMotion.fadeSpec()),
            exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) +
                fadeOut(animationSpec = MarkMotion.fadeSpec()),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PillMark(size = 54.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/**
 * Swipe an item away, and let the list close the gap.
 *
 * Two animations, and both matter. The row slides out under the finger, which is the
 * gesture; then the space it left collapses on a spring, which is the list reacting.
 * Removing the row the instant the swipe finishes skips the second half and makes
 * everything below jump, so the removal is held until the collapse has played.
 */
@Composable
fun <T> SwipeToDelete(
    item: T,
    onDelete: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = MaterialTheme.colorScheme.errorContainer,
    iconTint: Color = MaterialTheme.colorScheme.onErrorContainer,
    content: @Composable () -> Unit,
) {
    var removed by remember(item) { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && enabled) {
                removed = true
                true
            } else {
                false
            }
        },
        positionalThreshold = { distance -> distance * 0.45f },
    )

    LaunchedEffect(removed) {
        if (removed) {
            // Long enough for the collapse to read, short enough not to feel stuck.
            kotlinx.coroutines.delay(220)
            onDelete(item)
        }
    }

    AnimatedVisibility(
        visible = !removed,
        exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) +
            fadeOut(animationSpec = MarkMotion.fadeSpec()),
        modifier = modifier,
    ) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = enabled,
            backgroundContent = {
                val progress = dismissState.progress.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CardSquircle)
                        .background(background)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                // The bin grows as the swipe commits, so the point of
                                // no return is visible before the finger lifts.
                                val scale = 0.7f + progress * 0.5f
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                }
            },
            content = { content() },
        )
    }
}

/** A row of quick suggestions under an empty chat. */
@Composable
fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(ChipSquircle)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = ChipSquircle,
            )
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A thin divider that does not fight the surface it sits on. */
@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

/** Vertical breathing room, so a Column does not need a Spacer spelled out. */
@Composable
fun VSpace(height: Int) = Spacer(Modifier.height(height.dp))

/** The rounded container used for a group of settings rows. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardSquircle)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = content,
    )
}
