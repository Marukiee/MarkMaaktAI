package nl.markmaaktmedia.markmaaktai.ui.digest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.data.db.SummaryEntity
import nl.markmaaktmedia.markmaaktai.ui.components.EmptyState
import nl.markmaaktmedia.markmaaktai.ui.components.PillBadge
import nl.markmaaktmedia.markmaaktai.ui.components.SoftDivider
import nl.markmaaktmedia.markmaaktai.ui.components.SwipeToDelete
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.CardSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.ChipSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.LocalMarkExtraColors
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DigestScreen(
    onAskAbout: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DigestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column {
            Text(
                text = stringResource(R.string.digest_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
            )

            FilterRow(
                selected = state.filter,
                onSelect = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (summaries.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.digest_empty_title),
                    body = stringResource(R.string.digest_empty_body),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(summaries, key = { it.id }) { summary ->
                        SwipeToDelete(item = summary, onDelete = viewModel::delete) {
                            SummaryCard(
                                summary = summary,
                                expanded = state.expandedId == summary.id,
                                sourceLines = if (state.expandedId == summary.id) {
                                    state.sources.map {
                                        "${it.title.ifBlank { it.appLabel }}: ${it.body}"
                                    }
                                } else {
                                    emptyList()
                                },
                                onToggle = { viewModel.toggleExpanded(summary.id) },
                                onAsk = {
                                    onAskAbout("About ${summary.appLabel}: ${summary.summary}")
                                },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.lastDeleted != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) {
            Snackbar(
                shape = CardSquircle,
                action = {
                    Text(
                        text = stringResource(R.string.digest_undo),
                        color = MaterialTheme.colorScheme.inversePrimary,
                        modifier = Modifier.bouncyClickable { viewModel.undoDelete() },
                    )
                },
            ) {
                Text(stringResource(R.string.digest_deleted))
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: DigestFilter,
    onSelect: (DigestFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(stringResource(R.string.digest_filter_all), selected == DigestFilter.All) {
            onSelect(DigestFilter.All)
        }
        FilterChip(stringResource(R.string.digest_filter_urgent), selected == DigestFilter.Urgent) {
            onSelect(DigestFilter.Urgent)
        }
        FilterChip(stringResource(R.string.digest_filter_unread), selected == DigestFilter.Unread) {
            onSelect(DigestFilter.Unread)
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = MarkMotion.colourSpec(),
        label = "filterContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MarkMotion.colourSpec(),
        label = "filterContent",
    )

    Box(
        modifier = Modifier
            .clip(ChipSquircle)
            .background(container)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/**
 * One summary.
 *
 * Closed it is the one sentence the model wrote. Opening it slides the original
 * messages in underneath, so the summary can always be checked against what it was
 * made from. A summary you cannot verify is a summary you cannot trust.
 */
@Composable
private fun SummaryCard(
    summary: SummaryEntity,
    expanded: Boolean,
    sourceLines: List<String>,
    onToggle: () -> Unit,
    onAsk: () -> Unit,
) {
    val extras = LocalMarkExtraColors.current
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MarkMotion.springy(),
        label = "chevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardSquircle)
            .background(
                if (summary.isUrgent) extras.urgentContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .bouncyClickable(onClick = onToggle)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = summary.appLabel,
                style = MaterialTheme.typography.titleSmall,
                color = if (summary.isUrgent) extras.onUrgentContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (summary.isUrgent) {
                PillBadge(
                    text = stringResource(R.string.digest_urgent),
                    icon = MarkIcons.Urgent,
                    containerColor = extras.urgent,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                )
            }
            Icon(
                painter = MarkIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = chevron },
            )
        }

        VSpace(6)

        Text(
            text = summary.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = if (summary.isUrgent) extras.onUrgentContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (summary.actionItems.isNotEmpty()) {
            VSpace(12)
            Text(
                text = stringResource(R.string.digest_action_items),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            VSpace(4)
            summary.actionItems.forEach { item ->
                Text(
                    text = "- $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        VSpace(10)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillBadge(
                text = stringResource(R.string.digest_source_messages, summary.messageCount),
            )
            Text(
                text = timeFormat.format(Date(summary.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(ChipSquircle)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .bouncyClickable(onClick = onAsk)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    painter = MarkIcons.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = stringResource(R.string.digest_ask_about),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && sourceLines.isNotEmpty(),
            enter = expandVertically(animationSpec = MarkMotion.sizeSpring()) + fadeIn(),
            exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) + fadeOut(),
        ) {
            Column {
                VSpace(12)
                SoftDivider()
                VSpace(12)
                sourceLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
