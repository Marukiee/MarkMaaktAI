package nl.markmaaktmedia.markmaaktai.ui.onboarding

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ai.ModelCatalog
import nl.markmaaktmedia.markmaaktai.ai.ModelRole
import nl.markmaaktmedia.markmaaktai.ui.components.GlowContainer
import nl.markmaaktmedia.markmaaktai.ui.components.PillMark
import nl.markmaaktmedia.markmaaktai.ui.components.PillSpinner
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.models.ModelsViewModel
import nl.markmaaktmedia.markmaaktai.ui.models.formatSize
import nl.markmaaktmedia.markmaaktai.ui.settings.SettingsViewModel
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape

/**
 * Five short screens, and only the first one is compulsory reading.
 *
 * Every permission page can be skipped, because each feature degrades to something
 * that still works: no notification access means no summaries but the chat is fine,
 * no model means OCR still reads your screenshots. Blocking the app behind a wall of
 * grants for features the user has not seen yet is how good apps get uninstalled on
 * the first run.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    modelsViewModel: ModelsViewModel = hiltViewModel(),
) {
    var step by remember { mutableIntStateOf(0) }
    val access by settingsViewModel.access.collectAsStateWithLifecycle()
    val downloads by modelsViewModel.downloads.collectAsStateWithLifecycle()
    val recommended = ModelCatalog.recommendedText

    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { settingsViewModel.refreshAccess() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { modelsViewModel.importFromUri(it, ModelRole.TEXT) } }

    val steps = listOf(
        OnboardingStep(
            title = stringResource(R.string.onboarding_welcome_title),
            body = stringResource(R.string.onboarding_welcome_body),
        ),
        OnboardingStep(
            title = stringResource(R.string.onboarding_privacy_title),
            body = stringResource(R.string.onboarding_privacy_body),
        ),
        OnboardingStep(
            title = stringResource(R.string.onboarding_model_title),
            body = stringResource(R.string.onboarding_model_body),
        ),
        OnboardingStep(
            title = stringResource(R.string.onboarding_notifications_title),
            body = stringResource(R.string.onboarding_notifications_body),
        ),
        OnboardingStep(
            title = stringResource(R.string.onboarding_battery_title),
            body = stringResource(R.string.onboarding_battery_body),
        ),
        OnboardingStep(
            title = stringResource(R.string.onboarding_assistant_title),
            body = stringResource(R.string.onboarding_assistant_body),
        ),
    )

    GlowContainer(
        active = true,
        intensity = 0.35f,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (slideInHorizontally { it / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                            .using(SizeTransform(clip = false))
                    },
                    label = "onboardingStep",
                ) { index ->
                    val current = steps[index]
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PillMark(size = 64.dp)
                        VSpace(28)
                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center,
                        )
                        VSpace(12)
                        Text(
                            text = current.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        when (index) {
                            2 -> {
                                VSpace(24)
                                val progress = downloads[recommended.id]
                                if (progress != null) {
                                    PillSpinner(size = 34.dp)
                                    VSpace(10)
                                    Text(
                                        text = stringResource(
                                            R.string.models_downloading,
                                            (progress.fraction * 100).toInt(),
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    PrimaryButton(
                                        label = stringResource(
                                            R.string.onboarding_download_model,
                                            recommended.displayName,
                                            formatSize(recommended.sizeBytes),
                                        ),
                                        onClick = { modelsViewModel.download(recommended) },
                                    )
                                    VSpace(8)
                                    Text(
                                        text = stringResource(R.string.onboarding_no_account),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                    VSpace(14)
                                    SecondaryButton(
                                        label = stringResource(R.string.onboarding_have_file),
                                        onClick = { filePicker.launch(arrayOf("*/*")) },
                                    )
                                }
                                VSpace(14)
                                Text(
                                    text = stringResource(R.string.onboarding_works_without),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            3 -> {
                                VSpace(24)
                                PrimaryButton(
                                    label = grantLabel(access.notificationListener),
                                    onClick = {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        settingsViewModel.openNotificationAccess()
                                    },
                                )
                            }

                            4 -> {
                                VSpace(24)
                                PrimaryButton(
                                    label = grantLabel(access.batteryUnrestricted),
                                    onClick = settingsViewModel::requestBatteryExemption,
                                )
                            }

                            5 -> {
                                VSpace(24)
                                PrimaryButton(
                                    label = grantLabel(access.isDefaultAssistant),
                                    onClick = settingsViewModel::openAssistantSettings,
                                )
                            }

                            else -> Unit
                        }
                    }
                }
            }

            StepDots(count = steps.size, current = step)
            VSpace(20)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .bouncyClickable(onClick = onFinished)
                        .padding(12.dp),
                )
                PrimaryButton(
                    label = stringResource(
                        if (step == steps.lastIndex) R.string.onboarding_start
                        else R.string.onboarding_next
                    ),
                    onClick = {
                        if (step == steps.lastIndex) onFinished() else step++
                    },
                )
            }
        }
    }
}

private data class OnboardingStep(val title: String, val body: String)

@Composable
private fun grantLabel(granted: Boolean): String = stringResource(
    if (granted) R.string.onboarding_granted else R.string.onboarding_grant
)

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.primary)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Progress dots, where the current one is a stretched pill rather than a bigger dot. */
@Composable
private fun StepDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            val active = index == current
            val width by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (active) 22.dp else 7.dp,
                animationSpec = nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion.springy(),
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
