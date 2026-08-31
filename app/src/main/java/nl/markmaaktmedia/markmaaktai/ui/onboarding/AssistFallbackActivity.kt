package nl.markmaaktmedia.markmaaktai.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import nl.markmaaktmedia.markmaaktai.MainActivity
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import nl.markmaaktmedia.markmaaktai.data.prefs.UserSettings
import nl.markmaaktmedia.markmaaktai.ui.assist.AssistOverlay
import nl.markmaaktmedia.markmaaktai.ui.assist.AssistViewModel
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkTheme
import javax.inject.Inject

/**
 * The assist sheet as a transparent activity.
 *
 * Reached through ACTION_ASSIST, which is what fires on a phone where the assistant
 * gesture is wired up but MarkMaaktAI is not the registered VoiceInteractionService.
 * Same sheet, same look, minus the screen reading, which only the real session route
 * can provide.
 */
@AndroidEntryPoint
class AssistFallbackActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = UserSettings())
            val viewModel: AssistViewModel = hiltViewModel()
            val state by viewModel.state.collectAsState()

            MarkTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack,
                paletteStyle = settings.paletteStyle,
                colourSeed = settings.colourSeed,
                applySystemBarStyle = false,
            ) {
                AssistOverlay(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onAsk = viewModel::ask,
                    onDictate = viewModel::toggleDictation,
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finish()
                    },
                    onClose = {
                        // Let the sheet animate out, then drop the activity.
                        viewModel.beginClose()
                        window.decorView.postDelayed({ finish() }, 240)
                    },
                )
            }
        }
    }
}
