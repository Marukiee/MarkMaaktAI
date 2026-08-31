package nl.markmaaktmedia.markmaaktai.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ui.components.PillSpinner
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.CardSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
import nl.markmaaktmedia.markmaaktai.update.UpdateState

/**
 * The update banner.
 *
 * Only ever shown when there is something to say. An app that permanently reserves a
 * strip of the settings screen for "you are up to date" is spending attention on the
 * one outcome nobody needs to be told about.
 */
@Composable
fun UpdateCard(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = state !is UpdateState.Idle

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = MarkMotion.sizeSpring()) + fadeIn(),
        exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) + fadeOut(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardSquircle)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(16.dp),
        ) {
            when (state) {
                is UpdateState.Checking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PillSpinner(size = 20.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        text = stringResource(R.string.update_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                is UpdateState.UpToDate -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.update_up_to_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    UpdateButton(stringResource(R.string.generic_close), onDismiss)
                }

                is UpdateState.Available -> Column {
                    Text(
                        text = stringResource(R.string.update_available_title, state.release.versionName),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (state.release.changelog.isNotBlank()) {
                        VSpace(6)
                        Text(
                            text = state.release.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    VSpace(12)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.release.apkUrl != null) {
                            UpdateButton(stringResource(R.string.update_download), onDownload, primary = true)
                        }
                        UpdateButton(stringResource(R.string.update_open_release), onOpenPage)
                        UpdateButton(stringResource(R.string.update_later), onDismiss)
                    }
                }

                is UpdateState.Downloading -> Column {
                    Text(
                        text = stringResource(
                            R.string.update_downloading,
                            (state.progress * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    VSpace(10)
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(PillShape),
                    )
                }

                is UpdateState.ReadyToInstall -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.update_available_title, state.release.versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    UpdateButton(stringResource(R.string.update_install), onInstall, primary = true)
                }

                is UpdateState.Failed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = state.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    UpdateButton(stringResource(R.string.generic_retry), onCheck)
                }

                UpdateState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun UpdateButton(label: String, onClick: () -> Unit, primary: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(
                if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
