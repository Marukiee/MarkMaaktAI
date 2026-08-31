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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ui.components.MarkIconButton
import nl.markmaaktmedia.markmaaktai.ui.components.PillSpinner
import nl.markmaaktmedia.markmaaktai.ui.components.PrimaryPillButton
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.theme.CardSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
import nl.markmaaktmedia.markmaaktai.update.UpdateState

/**
 * The bar that appears at the top of the app when a new version is out.
 *
 * It only ever shows when there is something to do, and one button does the whole
 * job: it downloads the APK and hands it straight to the installer. Sending someone
 * to a releases page to find the right file, unzip a build artifact and work out
 * which of two downloads is the installable one is not an update flow, it is a
 * scavenger hunt.
 *
 * Dismissing is remembered per version, so saying no once does not mean being asked
 * again on the next launch, and a genuinely newer release still gets through.
 */
@Composable
fun TopUpdateBanner(
    modifier: Modifier = Modifier,
    viewModel: UpdateBannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dismissedTag by viewModel.dismissedTag.collectAsStateWithLifecycle()

    val release = when (val current = state) {
        is UpdateState.Available -> current.release
        is UpdateState.Downloading -> current.release
        is UpdateState.ReadyToInstall -> current.release
        else -> null
    }
    val visible = release != null && release.tag != dismissedTag

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = MarkMotion.sizeSpring()) + fadeIn(),
        exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) + fadeOut(),
        modifier = modifier,
    ) {
        if (release == null) return@AnimatedVisibility

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(CardSquircle)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = MarkIcons.Update,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.update_available_title, release.versionName),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.update_available_body, viewModel.currentVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                when (val current = state) {
                    is UpdateState.Downloading -> PillSpinner(
                        size = 24.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )

                    is UpdateState.ReadyToInstall -> PrimaryPillButton(
                        label = stringResource(R.string.update_install),
                        onClick = { viewModel.install(current.filePath) },
                    )

                    else -> PrimaryPillButton(
                        label = stringResource(R.string.update_download),
                        onClick = { viewModel.downloadAndInstall() },
                    )
                }

                MarkIconButton(
                    icon = MarkIcons.Close,
                    contentDescription = stringResource(R.string.update_later),
                    onClick = { viewModel.dismiss(release.tag) },
                    size = 36,
                    iconSize = 16,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            val downloading = state as? UpdateState.Downloading
            if (downloading != null) {
                VSpace(10)
                LinearProgressIndicator(
                    progress = { downloading.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(PillShape),
                )
                VSpace(6)
                Text(
                    text = stringResource(
                        R.string.update_downloading,
                        (downloading.progress * 100).toInt(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            if (!viewModel.canInstall && state is UpdateState.Available) {
                VSpace(8)
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.update_allow_install),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
