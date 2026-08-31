package nl.markmaaktmedia.markmaaktai.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.markmaaktmedia.markmaaktai.R
import nl.markmaaktmedia.markmaaktai.ui.chat.ChatScreen
import nl.markmaaktmedia.markmaaktai.ui.components.PillNavItem
import nl.markmaaktmedia.markmaaktai.ui.components.PillNavigationBar
import nl.markmaaktmedia.markmaaktai.ui.digest.DigestScreen
import nl.markmaaktmedia.markmaaktai.ui.digest.DigestViewModel
import nl.markmaaktmedia.markmaaktai.ui.models.ModelsScreen
import nl.markmaaktmedia.markmaaktai.ui.settings.SettingsScreen
import nl.markmaaktmedia.markmaaktai.ui.shots.ShotsScreen
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkIcons
import nl.markmaaktmedia.markmaaktai.ui.update.TopUpdateBanner
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkMotion

enum class MarkTab { Chat, Shots, Digest, Settings }

/**
 * The shell.
 *
 * Four tabs on one level with no back stack between them, which is what a set of
 * peers should be: switching tab is not something you undo with the back button.
 * Models is the one screen that does stack, on top of the tab that opened it, and it
 * has its own back arrow.
 */
@Composable
fun MarkNavHost(
    handoff: ChatHandoff,
    startTab: MarkTab = MarkTab.Chat,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(startTab) }
    var modelsOpen by remember { mutableStateOf(false) }
    val digestViewModel: DigestViewModel = hiltViewModel()
    val unread by digestViewModel.unreadCount.collectAsStateWithLifecycle()

    val items = listOf(
        PillNavItem(
            label = stringResource(R.string.nav_chat),
            icon = { MarkIcons.Chat },
            selectedIcon = { MarkIcons.ChatFilled },
        ),
        PillNavItem(
            label = stringResource(R.string.nav_shots),
            icon = { MarkIcons.Shots },
            selectedIcon = { MarkIcons.ShotsFilled },
        ),
        PillNavItem(
            label = stringResource(R.string.nav_digest),
            icon = { MarkIcons.Digest },
            selectedIcon = { MarkIcons.DigestFilled },
            badgeCount = unread,
        ),
        PillNavItem(
            label = stringResource(R.string.nav_settings),
            icon = { MarkIcons.Settings },
            selectedIcon = { MarkIcons.SettingsFilled },
        ),
    )

    /*
     * Back walks back up the app before it leaves it. Models closes first, then any
     * tab other than chat returns to chat, and only from chat does back actually exit.
     * Without this, back on the settings tab dropped straight out of the app, which
     * reads as a crash rather than as navigation.
     */
    androidx.activity.compose.BackHandler(enabled = modelsOpen) { modelsOpen = false }
    androidx.activity.compose.BackHandler(enabled = !modelsOpen && tab != MarkTab.Chat) {
        tab = MarkTab.Chat
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = modelsOpen to tab,
                transitionSpec = {
                    // Tabs cross fade, because sliding between peers implies an order
                    // they do not have. Models scales in, because it sits on top.
                    val enteringModels = targetState.first && !initialState.first
                    val leavingModels = !targetState.first && initialState.first
                    when {
                        enteringModels ->
                            (scaleIn(initialScale = 0.94f) + fadeIn())
                                .togetherWith(fadeOut(animationSpec = tween(120)))

                        leavingModels ->
                            fadeIn().togetherWith(scaleOut(targetScale = 0.94f) + fadeOut())

                        else ->
                            fadeIn(animationSpec = tween(MarkMotion.DurationMedium))
                                .togetherWith(fadeOut(animationSpec = tween(MarkMotion.DurationFast)))
                    }
                },
                label = "screen",
                modifier = Modifier.fillMaxSize(),
            ) { (showModels, currentTab) ->
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    // Above everything, on every tab. An update the user cannot see is
                    // an update that does not happen.
                    TopUpdateBanner()
                    Box(modifier = Modifier.weight(1f)) {
                    if (showModels) {
                        ModelsScreen(onBack = { modelsOpen = false })
                    } else {
                        when (currentTab) {
                            MarkTab.Chat -> ChatScreen(onOpenModels = { modelsOpen = true })

                            MarkTab.Shots -> ShotsScreen(
                                onAskAbout = { question ->
                                    handoff.offer(question)
                                    tab = MarkTab.Chat
                                },
                            )

                            MarkTab.Digest -> DigestScreen(
                                onAskAbout = { question ->
                                    handoff.offer(question)
                                    tab = MarkTab.Chat
                                },
                            )

                            MarkTab.Settings -> SettingsScreen(onOpenModels = { modelsOpen = true })
                        }
                        }
                    }
                }
            }

            if (!modelsOpen) {
                PillNavigationBar(
                    items = items,
                    selectedIndex = tab.ordinal,
                    onSelect = { index -> tab = MarkTab.entries[index] },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}
