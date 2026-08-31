package nl.markmaaktmedia.markmaaktai.service.assist

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.MainActivity
import nl.markmaaktmedia.markmaaktai.ai.InferenceEvent
import nl.markmaaktmedia.markmaaktai.ai.prompt.PromptContext
import nl.markmaaktmedia.markmaaktai.ai.stt.SpeechEvent
import nl.markmaaktmedia.markmaaktai.data.prefs.UserSettings
import nl.markmaaktmedia.markmaaktai.di.AssistEntryPoint
import nl.markmaaktmedia.markmaaktai.ui.assist.AssistOverlay
import nl.markmaaktmedia.markmaaktai.ui.assist.AssistUiState
import nl.markmaaktmedia.markmaaktai.ui.theme.MarkTheme

/**
 * The assistant sheet that opens over whatever app is in front.
 *
 * A VoiceInteractionSession is not an Activity, so nothing Compose relies on is
 * provided for free: this class is its own lifecycle, saved state and view model
 * owner, and hangs those on the content view before Compose ever looks for them.
 *
 * The screen is read twice over, because the two ways of doing it fail in opposite
 * situations. The assist structure gives exact text but only from apps that expose
 * it, and it is empty for anything drawing its own canvas. The screenshot always
 * arrives but has to be run through OCR. Whichever comes back with more is the one
 * the question gets answered from.
 */
class MarkAssistSession(context: Context) :
    VoiceInteractionSession(context),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AssistEntryPoint::class.java,
        )
    }

    private val state = MutableStateFlow(AssistUiState())

    private var screenText: String = ""
    private var structureText: String = ""
    private var answerJob: Job? = null
    private var dictationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        setUiEnabled(true)
    }

    override fun onCreateContentView(): View {
        val uiContext = localisedContext(context)
        val root = FrameLayout(uiContext)
        val composeView = ComposeView(uiContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val settings = remembered()
                MarkTheme(
                    themeMode = settings.themeMode,
                    dynamicColor = settings.dynamicColor,
                    pureBlack = settings.pureBlack,
                    paletteStyle = settings.paletteStyle,
                    colourSeed = settings.colourSeed,
                    // The sheet floats over another app, so it must not restyle that
                    // app's status bar on its way in.
                    applySystemBarStyle = false,
                ) {
                    val uiState by state.collectAsState()
                    AssistOverlay(
                        state = uiState,
                        onQueryChange = { value -> state.update { it.copy(query = value) } },
                        onAsk = ::ask,
                        onDictate = ::toggleDictation,
                        onOpenApp = ::openFullApp,
                        onClose = ::dismiss,
                    )
                }
            }
        }
        root.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        return root
    }

    /**
     * The context the sheet is built from, with the app's own language applied.
     *
     * A session is not an Activity, so it is handed the service context, and that one
     * carries the system language rather than the per app language chosen in Android's
     * settings. The sheet came up in English on a phone whose app was set to Dutch.
     * Reading the override back and rebuilding the configuration is what puts the
     * assistant in the same language as everything else in the app.
     */
    private fun localisedContext(base: Context): Context {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return base
        return runCatching {
            val manager = base.getSystemService(android.app.LocaleManager::class.java) ?: return base
            val locales = manager.applicationLocales
            if (locales.isEmpty) return base
            val config = android.content.res.Configuration(base.resources.configuration)
            config.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
            base.createConfigurationContext(config)
        }.getOrDefault(base)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        /*
         * The session window is not full screen by default, which left the glow
         * clipped to the sheet's own bounds and the sheet sitting under the gesture
         * bar. Taking the whole display and drawing behind the system bars is what
         * lets the light reach all four edges.
         */
        runCatching {
            window?.window?.let { w ->
                w.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)

                /*
                 * Resize, not pan. The default for this window pushes the whole thing
                 * up when the keyboard opens, which threw the sheet to the top of the
                 * screen instead of letting it rest on the keyboard.
                 */
                w.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
                )

                /*
                 * The navigation bar stays, the grey strip behind it does not.
                 *
                 * Android draws a translucent scrim behind the bar when a window is
                 * see-through, to keep the buttons legible. Over the glow that reads as
                 * a dirty band across the bottom of the screen, and the sheet floats
                 * clear of the bar anyway, so it is turned off.
                 */
                w.navigationBarColor = android.graphics.Color.TRANSPARENT
                w.statusBarColor = android.graphics.Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    w.isNavigationBarContrastEnforced = false
                    w.isStatusBarContrastEnforced = false
                }
            }
        }

        // The counter is what restarts the entry animation. Without it a second
        // summoning reuses the composition and the sheet is simply there.
        state.value = AssistUiState(showId = state.value.showId + 1)
        screenText = ""
        structureText = ""

        scope.launch { startListeningIfPossible() }
    }

    /**
     * Opens the microphone, but only when something can actually listen.
     *
     * Starting it regardless meant a phone with no speech model and no platform
     * recogniser greeted the user with "Dictation failed" in red before they had done
     * anything. Silence is the better answer: the text field still works.
     */
    private suspend fun startListeningIfPossible() {
        val speech = entryPoint.speechInput()
        if (!speech.hasMicrophonePermission()) return
        if (!speech.hasOfflineModel() && !speech.hasSystemRecogniser()) return
        toggleDictation()
    }

    /**
     * Back closes the same way the button does.
     *
     * The system's own handling takes the window away on the spot, so the sheet never
     * played its exit, and the session was left half torn down: the next summoning came
     * up with no arrival at all. Routing back through the same dismiss gives one exit
     * path with one set of state to reset.
     */
    override fun onBackPressed() {
        dismiss()
    }

    override fun onHide() {
        answerJob?.cancel()
        dictationJob?.cancel()
        // Cleared here as well as on show. A hide that did not come from dismiss, such
        // as the system taking the window for something else, would otherwise leave the
        // closing flag set and the next sheet would never become visible.
        state.update { it.copy(closing = false, isListening = false, isAnswering = false) }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onHide()
    }

    override fun onDestroy() {
        answerJob?.cancel()
        dictationJob?.cancel()
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        val bitmap = screenshot ?: return
        scope.launch {
            val text = runCatching {
                entryPoint.imageTextExtractor().extractStructured(bitmap)
            }.getOrDefault("")
            if (text.length > screenText.length) screenText = text
            state.update { it.copy(hasScreenContext = bestScreenText().isNotBlank()) }
        }
    }

    override fun onHandleAssist(assistState: AssistState) {
        super.onHandleAssist(assistState)
        val structure = assistState.assistStructure ?: return
        structureText = runCatching { readStructure(structure) }.getOrDefault("")
        state.update { it.copy(hasScreenContext = bestScreenText().isNotBlank()) }
    }

    /** Whichever of the two readings came back with more to go on. */
    private fun bestScreenText(): String =
        if (structureText.length >= screenText.length) structureText else screenText

    private fun ask() {
        val question = state.value.query.trim()
        if (question.isEmpty() || state.value.isAnswering) return

        // The microphone closes as soon as there is a question. Leaving it open meant
        // the assistant kept recording the room while it was answering.
        dictationJob?.cancel()
        dictationJob = null

        answerJob?.cancel()
        answerJob = scope.launch {
            state.update {
                it.copy(
                    isAnswering = true,
                    answer = "",
                    askedQuestion = question,
                    query = "",
                    error = null,
                    isListening = false,
                    level = 0f,
                )
            }
            val builder = StringBuilder()
            entryPoint.orchestrator().chat(
                history = emptyList(),
                question = question,
                context = PromptContext(screenText = bestScreenText()),
            ).collect { event ->
                when (event) {
                    is InferenceEvent.Token -> {
                        builder.append(event.text)
                        state.update { it.copy(answer = builder.toString()) }
                    }

                    is InferenceEvent.Completed ->
                        state.update { it.copy(answer = event.text.ifBlank { builder.toString() }) }

                    is InferenceEvent.Failed ->
                        state.update { it.copy(error = event.message) }

                    else -> Unit
                }
            }
            state.update { it.copy(isAnswering = false) }
        }
    }

    private fun toggleDictation() {
        if (state.value.isListening) {
            dictationJob?.cancel()
            state.update { it.copy(isListening = false) }
            return
        }
        dictationJob = scope.launch {
            state.update { it.copy(isListening = true) }
            entryPoint.speechInput().listen().collect { event ->
                when (event) {
                    is SpeechEvent.Level -> state.update { it.copy(level = event.rms) }
                    is SpeechEvent.Partial -> state.update { it.copy(query = event.text) }
                    is SpeechEvent.Final -> {
                        state.update { it.copy(query = event.text, isListening = false) }
                        ask()
                    }

                    is SpeechEvent.Failed ->
                        state.update { it.copy(error = event.reason, isListening = false) }

                    else -> Unit
                }
            }
            state.update { it.copy(isListening = false, level = 0f) }
        }
    }

    /**
     * Lets the sheet slide out before the window is taken away.
     *
     * Calling hide straight from the button removed the whole window on the same
     * frame, so the exit animation never had a chance to run and the sheet simply
     * vanished.
     */
    private fun dismiss() {
        if (state.value.closing) return
        state.update { it.copy(closing = true) }
        scope.launch {
            kotlinx.coroutines.delay(240)
            hide()
        }
    }

    /**
     * Hands the exchange over to the app and opens it on that thread.
     *
     * Dragging the sheet up means "carry on with this", so arriving at an empty chat
     * and having to ask again is the wrong answer. The question and the answer are
     * written to a real conversation first, and the app is told which one to open.
     * With nothing asked yet there is nothing to carry, so it simply opens.
     */
    private fun openFullApp() {
        val question = state.value.askedQuestion
        val answer = state.value.answer
        state.update { it.copy(closing = true) }

        scope.launch {
            val conversationId = if (question.isNotBlank()) {
                runCatching { saveExchange(question, answer) }.getOrNull()
            } else {
                null
            }

            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (conversationId != null) {
                intent.putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
            }
            runCatching { context.startActivity(intent) }
            hide()
        }
    }

    private suspend fun saveExchange(question: String, answer: String): Long {
        val chats = entryPoint.chats()
        val conversationId = chats.createConversation(question.take(60))
        val userId = chats.addUserMessage(
            conversationId = conversationId,
            text = question,
            imagePath = null,
            parentId = null,
        )
        if (answer.isNotBlank()) {
            val assistantId = chats.startAssistantMessage(conversationId, userId)
            chats.updateAssistantMessage(assistantId, answer)
        }
        return conversationId
    }

    /**
     * Walks the accessibility style tree the foreground app handed over and joins up
     * everything readable. Depth is capped because a deeply nested layout can produce
     * a tree that is expensive to walk and useless past the first few levels.
     */
    private fun readStructure(structure: AssistStructure): String {
        val builder = StringBuilder()
        for (index in 0 until structure.windowNodeCount) {
            val root = structure.getWindowNodeAt(index).rootViewNode ?: continue
            appendNode(root, builder, depth = 0)
        }
        return builder.toString().trim()
    }

    private fun appendNode(node: AssistStructure.ViewNode, builder: StringBuilder, depth: Int) {
        if (depth > MAX_TREE_DEPTH || builder.length > MAX_STRUCTURE_CHARS) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank()) builder.append(text).append('\n')

        val hint = node.hint?.trim()
        if (!hint.isNullOrBlank() && text.isNullOrBlank()) builder.append(hint).append('\n')

        for (index in 0 until node.childCount) {
            appendNode(node.getChildAt(index), builder, depth + 1)
        }
    }

    @Composable
    private fun remembered(): UserSettings =
        entryPoint.settings().settings.collectAsState(initial = UserSettings()).value

    private companion object {
        const val MAX_TREE_DEPTH = 24
        const val MAX_STRUCTURE_CHARS = 6000
    }
}
