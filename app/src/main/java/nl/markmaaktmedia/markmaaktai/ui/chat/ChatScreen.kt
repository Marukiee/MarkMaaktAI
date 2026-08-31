package nl.markmaaktmedia.markmaaktai.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.data.db.ConversationEntity
import nl.markmaaktmedia.markmaaktai.ui.components.EmptyState
import nl.markmaaktmedia.markmaaktai.ui.components.MarkErrorDialog
import nl.markmaaktmedia.markmaaktai.ui.components.MarkDropdownMenu
import nl.markmaaktmedia.markmaaktai.ui.components.MarkIconButton
import nl.markmaaktmedia.markmaaktai.ui.components.MarkMenuItem
import nl.markmaaktmedia.markmaaktai.ui.components.PrimaryPillButton
import nl.markmaaktmedia.markmaaktai.ui.components.SuggestionChip
import nl.markmaaktmedia.markmaaktai.ui.components.SwipeToDelete
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.CardSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion

@Composable
fun ChatScreen(
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val variants by viewModel.variants.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var historyOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottom = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(density)
    val navigationBottom =
        androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density)
    val restingGap = with(density) { NavigationBarClearance.roundToPx() } + navigationBottom
    val composerBottomInset = with(density) { maxOf(imeBottom, restingGap).toDp() }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::onAttachmentPicked) }

    /*
     * Sending always scrolls to the bottom, because you just wrote that and expect to
     * see it. Streaming only scrolls if you were already near the end, since dragging
     * the view down while someone reads further up is rude.
     */
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(messages.lastOrNull()?.content) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex >= messages.size - 3) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ChatTopBar(
            title = conversations.firstOrNull { it.id == state.conversationId }?.title
                ?: stringResource(R.string.chat_title),
            onOpenHistory = { historyOpen = true },
            onNewConversation = viewModel::newConversation,
        )

        // The transcript runs the full height and the composer floats over it, so a
        // photo or a long answer passes behind the bar instead of stopping dead at a
        // solid edge. The list is padded by however tall the bar turned out to be, so
        // nothing is ever stuck underneath it.
        var composerHeight by remember { mutableIntStateOf(0) }
        val composerInset = with(LocalDensity.current) { composerHeight.toDp() }

        Box(modifier = Modifier.weight(1f)) {
            // Keyed on the thread, and wrapping the empty state too, so starting a new
            // chat is a transition rather than the transcript blinking out.
            androidx.compose.animation.AnimatedContent(
                targetState = state.conversationId,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(MarkMotion.fadeSpec()) +
                        androidx.compose.animation.slideInVertically { it / 12 } togetherWith
                        androidx.compose.animation.fadeOut(MarkMotion.fadeSpec())
                },
                label = "conversation",
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                if (messages.isEmpty()) {
                    ChatEmptyState(
                        hasModel = state.hasTextModel,
                        onSuggestion = { viewModel.send(it) },
                        onOpenModels = onOpenModels,
                        bottomInset = composerInset,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = composerInset + 8.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                isStreaming = state.isGenerating && message.id == messages.last().id,
                                onOpenSource = { url -> openUrl(context, url) },
                                variant = variants[message.id],
                                onEdit = { text -> viewModel.editMessage(message, text) },
                                onShowVariant = viewModel::showVariant,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = MarkMotion.fadeSpec(),
                                    placementSpec = MarkMotion.spatial(),
                                ),
                            )
                        }
                    }
                }
            }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { composerHeight = it.height },
        ) {
            // A short fade rather than a hard line. Text that scrolls under the bar
            // thins out instead of being cut through the middle of a letter.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            )
                        )
                    )
            )

            WorkStatusLine(state = state)

            ChatComposer(
            state = state,
            onInputChange = viewModel::onInputChange,
            onSend = { viewModel.send() },
            onStop = viewModel::stop,
            onAttach = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveAttachment = viewModel::clearAttachment,
            onToggleWebSearch = viewModel::toggleWebSearch,
            onTogglePhoneContext = viewModel::togglePhoneContext,
            onDictate = viewModel::startDictation,
            /*
             * One continuous bottom inset instead of two that swap over.
             *
             * The bar has to clear the keyboard when it is up, and the floating
             * navigation bar when it is down. Switching between imePadding and a
             * fixed gap meant that at the moment the keyboard finished closing the
             * padding jumped from zero to the full gap, so the composer dropped
             * behind the navigation bar and sprang back up. Taking the larger of the
             * two every frame gives the same two resting places with nothing
             * discontinuous in between: the keyboard carries it down, and it comes to
             * rest on the navigation bar.
             */
                modifier = Modifier.padding(bottom = composerBottomInset),
            )
        }
        }
    }

    if (state.needsVisionModel) {
        nl.markmaaktmedia.markmaaktai.ui.components.MarkDialog(
            title = stringResource(R.string.vision_needed_title),
            body = stringResource(R.string.vision_needed_body),
            icon = MarkIcons.Image,
            onDismiss = viewModel::dismissVisionNotice,
            closeLabel = stringResource(R.string.generic_close),
            actions = {
                val best = viewModel.bestVisionModel()
                if (best != null) {
                    PrimaryPillButton(
                        label = stringResource(R.string.vision_download_best, best.displayName),
                        icon = MarkIcons.Download,
                        onClick = viewModel::downloadBestVisionModel,
                    )
                }
                nl.markmaaktmedia.markmaaktai.ui.components.SecondaryPillButton(
                    label = stringResource(R.string.chat_go_to_models),
                    onClick = {
                        viewModel.dismissVisionNotice()
                        onOpenModels()
                    },
                )
            },
        )
    }

    // Failures are a popup, not a red paragraph in the transcript. A stack trace typed
    // out as if the assistant had said it is both unreadable and untrue.
    state.error?.let { message ->
        MarkErrorDialog(
            title = stringResource(R.string.generic_error),
            message = message,
            onDismiss = viewModel::dismissError,
            confirmLabel = stringResource(R.string.generic_close),
            copyLabel = stringResource(R.string.chat_copy),
            retryLabel = stringResource(R.string.generic_retry).takeIf { state.lastQuestion.isNotBlank() },
            onRetry = { viewModel.retryLast() }.takeIf { state.lastQuestion.isNotBlank() },
        )
    }

    ConversationPanel(
        open = historyOpen,
        conversations = conversations,
        currentId = state.conversationId,
        onOpen = {
            viewModel.openConversation(it)
            historyOpen = false
        },
        onNew = {
            viewModel.newConversation()
            historyOpen = false
        },
        onTogglePin = viewModel::togglePin,
        onDelete = viewModel::deleteConversation,
        onDismiss = { historyOpen = false },
    )
}

/**
 * History on the left, new chat on the right.
 *
 * The overflow dot column is where a phone user reaches for "the things I made
 * before", and putting it opposite the button that throws the current thread away
 * keeps those two apart. They were next to each other before, which is asking for a
 * mis-tap that loses what you were reading.
 */
@Composable
private fun ChatTopBar(
    title: String,
    onOpenHistory: () -> Unit,
    onNewConversation: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MarkIconButton(
            icon = MarkIcons.More,
            contentDescription = stringResource(R.string.chat_history),
            onClick = onOpenHistory,
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        MarkIconButton(
            icon = MarkIcons.NewChat,
            contentDescription = stringResource(R.string.chat_new_conversation),
            onClick = onNewConversation,
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun ChatEmptyState(
    hasModel: Boolean,
    onSuggestion: (String) -> Unit,
    onOpenModels: () -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    if (!hasModel) {
        EmptyState(
            title = stringResource(R.string.chat_no_model_title),
            body = stringResource(R.string.chat_no_model_body),
            icon = MarkIcons.Model,
            action = {
                PrimaryPillButton(
                    label = stringResource(R.string.chat_go_to_models),
                    icon = MarkIcons.Model,
                    onClick = onOpenModels,
                )
            },
        )
        return
    }

    // Each suggestion gets the icon for what it is about, rather than three copies of
    // the same sparkle, which said only "this is AI" three times over.
    val suggestions = listOf(
        stringResource(R.string.suggestion_summarise_day) to MarkIcons.Today,
        stringResource(R.string.suggestion_urgent) to MarkIcons.Notifications,
        stringResource(R.string.suggestion_draft_reply) to MarkIcons.Edit,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Clears the floating composer, so the last suggestion is reachable rather
            // than sitting under the bar.
            .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = bottomInset + 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                painter = MarkIcons.SparkleFilled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(38.dp)
                    .offset(x = (-1).dp),
            )
        }
        VSpace(20)
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        VSpace(8)
        Text(
            text = stringResource(R.string.chat_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        VSpace(28)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { (label, icon) ->
                SuggestionChip(
                    text = label,
                    icon = icon,
                    onClick = { onSuggestion(label) },
                )
            }
        }
    }
}

/**
 * The conversation list, sliding in from the left edge.
 *
 * The scrim fades in with it and swallows taps, so the panel closes the way every
 * drawer does. It sits above the whole screen rather than inside the chat column,
 * because a panel that stops short of the status bar reads as a card that failed to
 * position itself.
 */
@Composable
private fun ConversationPanel(
    open: Boolean,
    conversations: List<ConversationEntity>,
    currentId: Long,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onTogglePin: (ConversationEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var windowPresent by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            windowPresent = true
            // One frame later, so AnimatedVisibility is composed hidden first and has
            // a state to animate out of. Setting both together made it appear.
            androidx.compose.runtime.withFrameNanos { }
            contentVisible = true
        } else {
            contentVisible = false
            // Long enough for the slide out to finish before the window goes.
            kotlinx.coroutines.delay(260)
            windowPresent = false
        }
    }
    if (!windowPresent) return

    val scrimAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 0.45f else 0f,
        animationSpec = MarkMotion.fadeSpec(),
        label = "panelScrim",
    )

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent
            as? androidx.compose.ui.window.DialogWindowProvider)?.window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        androidx.compose.runtime.SideEffect {
            dialogWindow?.let { window ->
                window.setDimAmount(0f)
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                androidx.core.view.WindowCompat
                    .getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars = lightBars
            }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
        )

        AnimatedVisibility(
            visible = contentVisible,
            enter = slideInHorizontally(animationSpec = MarkMotion.spatial()) { -it } + fadeIn(),
            exit = slideOutHorizontally(animationSpec = MarkMotion.spatial()) { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            /*
             * The panel came in from the left, so pushing it back that way is what a
             * hand expects to close it. It follows the finger and springs back if the
             * push was not enough. Rows in the list consume their own horizontal drags
             * for swipe to delete, so this only picks up drags on the panel itself.
             */
            val panelScope = rememberCoroutineScope()
            val panelOffset = remember { Animatable(0f) }
            var panelWidth by remember { mutableIntStateOf(1) }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.84f)
                    .onSizeChanged { panelWidth = it.width.coerceAtLeast(1) }
                    .offset { IntOffset(panelOffset.value.toInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                panelScope.launch {
                                    panelOffset.snapTo(
                                        (panelOffset.value + delta).coerceAtMost(0f)
                                    )
                                }
                            },
                            onDragEnd = {
                                if (-panelOffset.value > panelWidth * 0.25f) {
                                    // Left where the finger put it. Snapping back to
                                    // zero here made the panel jump home for a moment
                                    // before the exit had a chance to play, so it
                                    // looked like the swipe had been refused. The
                                    // offset goes with the composition on the way out.
                                    onDismiss()
                                } else {
                                    panelScope.launch {
                                        panelOffset.animateTo(0f, MarkMotion.spatial())
                                    }
                                }
                            },
                            onDragCancel = {
                                panelScope.launch {
                                    panelOffset.animateTo(0f, MarkMotion.spatial())
                                }
                            },
                        )
                    }
                    .clip(PanelShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    // Stops a tap on the panel from reaching the scrim behind it,
                    // which was closing the thing the user had just opened.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .systemBarsPadding()
                    .padding(horizontal = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chat_history),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                }

                if (conversations.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    return@Column
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        SwipeToDelete(
                            item = conversation,
                            key = conversation.id,
                            onDelete = { onDelete(it.id) },
                            shape = CardSquircle,
                        ) {
                            ConversationRow(
                                conversation = conversation,
                                selected = conversation.id == currentId,
                                onOpen = { onOpen(conversation.id) },
                                onTogglePin = { onTogglePin(conversation) },
                                onDelete = { onDelete(conversation.id) },
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * One thread in the list.
 *
 * A pinned thread shows its pin, so the reason it is sitting above a more recent one
 * is visible rather than mysterious. The overflow menu carries pin and delete: swipe
 * to delete still works, but a gesture nobody was told about cannot be the only way
 * to reach either of them.
 */
@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    selected: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun runAfterMenuCloses(action: () -> Unit) {
        menuOpen = false
        scope.launch {
            kotlinx.coroutines.delay(160)
            action()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardSquircle)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .bouncyClickable(onClick = onOpen)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (conversation.pinned) {
            androidx.compose.material3.Icon(
                painter = MarkIcons.Pin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(15.dp),
            )
        }
        Text(
            text = conversation.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
        )

        Box {
            MarkIconButton(
                icon = MarkIcons.More,
                contentDescription = stringResource(R.string.chat_conversation_actions),
                onClick = { menuOpen = true },
                size = 38,
                iconSize = 18,
            )
            MarkDropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                MarkMenuItem(
                    label = stringResource(
                        if (conversation.pinned) R.string.chat_unpin else R.string.chat_pin
                    ),
                    icon = if (conversation.pinned) MarkIcons.PinOff else MarkIcons.Pin,
                    onClick = { runAfterMenuCloses(onTogglePin) },
                )
                MarkMenuItem(
                    label = stringResource(R.string.generic_delete),
                    icon = MarkIcons.Delete,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { runAfterMenuCloses(onDelete) },
                )
            }
        }
    }
}

/** Square against the screen edge, rounded on the side that faces the content. */
private val PanelShape = androidx.compose.foundation.shape.RoundedCornerShape(
    topEnd = 28.dp,
    bottomEnd = 28.dp,
)

/** How much room the floating navigation bar needs above the system inset. */
private val NavigationBarClearance = 76.dp

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
