package nl.markmaaktmedia.markmaaktai.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.data.db.ConversationEntity
import nl.markmaaktmedia.markmaaktai.ui.components.EmptyState
import nl.markmaaktmedia.markmaaktai.ui.components.MarkErrorDialog
import nl.markmaaktmedia.markmaaktai.ui.components.MarkIconButton
import nl.markmaaktmedia.markmaaktai.ui.components.PillMark
import nl.markmaaktmedia.markmaaktai.ui.components.PrimaryPillButton
import nl.markmaaktmedia.markmaaktai.ui.components.SuggestionChip
import nl.markmaaktmedia.markmaaktai.ui.components.SwipeToDelete
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.CardSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.theme.SheetSquircle

@Composable
fun ChatScreen(
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var historyOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(viewModel::onAttachmentPicked) }

    // Follow the answer as it streams, but only while the user is already near the
    // bottom. Yanking the view down while someone reads further up is rude.
    LaunchedEffect(messages.size, state.isGenerating) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex >= messages.size - 3) {
            listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ChatTopBar(
            title = conversations.firstOrNull { it.id == state.conversationId }?.title
                ?: stringResource(R.string.chat_title),
            onOpenHistory = { historyOpen = true },
            onNewConversation = viewModel::newConversation,
        )

        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                ChatEmptyState(
                    hasModel = state.hasTextModel,
                    onSuggestion = { viewModel.send(it) },
                    onOpenModels = onOpenModels,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isStreaming = state.isGenerating && message.id == messages.last().id,
                            onOpenSource = { url -> openUrl(context, url) },
                        )
                    }
                }
            }
        }

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
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                // Clears the floating navigation bar, which is drawn over this screen.
                .padding(bottom = 76.dp),
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
            retryLabel = stringResource(R.string.generic_retry).takeIf { state.lastQuestion.isNotBlank() },
            onRetry = { viewModel.retryLast() }.takeIf { state.lastQuestion.isNotBlank() },
        )
    }

    if (historyOpen) {
        ConversationHistorySheet(
            conversations = conversations,
            currentId = state.conversationId,
            onOpen = {
                viewModel.openConversation(it)
                historyOpen = false
            },
            onDelete = viewModel::deleteConversation,
            onDismiss = { historyOpen = false },
        )
    }
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
) {
    if (!hasModel) {
        EmptyState(
            title = stringResource(R.string.chat_no_model_title),
            body = stringResource(R.string.chat_no_model_body),
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

    val suggestions = listOf(
        stringResource(R.string.suggestion_summarise_day),
        stringResource(R.string.suggestion_urgent),
        stringResource(R.string.suggestion_draft_reply),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PillMark(size = 56.dp)
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
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    text = suggestion,
                    icon = MarkIcons.Sparkle,
                    onClick = { onSuggestion(suggestion) },
                )
            }
        }
    }
}

@Composable
private fun ConversationHistorySheet(
    conversations: List<ConversationEntity>,
    currentId: Long,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetSquircle,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.chat_history),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
            )

            if (conversations.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 32.dp),
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
                        onDelete = { onDelete(it.id) },
                        shape = CardSquircle,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CardSquircle)
                                .background(
                                    if (conversation.id == currentId) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                )
                                .bouncyClickable { onOpen(conversation.id) }
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = conversation.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
