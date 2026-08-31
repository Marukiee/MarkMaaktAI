package nl.markmaaktmedia.markmaaktai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
import nl.markmaaktmedia.markmaaktai.ui.theme.SquircleShape

/**
 * The app's own dialog.
 *
 * Material's AlertDialog paints itself on `surfaceContainerHigh` with its own
 * elevation tint, which on the pure black theme comes out lighter than everything
 * around it and reads as a light-mode panel dropped into a dark app. This one takes
 * its colours from the same roles as the rest of the screens, so it belongs to the
 * theme rather than sitting on top of it.
 *
 * It also arrives properly: a spring-scaled entry rather than the platform fade, in
 * the same motion language as everything else that appears.
 */
@Composable
fun MarkDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    body: String? = null,
    dismissOnOutsideTap: Boolean = true,
    actions: @Composable () -> Unit = {},
    content: (@Composable () -> Unit)? = null,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.88f,
        animationSpec = MarkMotion.springy(),
        label = "dialogScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MarkMotion.fadeSpec(),
        label = "dialogAlpha",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = dismissOnOutsideTap,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(SquircleShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(PillShape)
                        .background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp),
                    )
                }
                VSpace(16)
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            if (body != null) {
                VSpace(10)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }

            if (content != null) {
                VSpace(16)
                content()
            }

            VSpace(24)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                actions()
            }
        }
    }
}

/** The dialog shown when something failed, with the detail kept readable. */
@Composable
fun MarkErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    MarkDialog(
        title = title,
        body = message,
        icon = MarkIcons.Error,
        iconTint = MaterialTheme.colorScheme.error,
        onDismiss = onDismiss,
        actions = {
            if (retryLabel != null && onRetry != null) {
                SecondaryPillButton(label = retryLabel, onClick = { onDismiss(); onRetry() })
            }
            PrimaryPillButton(label = confirmLabel, onClick = onDismiss)
        },
    )
}

/** Confirmation for something that cannot be undone. */
@Composable
fun MarkConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    MarkDialog(
        title = title,
        body = body,
        icon = if (destructive) MarkIcons.Delete else MarkIcons.Info,
        iconTint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        onDismiss = onDismiss,
        actions = {
            SecondaryPillButton(label = cancelLabel, onClick = onDismiss)
            PrimaryPillButton(
                label = confirmLabel,
                onClick = { onDismiss(); onConfirm() },
                container = if (destructive) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primary,
                content = if (destructive) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimary,
            )
        },
    )
}
