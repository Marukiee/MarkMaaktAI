package nl.markmaaktmedia.markmaaktai.ui.shots

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.data.db.ScreenshotEntity
import nl.markmaaktmedia.markmaaktai.ui.components.EmptyState
import nl.markmaaktmedia.markmaaktai.ui.components.PillBadge
import nl.markmaaktmedia.markmaaktai.ui.components.MarkIconButton
import nl.markmaaktmedia.markmaaktai.ui.components.PillSpinner
import nl.markmaaktmedia.markmaaktai.ui.components.predictiveBack
import nl.markmaaktmedia.markmaaktai.ui.components.rememberPredictiveBack
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.CardSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion
import nl.markmaaktmedia.markmaaktai.ui.theme.ChipSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
import nl.markmaaktmedia.markmaaktai.ui.theme.SquircleShape

/**
 * Every screenshot on the phone, searchable by what is written in it.
 *
 * Tapping a thumbnail grows the card out of the grid rather than cutting to a new
 * screen, so the picture stays the thing being opened. The detail closes by shrinking
 * back the same way, which keeps the grid position readable the whole time.
 */
@Composable
fun ShotsScreen(
    onAskAbout: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShotsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shots by viewModel.shots.collectAsStateWithLifecycle()
    val context = LocalContext.current

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.clearSearch() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> viewModel.onPermissionResult(result.values.any { it }) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.shots_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .bouncyClickable { viewModel.scan() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isIndexing) {
                        PillSpinner(size = 18.dp)
                    } else {
                        Icon(
                            painter = MarkIcons.Refresh,
                            contentDescription = stringResource(R.string.shots_rescan),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }

            SearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // One line that either counts or spins, crossfading between the two, so
            // the row does not appear and disappear under the search box.
            AnimatedVisibility(
                visible = state.query.isNotBlank(),
                enter = expandVertically(animationSpec = MarkMotion.sizeSpring()) +
                    fadeIn(animationSpec = MarkMotion.fadeSpec()),
                exit = shrinkVertically(animationSpec = MarkMotion.sizeSpring()) +
                    fadeOut(animationSpec = MarkMotion.fadeSpec()),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AnimatedVisibility(visible = state.isSearching) {
                        PillSpinner(size = 16.dp)
                    }
                    Crossfade(
                        targetState = if (state.isSearching) null else shots.size,
                        animationSpec = MarkMotion.fadeSpec(),
                        label = "shotsCount",
                    ) { count ->
                        Text(
                            text = if (count == null) {
                                stringResource(R.string.shots_searching)
                            } else {
                                pluralStringResource(R.plurals.shots_results, count, count)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = state.isIndexing) {
                Text(
                    text = stringResource(
                        R.string.shots_indexing,
                        state.progress.done,
                        state.progress.total,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }

            CategoryRow(
                selected = state.category,
                onSelect = viewModel::setCategory,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            when {
                !state.hasPermission -> EmptyState(
                    title = stringResource(R.string.shots_permission_title),
                    body = stringResource(R.string.shots_permission_body),
                    icon = MarkIcons.Search,
                    action = {
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .bouncyClickable {
                                    permissionLauncher.launch(mediaPermissions())
                                }
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.onboarding_grant),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    },
                )

                shots.isEmpty() -> EmptyState(
                    title = stringResource(R.string.shots_empty_title),
                    body = stringResource(R.string.shots_empty_body),
                    icon = MarkIcons.Image,
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shots, key = { it.id }) { shot ->
                        ShotTile(
                            shot = shot,
                            onClick = { viewModel.open(shot.id) },
                            // A tile that survived the query grows into its new place.
                            // Only while searching: browsing scrolls through hundreds of
                            // these, and a tile that pops every time it comes back into
                            // view is the sort of animation that makes an app tiring.
                            growsIn = state.query.isNotBlank(),
                            // Filtering and searching change the grid constantly, and
                            // without this the tiles teleport into their new places.
                            modifier = Modifier.animateItem(
                                fadeInSpec = MarkMotion.fadeSpec(),
                                fadeOutSpec = MarkMotion.fadeSpec(),
                                placementSpec = MarkMotion.spatial(),
                            ),
                        )
                    }
                }
            }
        }

        val opened = shots.firstOrNull { it.id == state.openedId }
        val viewerBack = rememberPredictiveBack(enabled = opened != null) { viewModel.open(null) }
        AnimatedVisibility(
            visible = opened != null,
            enter = scaleIn(initialScale = 0.88f, animationSpec = MarkMotion.springy()) + fadeIn(),
            exit = scaleOut(targetScale = 0.92f) + fadeOut(),
        ) {
            opened?.let { shot ->
                ShotDetail(
                    modifier = Modifier.predictiveBack(viewerBack),
                    shot = shot,
                    onClose = { viewModel.open(null) },
                    onFavourite = { viewModel.toggleFavourite(shot) },
                    onOpenInGallery = { openInGallery(context, shot.contentUri) },
                    onAsk = {
                        viewModel.open(null)
                        onAskAbout("About this screenshot: ${shot.ocrText.take(1200)}")
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = MarkIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.shots_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(visible = query.isNotEmpty()) {
            Icon(
                painter = MarkIcons.Close,
                contentDescription = stringResource(R.string.generic_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .bouncyClickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = listOf(
        null to stringResource(R.string.digest_filter_all),
        "finance" to stringResource(R.string.category_finance),
        "travel" to stringResource(R.string.shots_category_travel),
        "delivery" to stringResource(R.string.category_delivery),
        "recipe" to stringResource(R.string.shots_category_recipe),
        "web" to stringResource(R.string.shots_category_web),
        "other" to stringResource(R.string.category_other),
    )

    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(categories) { (key, label) ->
            val active = selected == key
            Box(
                modifier = Modifier
                    .clip(ChipSquircle)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .bouncyClickable { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShotTile(
    shot: ScreenshotEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    growsIn: Boolean = false,
) {
    // Runs once for as long as this tile keeps its place in the grid. A tile that was
    // already there when the query changed keeps its scale and only slides.
    val scale = remember(shot.id) { Animatable(if (growsIn) EnterScale else 1f) }
    LaunchedEffect(shot.id) {
        if (growsIn) scale.animateTo(1f, animationSpec = MarkMotion.springy())
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(SquircleShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .bouncyClickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = Uri.parse(shot.contentUri),
                contentDescription = shot.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f),
            )
            if (shot.isFavourite) {
                Icon(
                    painter = MarkIcons.StarFilled,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp),
                )
            }
        }
        Text(
            text = shot.title.ifBlank { shot.fileName },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ShotDetail(
    shot: ScreenshotEntity,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onFavourite: () -> Unit,
    onOpenInGallery: () -> Unit,
    onAsk: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))
            // Tapping the backdrop closes. No ripple and no press scale here: this is
            // a dismiss area, not a button, and animating the whole screen under the
            // finger is what made the sheet feel like it was misfiring.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        var drag by remember { mutableFloatStateOf(0f) }

        Column(
            modifier = Modifier
                .padding(20.dp)
                .offset { androidx.compose.ui.unit.IntOffset(0, drag.toInt().coerceAtLeast(0)) }
                .clip(CardSquircle)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                // Swallows taps so pressing the picture, or missing a button by a few
                // pixels, does not close the thing you just opened.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                // Flicking a picture away is the gesture people try first, so it is
                // wired up rather than left to the button alone.
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> drag += delta },
                    onDragStopped = {
                        if (drag > DismissDistance) onClose() else drag = 0f
                    },
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                MarkIconButton(
                    icon = MarkIcons.Close,
                    contentDescription = stringResource(R.string.generic_close),
                    onClick = onClose,
                    size = 34,
                    iconSize = 17,
                )
            }
            AsyncImage(
                model = Uri.parse(shot.contentUri),
                contentDescription = shot.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SquircleShape(20.dp)),
            )
            VSpace(14)
            Text(
                text = shot.title.ifBlank { shot.fileName },
                style = MaterialTheme.typography.titleMedium,
            )
            if (shot.summary.isNotBlank()) {
                VSpace(6)
                Text(
                    text = shot.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VSpace(12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillBadge(
                    text = stringResource(R.string.shots_ask),
                    modifier = Modifier.bouncyClickable(onClick = onAsk),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
                PillBadge(
                    text = stringResource(R.string.shots_open_gallery),
                    modifier = Modifier.bouncyClickable(onClick = onOpenInGallery),
                )
                Icon(
                    painter = if (shot.isFavourite) MarkIcons.StarFilled else MarkIcons.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .bouncyClickable(onClick = onFavourite)
                        .padding(4.dp),
                )
            }
        }
    }
}

/** How far the card has to be pulled down before letting go closes it. */
private const val DismissDistance = 220f

private fun mediaPermissions(): Array<String> =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    } else {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    }

private fun openInGallery(context: android.content.Context, uri: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(uri), "image/*")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}

/** How small a tile starts when it joins a set of search results. */
private const val EnterScale = 0.86f
