package nl.markmaaktmedia.markmaaktai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion

/**
 * A tap that answers back.
 *
 * The element dips under the finger and springs out again with a touch of overshoot,
 * which is what makes a flat rectangle feel like something that was actually pressed.
 * The scale runs on a spring rather than a tween on purpose: a spring keeps its
 * momentum when the finger lifts mid-animation, so a quick tap and a slow press both
 * look right instead of only the one that was designed for.
 *
 * The ripple stays. It is what tells you where the touch landed, and losing it for
 * the sake of the scale would trade information for polish.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = MarkMotion.PressedScale,
    withHaptics: Boolean = false,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = MarkMotion.bouncy(),
        label = "pressScale",
    )

    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interactionSource,
        indication = ripple(),
        enabled = enabled,
        role = role,
        onClickLabel = onClickLabel,
    ) {
        if (withHaptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
    }
}

/** The same press feedback without a click, for a container that only wants the dip. */
fun Modifier.pressScale(pressed: Boolean, pressedScale: Float = MarkMotion.PressedScale): Modifier =
    composed {
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressedScale else 1f,
            animationSpec = MarkMotion.bouncy(),
            label = "pressScaleOnly",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }

/**
 * A surface with the app's press feedback built in.
 *
 * Used instead of Material's Card wherever the card is tappable, so every tappable
 * surface in the app dips by the same amount and settles on the same spring.
 */
@Composable
fun BouncySurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = modifier
        .clip(shape)
        .background(color)

    Box(
        modifier = if (onClick != null) {
            base.bouncyClickable(enabled = enabled, onClick = onClick)
        } else {
            base
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = { content() })
    }
}
