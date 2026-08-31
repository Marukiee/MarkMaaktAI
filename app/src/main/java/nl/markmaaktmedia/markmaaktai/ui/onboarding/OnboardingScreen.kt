package nl.markmaaktmedia.markmaaktai.ui.onboarding

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ai.ModelCatalog
import nl.markmaaktmedia.markmaaktai.ai.ModelRole
import nl.markmaaktmedia.markmaaktai.ui.components.MarkIconButton
import nl.markmaaktmedia.markmaaktai.ui.components.PillMark
import nl.markmaaktmedia.markmaaktai.ui.components.PrimaryPillButton
import nl.markmaaktmedia.markmaaktai.ui.components.SecondaryPillButton
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.models.ModelsViewModel
import nl.markmaaktmedia.markmaaktai.ui.models.formatSize
import nl.markmaaktmedia.markmaaktai.ui.settings.SettingsViewModel
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape

/**
 * Six short pages, and only the first is compulsory reading.
 *
 * Every permission page can be skipped, because each feature degrades to something
 * that still works: no notification access means no summaries but the chat is fine,
 * no model means OCR still reads your screenshots. Blocking the app behind a wall of
 * grants for features nobody has seen yet is how good apps get uninstalled on the
 * first run.
 *
 * Flat surface behind the text on purpose. An earlier version put a coloured glow
 * across the whole page and the body copy sat on top of it, which was pretty for
 * about a second and unreadable after that. The only colour now is inside the icon
 * badge, well away from anything anyone has to read.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    modelsViewModel: ModelsViewModel = hiltViewModel(),
) {
    var step by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    val access by settingsViewModel.access.collectAsStateWithLifecycle()
    val downloads by modelsViewModel.downloads.collectAsStateWithLifecycle()
    val modelState by modelsViewModel.uiState.collectAsStateWithLifecycle()
    val recommended = ModelCatalog.recommendedText

    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { settingsViewModel.refreshAccess() }

    val microphonePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { settingsViewModel.refreshAccess() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { modelsViewModel.importFromUri(it, ModelRole.TEXT) } }

    val pages = onboardingPages()

    fun goBack() {
        if (step > 0) {
            forward = false
            step--
        }
    }

    // The system back gesture walks the pages, and only leaves once there is nowhere
    // left to go back to. Dropping straight out of onboarding on the first back press
    // loses whatever was set up on the way in.
    BackHandler(enabled = step > 0) { goBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = step > 0) {
                MarkIconButton(
                    icon = MarkIcons.Back,
                    contentDescription = stringResource(R.string.generic_back),
                    onClick = { goBack() },
                    background = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            Box(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.onboarding_skip),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(PillShape)
                    .bouncyClickable(onClick = onFinished)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val direction = if (forward) 1 else -1
                (slideInHorizontally { it / 4 * direction } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 4 * direction } + fadeOut())
            },
            label = "onboardingStep",
            modifier = Modifier.weight(1f),
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                IconBadge(icon = page.icon, isMark = index == 0)
                VSpace(28)
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                VSpace(12)
                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                when (index) {
                    ModelStep -> {
                        VSpace(28)
                        val progress = downloads[recommended.id]
                        val installed = modelState.activeTextPath.isNotBlank()
                        when {
                            progress != null -> DownloadProgressBlock(progress.fraction)

                            installed -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    painter = MarkIcons.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_model_ready),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PrimaryPillButton(
                                    label = stringResource(
                                        R.string.onboarding_download_model,
                                        recommended.displayName,
                                        formatSize(recommended.sizeBytes),
                                    ),
                                    icon = MarkIcons.Download,
                                    onClick = { modelsViewModel.download(recommended) },
                                )
                                VSpace(10)
                                Text(
                                    text = stringResource(R.string.onboarding_no_account),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                VSpace(16)
                                SecondaryPillButton(
                                    label = stringResource(R.string.onboarding_have_file),
                                    icon = MarkIcons.Folder,
                                    onClick = { filePicker.launch(arrayOf("*/*")) },
                                )
                            }
                        }
                        VSpace(18)
                        Text(
                            text = stringResource(R.string.onboarding_works_without),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    NotificationStep -> GrantBlock(
                        granted = access.notificationListener,
                        onGrant = {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            settingsViewModel.openNotificationAccess()
                        },
                    )

                    VoiceStep -> GrantBlock(
                        granted = access.isDefaultAssistant,
                        onGrant = {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                            settingsViewModel.openAssistantSettings()
                        },
                    )

                    BatteryStep -> GrantBlock(
                        granted = access.batteryUnrestricted,
                        onGrant = settingsViewModel::requestBatteryExemption,
                    )

                    else -> Unit
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepDots(count = pages.size, current = step)
            VSpace(20)
            PrimaryPillButton(
                label = stringResource(
                    if (step == pages.lastIndex) R.string.onboarding_start else R.string.onboarding_next
                ),
                onClick = {
                    if (step == pages.lastIndex) {
                        onFinished()
                    } else {
                        forward = true
                        step++
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class OnboardingPage(
    val title: String,
    val body: String,
    val icon: Painter,
)

private const val ModelStep = 2
private const val NotificationStep = 3
private const val VoiceStep = 4
private const val BatteryStep = 5

@Composable
private fun onboardingPages(): List<OnboardingPage> = listOf(
    OnboardingPage(
        title = stringResource(R.string.onboarding_welcome_title),
        body = stringResource(R.string.onboarding_welcome_body),
        icon = MarkIcons.Sparkle,
    ),
    OnboardingPage(
        title = stringResource(R.string.onboarding_privacy_title),
        body = stringResource(R.string.onboarding_privacy_body),
        icon = MarkIcons.Shield,
    ),
    OnboardingPage(
        title = stringResource(R.string.onboarding_model_title),
        body = stringResource(R.string.onboarding_model_body),
        icon = MarkIcons.Model,
    ),
    OnboardingPage(
        title = stringResource(R.string.onboarding_notifications_title),
        body = stringResource(R.string.onboarding_notifications_body),
        icon = MarkIcons.Notifications,
    ),
    OnboardingPage(
        title = stringResource(R.string.onboarding_assistant_title),
        body = stringResource(R.string.onboarding_assistant_body),
        icon = MarkIcons.Assistant,
    ),
    OnboardingPage(
        title = stringResource(R.string.onboarding_battery_title),
        body = stringResource(R.string.onboarding_battery_body),
        icon = MarkIcons.Battery,
    ),
)

/** The one place colour is allowed on these pages. */
@Composable
private fun IconBadge(icon: Painter, isMark: Boolean) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (isMark) {
            PillMark(size = 56.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun GrantBlock(granted: Boolean, onGrant: () -> Unit) {
    VSpace(28)
    if (granted) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = MarkIcons.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_granted),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        PrimaryPillButton(
            label = stringResource(R.string.onboarding_grant),
            onClick = onGrant,
        )
    }
}

@Composable
private fun DownloadProgressBlock(fraction: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(PillShape),
        )
        VSpace(10)
        Text(
            text = stringResource(R.string.models_downloading, (fraction * 100).toInt()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        VSpace(4)
        Text(
            text = stringResource(R.string.onboarding_download_continues),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Progress dots, where the current one is a stretched pill rather than a bigger dot. */
@Composable
private fun StepDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 24.dp else 7.dp,
                animationSpec = MarkMotion.springy(),
                label = "stepDot",
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 7.dp)
                    .clip(PillShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}
