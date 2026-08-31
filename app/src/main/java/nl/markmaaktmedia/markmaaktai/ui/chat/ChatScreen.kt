package nl.markmaaktmedia.markmaaktai.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ui.components.EmptyState
import nl.markmaaktmedia.markmaaktai.ui.components.PillMark
import nl.markmaaktmedia.markmaaktai.ui.components.SuggestionChip
import nl.markmaaktmedia.markmaaktai.ui.components.SwipeToDelete
import nl.markmaaktmedia.markmaaktai.ui.components.VSpace
import nl.markmaaktmedia.markmaaktai.ui.components.bouncyClickable
import nl.markmaaktmedia.markmaaktai.ui.theme.CardSquircle
import nl.markmaaktmedia.markmaaktai.ui.theme.PillShape
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

    // Follow the answer as it streams, but only while the user is already at the
    // bottom. Yanking the view down while someone is reading further up is rude.
    LaunchedEffect(messages.size, state.isGenerating) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex >= messages.size - 3) {
            listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ChatTopBar(
            title = conversations.firstOrNull { it.id == state.conversationId }?.title
                ?: stringResource(R.string.chat_title),
            onNewConversation = viewModel::newConversation,
            onOpenHistory = { historyOpen = true },
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 8.dp,
                        bottom = 16.dp,
                    ),
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

            androidx.compose.animation.AnimatedVisibility(
                visible = state.error != null,
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
                            text = stringResource(R.string.generic_close),
                            modifier = Modifier.bouncyClickable { viewModel.dismissError() },
                            color = MaterialTheme.colorScheme.inversePrimary,
                        )
                    },
                ) {
                    Text(state.error.orEmpty())
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
                .padding(bottom = 74.dp),
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

@Composable
private fun ChatTopBar(
    title: String,
    onNewConversation: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PillMark(size = 26.dp)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TopBarButton(
            icon = Icons.Rounded.History,
            description = stringResource(R.string.chat_new_conversation),
            onClick = onOpenHistory,
        )
        TopBarButton(
            icon = Icons.Rounded.AddComment,
            description = stringResource(R.string.chat_new_conversation),
            onClick = onNewConversation,
        )
    }
}

@Composable
private fun TopBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
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
                Row(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .bouncyClickable(onClick = onOpenModels)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chat_go_to_models),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        VSpace(28)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(text = suggestion, onClick = { onSuggestion(suggestion) })
            }
        }
    }
}

@Composable
private fun ConversationHistorySheet(
    conversations: List<nl.markmaaktmedia.markmaaktai.data.db.ConversationEntity>,
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
                text = stringResource(R.string.chat_new_conversation),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (conversations.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                return@Column
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    SwipeToDelete(item = conversation, onDelete = { onDelete(it.id) }) {
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
                                .padding(horizontal = 16.dp, vertical = 14.dp),
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

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
