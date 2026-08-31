package nl.markmaaktmedia.markmaaktai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import nl.markmaaktmedia.markmaaktai.BuildConfig
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.data.prefs.ThemeMode
import nl.markmaaktmedia.markmaaktai.ui.components.SectionHeader
import nl.markmaaktmedia.markmaaktai.ui.components.SettingsGroup
import nl.markmaaktmedia.markmaaktai.ui.components.SoftDivider
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.update.UpdateCard

@Composable
fun SettingsScreen(
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // The three system permissions can be changed outside the app, so they are
    // re-read every time this screen comes back to the front.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
        }

        item {
            UpdateCard(
                state = updateState,
                onCheck = viewModel::checkForUpdates,
                onDownload = viewModel::downloadUpdate,
                onInstall = viewModel::installUpdate,
                onOpenPage = { viewModel.openUrl(viewModel.releasesUrl) },
                onDismiss = viewModel::dismissUpdate,
            )
        }

        item { SectionHeader(stringResource(R.string.settings_section_look)) }
        item {
            SettingsGroup {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    VSpace(10)
                    SegmentedPillSwitch(
                        options = listOf(
                            stringResource(R.string.settings_theme_system),
                            stringResource(R.string.settings_theme_light),
                            stringResource(R.string.settings_theme_dark),
                        ),
                        selectedIndex = when (settings.themeMode) {
                            ThemeMode.SYSTEM -> 0
                            ThemeMode.LIGHT -> 1
                            ThemeMode.DARK -> 2
                        },
                        onSelect = { index ->
                            viewModel.setThemeMode(
                                when (index) {
                                    1 -> ThemeMode.LIGHT
                                    2 -> ThemeMode.DARK
                                    else -> ThemeMode.SYSTEM
                                }
                            )
                        },
                    )
                }
                SoftDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_desc),
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                SoftDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_pure_black),
                    subtitle = stringResource(R.string.settings_pure_black_desc),
                    checked = settings.pureBlack,
                    onCheckedChange = viewModel::setPureBlack,
                    help = stringResource(R.string.help_pure_black),
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_section_ai)) }
        item {
            SettingsGroup {
                ActionRow(
                    title = stringResource(R.string.models_title),
                    subtitle = settings.textModelPath.substringAfterLast('/')
                        .ifBlank { stringResource(R.string.models_not_installed) },
                    icon = Icons.Rounded.Memory,
                    onClick = onOpenModels,
                )
                SoftDivider()
                SliderRow(
                    title = stringResource(R.string.settings_temperature),
                    valueLabel = String.format("%.1f", settings.temperature),
                    value = settings.temperature,
                    range = 0.1f..1.5f,
                    steps = 13,
                    help = stringResource(R.string.help_temperature),
                    onValueChange = viewModel::setTemperature,
                )
                SoftDivider()
                SliderRow(
                    title = stringResource(R.string.settings_max_tokens),
                    valueLabel = settings.maxTokens.toString(),
                    value = settings.maxTokens.toFloat(),
                    range = 256f..2048f,
                    steps = 6,
                    help = stringResource(R.string.help_max_tokens),
                    onValueChange = { viewModel.setMaxTokens(it.toInt()) },
                )
                SoftDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_gpu),
                    subtitle = stringResource(R.string.settings_gpu_desc),
                    checked = settings.useGpu,
                    onCheckedChange = viewModel::setUseGpu,
                    help = stringResource(R.string.help_gpu),
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_section_search)) }
        item {
            SettingsGroup {
                TextFieldRow(
                    title = stringResource(R.string.settings_searxng_url),
                    value = settings.searxngUrl,
                    placeholder = BuildConfig.DEFAULT_SEARXNG_URL,
                    onValueChange = viewModel::setSearxngUrl,
                    help = stringResource(R.string.settings_searxng_url_desc),
                )
                SoftDivider()
                TextFieldRow(
                    title = stringResource(R.string.settings_brave_key),
                    value = settings.braveApiKey,
                    placeholder = stringResource(R.string.settings_brave_key_desc),
                    onValueChange = viewModel::setBraveApiKey,
                    help = stringResource(R.string.settings_brave_key_desc),
                )
                SoftDivider()
                SliderRow(
                    title = stringResource(R.string.settings_search_results),
                    valueLabel = settings.searchResultCount.toString(),
                    value = settings.searchResultCount.toFloat(),
                    range = 2f..8f,
                    steps = 5,
                    onValueChange = { viewModel.setSearchResultCount(it.toInt()) },
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_section_notifications)) }
        item {
            SettingsGroup {
                ActionRow(
                    title = stringResource(R.string.settings_notification_access),
                    subtitle = stringResource(R.string.settings_notification_access_desc),
                    trailing = accessLabel(access.notificationListener),
                    icon = Icons.Rounded.NotificationsActive,
                    onClick = viewModel::openNotificationAccess,
                )
                SoftDivider()
                ActionRow(
                    title = stringResource(R.string.settings_battery),
                    subtitle = stringResource(R.string.settings_battery_desc),
                    trailing = accessLabel(access.batteryUnrestricted),
                    icon = Icons.Rounded.BatteryStd,
                    onClick = viewModel::requestBatteryExemption,
                    help = stringResource(R.string.help_battery),
                )
                SoftDivider()
                ActionRow(
                    title = stringResource(R.string.settings_assistant),
                    subtitle = stringResource(R.string.settings_assistant_desc),
                    trailing = accessLabel(access.isDefaultAssistant),
                    icon = Icons.Rounded.RecordVoiceOver,
                    onClick = viewModel::openAssistantSettings,
                    help = stringResource(R.string.help_assistant),
                )
                SoftDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_section_notifications),
                    checked = settings.notificationIntelligence,
                    onCheckedChange = viewModel::setNotificationIntelligence,
                )
                SoftDivider()
                SliderRow(
                    title = stringResource(R.string.settings_min_words),
                    valueLabel = stringResource(R.string.settings_min_words_value, settings.minWordCount),
                    value = settings.minWordCount.toFloat(),
                    range = 2f..20f,
                    steps = 17,
                    help = stringResource(R.string.help_min_words),
                    onValueChange = { viewModel.setMinWordCount(it.toInt()) },
                )
                SoftDivider()
                SliderRow(
                    title = stringResource(R.string.settings_cluster_size),
                    valueLabel = stringResource(R.string.settings_cluster_size_value, settings.clusterSize),
                    value = settings.clusterSize.toFloat(),
                    range = 2f..10f,
                    steps = 7,
                    help = stringResource(R.string.help_cluster_size),
                    onValueChange = { viewModel.setClusterSize(it.toInt()) },
                )
                SoftDivider()
                SliderRow(
                    title = stringResource(R.string.settings_cluster_window),
                    valueLabel = stringResource(
                        R.string.settings_cluster_window_value,
                        settings.clusterWindowMinutes,
                    ),
                    value = settings.clusterWindowMinutes.toFloat(),
                    range = 1f..15f,
                    steps = 13,
                    onValueChange = { viewModel.setClusterWindow(it.toInt()) },
                )
                SoftDivider()
                SwitchRow(
                    title = stringResource(R.string.digest_urgent),
                    checked = settings.urgentAlerts,
                    onCheckedChange = viewModel::setUrgentAlerts,
                    help = stringResource(R.string.help_urgent),
                )
                SoftDivider()
                SliderRow(
                    title = stringResource(R.string.settings_retention),
                    valueLabel = stringResource(R.string.settings_retention_value, settings.retentionDays),
                    value = settings.retentionDays.toFloat(),
                    range = 7f..180f,
                    steps = 0,
                    help = stringResource(R.string.help_retention),
                    onValueChange = { viewModel.setRetentionDays(it.toInt()) },
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_section_about)) }
        item {
            SettingsGroup {
                ActionRow(
                    title = stringResource(R.string.settings_check_updates),
                    subtitle = stringResource(R.string.settings_version, viewModel.versionName),
                    icon = Icons.Rounded.SystemUpdate,
                    onClick = viewModel::checkForUpdates,
                )
                SoftDivider()
                ActionRow(
                    title = stringResource(R.string.settings_source),
                    subtitle = "github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}",
                    icon = Icons.Rounded.Code,
                    onClick = {
                        viewModel.openUrl(
                            "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}"
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun accessLabel(granted: Boolean): String = stringResource(
    if (granted) R.string.onboarding_granted else R.string.onboarding_grant
)
