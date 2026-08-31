package nl.markmaaktmedia.markmaaktai.ui.models

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ai.ModelRole
import nl.markmaaktmedia.markmaaktai.ai.ModelSpec
import nl.markmaaktmedia.markmaaktai.ui.components.PillBadge
import nl.markmaaktmedia.markmaaktai.ui.components.PillSpinner
import nl.markmaaktmedia.markmaaktai.ui.components.SectionHeader
import nl.markmaaktmedia.markmaaktai.ui.components.SettingsGroup
import nl.markmaaktmedia.markmaaktai.ui.components.SoftDivider
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.CardSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
import java.util.Locale

@Composable
fun ModelsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var pickerRole by remember { mutableStateOf(ModelRole.TEXT) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { viewModel.importFromUri(it, pickerRole) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(PillShape)
                        .bouncyClickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = MarkIcons.Back,
                        contentDescription = stringResource(R.string.generic_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.models_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.models_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }

        item {
            Text(
                text = stringResource(R.string.models_free_space, formatSize(state.freeBytes)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            )
        }

        modelSection(
            titleRes = R.string.models_text_role,
            role = ModelRole.TEXT,
            specs = viewModel.textCatalog,
            activePath = state.activeTextPath,
            downloads = downloads,
            viewModel = viewModel,
            onPickFile = {
                pickerRole = ModelRole.TEXT
                filePicker.launch(arrayOf("*/*"))
            },
        )

        modelSection(
            titleRes = R.string.models_vision_role,
            role = ModelRole.VISION,
            specs = viewModel.visionCatalog,
            activePath = state.activeVisionPath,
            downloads = downloads,
            viewModel = viewModel,
            onPickFile = {
                pickerRole = ModelRole.VISION
                filePicker.launch(arrayOf("*/*"))
            },
            footer = {
                Text(
                    text = stringResource(R.string.models_ocr_fallback_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            },
        )

        modelSection(
            titleRes = R.string.models_speech_role,
            role = ModelRole.SPEECH,
            specs = viewModel.speechCatalog,
            activePath = state.activeSpeechPath,
            downloads = downloads,
            viewModel = viewModel,
            onPickFile = {
                pickerRole = ModelRole.SPEECH
                filePicker.launch(arrayOf("*/*"))
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.modelSection(
    titleRes: Int,
    role: ModelRole,
    specs: List<ModelSpec>,
    activePath: String,
    downloads: Map<String, nl.markmaaktmedia.markmaaktai.data.repository.DownloadProgress>,
    viewModel: ModelsViewModel,
    onPickFile: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    item { SectionHeader(stringResource(titleRes)) }
    item {
        SettingsGroup {
            specs.forEachIndexed { index, spec ->
                if (index > 0) SoftDivider()
                ModelRow(
                    spec = spec,
                    installed = viewModel.isInstalled(spec),
                    active = activePath.endsWith(spec.fileName),
                    progress = downloads[spec.id],
                    onDownload = { viewModel.download(spec) },
                    onCancel = { viewModel.cancelDownload(spec) },
                )
            }
            SoftDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable(onClick = onPickFile)
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = MarkIcons.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.models_import_file),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            footer?.invoke()
        }
    }
}

@Composable
private fun ModelRow(
    spec: ModelSpec,
    installed: Boolean,
    active: Boolean,
    progress: nl.markmaaktmedia.markmaaktai.data.repository.DownloadProgress?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = spec.displayName, style = MaterialTheme.typography.bodyLarge)
                    if (spec.isRecommended) {
                        PillBadge(text = stringResource(R.string.models_recommended))
                    }
                    if (spec.isExperimental) {
                        PillBadge(
                            text = stringResource(R.string.models_experimental),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Text(
                    text = spec.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = formatSize(spec.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            when {
                progress != null -> Box(
                    modifier = Modifier
                        .size(40.dp)
                        .bouncyClickable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    PillSpinner(size = 20.dp)
                }

                active -> Icon(
                    painter = MarkIcons.CheckCircle,
                    contentDescription = stringResource(R.string.models_active),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )

                installed -> PillBadge(text = stringResource(R.string.models_installed))

                else -> Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .bouncyClickable(onClick = onDownload),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = MarkIcons.Download,
                        contentDescription = stringResource(R.string.models_download),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        AnimatedVisibility(visible = progress != null, enter = fadeIn(), exit = fadeOut()) {
            Column {
                VSpace(10)
                LinearProgressIndicator(
                    progress = { progress?.fraction ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(PillShape),
                )
                if (progress?.error != null) {
                    VSpace(6)
                    Text(
                        text = progress.error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Human readable file size. Gigabytes with one decimal, megabytes whole. */
internal fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 MB"
    bytes >= 1_000_000_000L -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_000_000_000.0)
    else -> "${bytes / 1_000_000} MB"
}
