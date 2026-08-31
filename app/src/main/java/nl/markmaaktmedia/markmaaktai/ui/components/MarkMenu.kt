package nl.markmaaktmedia.markmaaktai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion

/**
 * A menu that grows out of the button that opened it, and shrinks back into it.
 *
 * Material's own menu fades on the way out, so it appears to come from somewhere and
 * then leave from nowhere. Scaling from the same corner in both directions is what
 * ties it to the button it belongs to.
 *
 * It is also not focusable. A focusable popup is dismissed by the system the moment
 * anything else takes focus, which meant a slow swipe towards the home screen closed
 * the menu before the gesture had even been decided. This one only closes when it is
 * asked to.
 */
@Composable
fun MarkDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /** Cleared the anchor by default, so the menu sits under the button. */
    offset: DpOffset = DpOffset(0.dp, 44.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded

    // Stays composed while the shrink plays out, then goes.
    if (!expanded && !visibleState.currentState && visibleState.isIdle) return

    Popup(
        alignment = Alignment.TopEnd,
        offset = with(androidx.compose.ui.platform.LocalDensity.current) {
            androidx.compose.ui.unit.IntOffset(offset.x.roundToPx(), offset.y.roundToPx())
        },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            // Settles rather than bounces. A menu that overshoots reads as a toy.
            enter = scaleIn(
                animationSpec = MarkMotion.spatial(),
                initialScale = 0.8f,
                transformOrigin = MenuOrigin,
            ) + fadeIn(animationSpec = MarkMotion.fadeSpec()),
            exit = scaleOut(
                animationSpec = MarkMotion.spatial(),
                targetScale = 0.7f,
                transformOrigin = MenuOrigin,
            ) + fadeOut(animationSpec = MarkMotion.fadeSpec()),
        ) {
            Column(
                modifier = modifier
                    .widthIn(min = 180.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 6.dp),
                content = content,
            )
        }
    }
}

@Composable
fun MarkMenuItem(
    label: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
        )
    }
}

/** Top right, because that is where the overflow button always is. */
private val MenuOrigin = TransformOrigin(1f, 0f)
