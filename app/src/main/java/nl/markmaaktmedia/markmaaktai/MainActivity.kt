package nl.markmaaktmedia.markmaaktai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import nl.markmaaktmedia.markmaaktai.data.prefs.UserSettings
import nl.markmaaktmedia.markmaaktai.ui.navigation.ChatHandoff
import nl.markmaaktmedia.markmaaktai.ui.navigation.MarkNavHost
import nl.markmaaktmedia.markmaaktai.ui.navigation.MarkTab
import nl.markmaaktmedia.markmaaktai.ui.onboarding.OnboardingScreen
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkTheme
import nl.markmaaktmedia.markmaaktai.update.UpdateRepository
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var handoff: ChatHandoff

    @Inject lateinit var updateRepository: UpdateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val openSummary = intent?.hasExtra(EXTRA_SUMMARY_ID) == true
        checkForUpdatesOncePerDay()

        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = UserSettings())
            var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
            val scope = rememberCoroutineScope()

            // Null until the stored value has actually arrived, so a fresh launch does
            // not flash the onboarding at someone who finished it months ago.
            androidx.compose.runtime.LaunchedEffect(settings.onboardingDone) {
                onboardingDone = settings.onboardingDone
            }

            MarkTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack,
            ) {
                AnimatedContent(
                    targetState = onboardingDone,
                    transitionSpec = {
                        fadeIn().togetherWith(scaleOut(targetScale = 1.04f) + fadeOut())
                    },
                    label = "root",
                ) { done ->
                    when (done) {
                        null -> Unit
                        false -> OnboardingScreen(
                            onFinished = {
                                scope.launch { settingsRepository.setOnboardingDone(true) }
                            },
                        )

                        true -> MarkNavHost(
                            handoff = handoff,
                            startTab = if (openSummary) MarkTab.Digest else MarkTab.Chat,
                        )
                    }
                }
            }
        }
    }

    /**
     * One update check a day, at launch. No background job and no service: an app that
     * checks for its own updates on a schedule is spending the user's battery on
     * something that can wait until they next open it.
     */
    private fun checkForUpdatesOncePerDay() {
        lifecycleScope.launch {
            val settings = settingsRepository.current()
            val elapsed = System.currentTimeMillis() - settings.lastUpdateCheck
            if (elapsed < CHECK_INTERVAL_MS) return@launch
            settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
            updateRepository.check()
        }
    }

    companion object {
        const val EXTRA_SUMMARY_ID = "summary_id"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
