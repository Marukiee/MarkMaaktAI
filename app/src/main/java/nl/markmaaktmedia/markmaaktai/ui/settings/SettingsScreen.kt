package nl.markmaaktmedia.markmaaktai.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.data.prefs.ThemeMode
import nl.markmaaktmedia.markmaaktai.ui.components.GroupedRow
import nl.markmaaktmedia.markmaaktai.ui.components.MarkIconButton
import nl.markmaaktmedia.markmaaktai.ui.components.SettingsGroup
import nl.markmaaktmedia.markmaaktai.ui.components.predictiveBack
import nl.markmaaktmedia.markmaaktai.ui.components.rememberPredictiveBack
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.update.UpdateCard

/** The pages settings is split across. */
enum class SettingsPage { Root, Look, Ai, Search, Access, Notifications, About }

/**
 * Settings, split into pages.
 *
 * Everything used to live on one scroll, which meant finding the notification word
 * threshold involved passing every colour and every slider on the way down. A short
 * list of named categories is faster to scan than a long list of controls, and each
 * page is then small enough to take in at once.
 *
 * The pages are a local state machine rather than navigation destinations. They are
 * one screen's internal structure, not places you should be able to arrive at from a
 * notification, and keeping them out of the back stack means leaving settings leaves
 * settings instead of walking back up through it.
 */
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
    val systemDark = isSystemInDarkTheme()

    var page by remember { mutableStateOf(SettingsPage.Root) }

    LaunchedEffect(Unit) { viewModel.checkOnOpen() }

    // The system permissions can be changed outside the app, so they are re-read
    // every time this screen comes back to the front.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The sub pages are a hierarchy, so back should show the list underneath while
    // the finger is still moving.
    val backState = rememberPredictiveBack(enabled = page != SettingsPage.Root) {
        page = SettingsPage.Root
    }

    val crashReport by viewModel.crashReport.collectAsStateWithLifecycle()
    crashReport?.let { report ->
        nl.markmaaktmedia.markmaaktai.ui.components.MarkErrorDialog(
            title = stringResource(R.string.settings_last_crash),
            message = report.ifBlank { stringResource(R.string.settings_last_crash_none) },
            confirmLabel = stringResource(R.string.generic_close),
            copyLabel = stringResource(R.string.chat_copy),
            retryLabel = stringResource(R.string.generic_delete),
            onRetry = viewModel::clearCrashReport,
            onDismiss = viewModel::dismissCrashReport,
        )
    }

    val darkPreview = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            // Forward goes left, back goes right, so the hierarchy is legible from
            // the movement alone.
            val forward = targetState != SettingsPage.Root
            val direction = if (forward) 1 else -1
            (slideInHorizontally { it / 3 * direction } + fadeIn())
                .togetherWith(slideOutHorizontally { -it / 3 * direction } + fadeOut())
        },
        label = "settingsPage",
        modifier = modifier
            .fillMaxSize()
            .predictiveBack(backState),
    ) { current ->
        when (current) {
            SettingsPage.Root -> SettingsRoot(
                versionName = viewModel.versionName,
                updateState = updateState,
                activeModelName = settings.textModelPath.substringAfterLast('/'),
                onOpen = { page = it },
                onCheckUpdates = viewModel::checkForUpdates,
                onDownloadUpdate = viewModel::downloadUpdate,
                onInstallUpdate = viewModel::installUpdate,
                onOpenReleases = { viewModel.openUrl(viewModel.releasesUrl) },
                onDismissUpdate = viewModel::dismissUpdate,
            )

            SettingsPage.Look -> SettingsPageScaffold(
                title = stringResource(R.string.settings_section_look),
                onBack = { page = SettingsPage.Root },
            ) {
                LookSettings(
                    settings = settings,
                    darkPreview = darkPreview,
                    viewModel = viewModel,
                )
            }

            SettingsPage.Ai -> SettingsPageScaffold(
                title = stringResource(R.string.settings_section_ai),
                onBack = { page = SettingsPage.Root },
            ) {
                AiSettings(settings = settings, viewModel = viewModel, onOpenModels = onOpenModels)
            }

            SettingsPage.Search -> SettingsPageScaffold(
                title = stringResource(R.string.settings_section_search),
                onBack = { page = SettingsPage.Root },
            ) {
                SearchSettings(settings = settings, viewModel = viewModel)
            }

            SettingsPage.Access -> SettingsPageScaffold(
                title = stringResource(R.string.settings_section_access),
                onBack = { page = SettingsPage.Root },
            ) {
                AccessSettings(access = access, viewModel = viewModel)
            }

            SettingsPage.Notifications -> SettingsPageScaffold(
                title = stringResource(R.string.settings_section_notifications),
                onBack = { page = SettingsPage.Root },
            ) {
                NotificationSettings(settings = settings, viewModel = viewModel)
            }

            SettingsPage.About -> SettingsPageScaffold(
                title = stringResource(R.string.settings_section_about),
                onBack = { page = SettingsPage.Root },
            ) {
                AboutSettings(
                    versionName = viewModel.versionName,
                    updateState = updateState,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun SettingsRoot(
    versionName: String,
    updateState: nl.markmaaktmedia.markmaaktai.update.UpdateState,
    activeModelName: String,
    onOpen: (SettingsPage) -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenReleases: () -> Unit,
    onDismissUpdate: () -> Unit,
) {
    val categories = listOf(
        Category(SettingsPage.Look, MarkIcons.Palette, R.string.settings_section_look, R.string.settings_look_summary),
        Category(SettingsPage.Ai, MarkIcons.Model, R.string.settings_section_ai, R.string.settings_ai_summary),
        Category(SettingsPage.Search, MarkIcons.Web, R.string.settings_section_search, R.string.settings_search_summary),
        Category(SettingsPage.Access, MarkIcons.Shield, R.string.settings_section_access, R.string.settings_access_summary),
        Category(SettingsPage.Notifications, MarkIcons.Notifications, R.string.settings_section_notifications, R.string.settings_notifications_summary),
        Category(SettingsPage.About, MarkIcons.Info, R.string.settings_section_about, R.string.settings_about_summary),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 130.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 12.dp),
            )
        }

        item {
            UpdateCard(
                state = updateState,
                onCheck = onCheckUpdates,
                onDownload = onDownloadUpdate,
                onInstall = onInstallUpdate,
                onOpenPage = onOpenReleases,
                onDismiss = onDismissUpdate,
            )
        }

        item {
            SettingsGroup(modifier = Modifier.padding(top = 8.dp)) {
                categories.forEachIndexed { index, category ->
                    GroupedRow(
                        index = index,
                        total = categories.size,
                        onClick = { onOpen(category.page) },
                    ) {
                        CategoryRow(
                            icon = category.icon,
                            title = stringResource(category.title),
                            subtitle = when (category.page) {
                                SettingsPage.Ai -> activeModelName.ifBlank {
                                    stringResource(R.string.models_not_installed)
                                }

                                SettingsPage.About -> stringResource(R.string.settings_version, versionName)
                                else -> stringResource(category.summary)
                            },
                        )
                    }
                }
            }
        }
    }
}

private data class Category(
    val page: SettingsPage,
    val icon: Painter,
    val title: Int,
    val summary: Int,
)

@Composable
private fun CategoryRow(icon: Painter, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            painter = MarkIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Title, back arrow and a scrolling body. Every settings page uses it. */
@Composable
private fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MarkIconButton(
                    icon = MarkIcons.Back,
                    contentDescription = stringResource(R.string.generic_back),
                    onClick = onBack,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        item { content() }
    }
}
