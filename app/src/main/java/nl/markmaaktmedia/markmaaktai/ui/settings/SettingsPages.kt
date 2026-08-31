package nl.markmaaktmedia.markmaaktai.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.markmaaktmedia.markmaaktai.BuildConfig
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.data.prefs.ThemeMode
import nl.markmaaktmedia.markmaaktai.data.prefs.UserSettings
import nl.markmaaktmedia.markmaaktai.ui.components.GroupedRow
import nl.markmaaktmedia.markmaaktai.ui.components.SettingsGroup
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import java.util.Locale

/** The body of each settings page. Kept apart so the page shell stays readable. */

@Composable
fun LookSettings(
    settings: UserSettings,
    darkPreview: Boolean,
    viewModel: SettingsViewModel,
) {
    SettingsGroup {
        GroupedRow(index = 0, total = 4) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                )
                VSpace(12)
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
        }
        GroupedRow(index = 1, total = 4) {
            ColourSeedRow(selected = settings.colourSeed, onSelect = viewModel::setColourSeed)
        }
        GroupedRow(index = 2, total = 4) {
            PaletteStyleRow(
                selected = settings.paletteStyle,
                seed = settings.colourSeed,
                dark = darkPreview,
                onSelect = viewModel::setPaletteStyle,
            )
        }
        GroupedRow(index = 3, total = 4) {
            SwitchRow(
                title = stringResource(R.string.settings_pure_black),
                subtitle = stringResource(R.string.settings_pure_black_desc),
                checked = settings.pureBlack,
                onCheckedChange = viewModel::setPureBlack,
                help = stringResource(R.string.help_pure_black),
            )
        }
    }
}

@Composable
fun AiSettings(
    settings: UserSettings,
    viewModel: SettingsViewModel,
    onOpenModels: () -> Unit,
) {
    SettingsGroup {
        GroupedRow(index = 0, total = 4, onClick = onOpenModels) {
            ActionRow(
                title = stringResource(R.string.models_title),
                subtitle = settings.textModelPath.substringAfterLast('/')
                    .ifBlank { stringResource(R.string.models_not_installed) },
                icon = MarkIcons.Model,
                onClick = onOpenModels,
            )
        }
        GroupedRow(index = 1, total = 4) {
            SliderRow(
                title = stringResource(R.string.settings_temperature),
                valueLabel = String.format(Locale.getDefault(), "%.1f", settings.temperature),
                value = settings.temperature,
                range = 0.1f..1.5f,
                steps = 13,
                help = stringResource(R.string.help_temperature),
                onValueChange = viewModel::setTemperature,
            )
        }
        GroupedRow(index = 2, total = 4) {
            SliderRow(
                title = stringResource(R.string.settings_max_tokens),
                valueLabel = settings.maxTokens.toString(),
                value = settings.maxTokens.toFloat(),
                range = 128f..768f,
                steps = 4,
                help = stringResource(R.string.help_max_tokens),
                onValueChange = { viewModel.setMaxTokens(it.toInt()) },
            )
        }
        GroupedRow(index = 3, total = 4) {
            SwitchRow(
                title = stringResource(R.string.settings_gpu),
                subtitle = stringResource(R.string.settings_gpu_desc),
                checked = settings.useGpu,
                onCheckedChange = viewModel::setUseGpu,
                help = stringResource(R.string.help_gpu),
            )
        }
    }
}

@Composable
fun SearchSettings(settings: UserSettings, viewModel: SettingsViewModel) {
    SettingsGroup {
        GroupedRow(index = 0, total = 3) {
            TextFieldRow(
                title = stringResource(R.string.settings_searxng_url),
                value = settings.searxngUrl,
                placeholder = BuildConfig.DEFAULT_SEARXNG_URL,
                onValueChange = viewModel::setSearxngUrl,
                help = stringResource(R.string.settings_searxng_url_desc),
            )
        }
        GroupedRow(index = 1, total = 3) {
            TextFieldRow(
                title = stringResource(R.string.settings_brave_key),
                value = settings.braveApiKey,
                placeholder = stringResource(R.string.settings_brave_key_hint),
                onValueChange = viewModel::setBraveApiKey,
                help = stringResource(R.string.settings_brave_key_desc),
            )
        }
        GroupedRow(index = 2, total = 3) {
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
}

@Composable
fun AccessSettings(access: SystemAccess, viewModel: SettingsViewModel) {
    SettingsGroup {
        GroupedRow(index = 0, total = 3, onClick = viewModel::openNotificationAccess) {
            ActionRow(
                title = stringResource(R.string.settings_notification_access),
                subtitle = stringResource(R.string.settings_notification_access_desc),
                trailing = accessLabel(access.notificationListener),
                icon = MarkIcons.Notifications,
                onClick = viewModel::openNotificationAccess,
            )
        }
        GroupedRow(index = 1, total = 3, onClick = viewModel::requestBatteryExemption) {
            ActionRow(
                title = stringResource(R.string.settings_battery),
                subtitle = stringResource(R.string.settings_battery_desc),
                trailing = accessLabel(access.batteryUnrestricted),
                icon = MarkIcons.Battery,
                onClick = viewModel::requestBatteryExemption,
                help = stringResource(R.string.help_battery),
            )
        }
        GroupedRow(index = 2, total = 3, onClick = viewModel::openAssistantSettings) {
            ActionRow(
                title = stringResource(R.string.settings_assistant),
                subtitle = stringResource(R.string.settings_assistant_desc),
                trailing = accessLabel(access.isDefaultAssistant),
                icon = MarkIcons.Assistant,
                onClick = viewModel::openAssistantSettings,
                help = stringResource(R.string.help_assistant),
            )
        }
    }
}

@Composable
fun NotificationSettings(settings: UserSettings, viewModel: SettingsViewModel) {
    SettingsGroup {
        GroupedRow(index = 0, total = 6) {
            SwitchRow(
                title = stringResource(R.string.settings_notification_enabled),
                subtitle = stringResource(R.string.settings_notification_enabled_desc),
                checked = settings.notificationIntelligence,
                onCheckedChange = viewModel::setNotificationIntelligence,
            )
        }
        GroupedRow(index = 1, total = 6) {
            SliderRow(
                title = stringResource(R.string.settings_min_words),
                valueLabel = stringResource(R.string.settings_min_words_value, settings.minWordCount),
                value = settings.minWordCount.toFloat(),
                range = 2f..20f,
                steps = 17,
                help = stringResource(R.string.help_min_words),
                onValueChange = { viewModel.setMinWordCount(it.toInt()) },
            )
        }
        GroupedRow(index = 2, total = 6) {
            SliderRow(
                title = stringResource(R.string.settings_cluster_size),
                valueLabel = stringResource(R.string.settings_cluster_size_value, settings.clusterSize),
                value = settings.clusterSize.toFloat(),
                range = 2f..10f,
                steps = 7,
                help = stringResource(R.string.help_cluster_size),
                onValueChange = { viewModel.setClusterSize(it.toInt()) },
            )
        }
        GroupedRow(index = 3, total = 6) {
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
        }
        GroupedRow(index = 4, total = 6) {
            SwitchRow(
                title = stringResource(R.string.settings_urgent_alerts),
                subtitle = stringResource(R.string.settings_urgent_alerts_desc),
                checked = settings.urgentAlerts,
                onCheckedChange = viewModel::setUrgentAlerts,
                help = stringResource(R.string.help_urgent),
            )
        }
        GroupedRow(index = 5, total = 6) {
            SliderRow(
                title = stringResource(R.string.settings_retention),
                valueLabel = stringResource(R.string.settings_retention_value, settings.retentionDays),
                value = settings.retentionDays.toFloat(),
                range = 7f..180f,
                help = stringResource(R.string.help_retention),
                onValueChange = { viewModel.setRetentionDays(it.toInt()) },
            )
        }
    }
}

@Composable
fun AboutSettings(versionName: String, viewModel: SettingsViewModel) {
    SettingsGroup {
        GroupedRow(index = 0, total = 3, onClick = viewModel::checkForUpdates) {
            ActionRow(
                title = stringResource(R.string.settings_check_updates),
                subtitle = stringResource(R.string.settings_version, versionName),
                icon = MarkIcons.Update,
                onClick = viewModel::checkForUpdates,
            )
        }
        GroupedRow(index = 1, total = 3, onClick = { viewModel.openUrl(viewModel.repositoryUrl) }) {
            ActionRow(
                title = stringResource(R.string.settings_source),
                subtitle = "${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}",
                icon = MarkIcons.Code,
                onClick = { viewModel.openUrl(viewModel.repositoryUrl) },
            )
        }
        GroupedRow(index = 2, total = 3, onClick = viewModel::showLastCrash) {
            ActionRow(
                title = stringResource(R.string.settings_last_crash),
                subtitle = stringResource(
                    if (viewModel.hasCrashReport) R.string.settings_last_crash_present
                    else R.string.settings_last_crash_none
                ),
                icon = MarkIcons.Error,
                onClick = viewModel::showLastCrash,
            )
        }
    }
}

@Composable
private fun accessLabel(granted: Boolean): String = stringResource(
    if (granted) R.string.onboarding_granted else R.string.onboarding_grant
)
