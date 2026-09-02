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
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.markmaaktmedia.markmaaktai.MainActivity
import nl.markmaaktmedia.markmaaktai.ai.InferenceEvent
import nl.markmaaktmedia.markmaaktai.ai.prompt.PromptContext
import nl.markmaaktmedia.markmaaktai.ai.prompt.PromptTurn
import nl.markmaaktmedia.markmaaktai.data.repository.ChatRepository
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

    /**
     * The thread this summoning is writing to, created on the first question.
     *
     * Every question and answer goes in as it happens, so a follow up has the earlier
     * turns to work from and opening the app lands on the whole exchange rather than
     * the last line of it.
     */
    private var conversationId: Long? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        setUiEnabled(true)
        applyWindowStyle()
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
                        onAskScreen = ::askAboutScreen,
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

    /**
     * Makes the session window behave like an edge to edge surface.
     *
     * Not full screen by default, which left the glow clipped to the sheet's own
     * bounds. Applied on create as well as on show, because the window exists before
     * the first show and a style that is only set later is a style that is sometimes
     * not set at all.
     */
    private fun applyWindowStyle() {
        runCatching {
            val w = window?.window ?: return
            w.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)

            /*
             * The window does nothing when the keyboard opens, and the sheet moves
             * itself out of the way instead.
             *
             * Anything else means two things react to the same keyboard: the system
             * pans or resizes the window, the layout adds the keyboard inset on top of
             * that, and the sheet ends up at the top of the screen.
             */
            w.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            )

            /*
             * The navigation bar stays, the grey strip behind it does not.
             *
             * Android draws a translucent scrim behind the bar when a window is
             * see-through, to keep the buttons legible. Over the glow that reads as a
             * dirty band across the bottom of the screen, and the sheet floats clear of
             * the bar anyway, so it is turned off.
             */
            w.navigationBarColor = android.graphics.Color.TRANSPARENT
            w.statusBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                w.isNavigationBarContrastEnforced = false
                w.isStatusBarContrastEnforced = false
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        applyWindowStyle()

        // The counter is what restarts the entry animation. Without it a second
        // summoning reuses the composition and the sheet is simply there.
        state.value = AssistUiState(showId = state.value.showId + 1)
        screenText = ""
        structureText = ""
        conversationId = null

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

            val chats = entryPoint.chats()
            // Written down before the model starts, so the thread is real even if the
            // answer fails or the sheet is closed halfway through.
            val threadId = conversationId ?: runCatching {
                chats.createConversation(question.take(60))
            }.getOrNull()
            conversationId = threadId

            val history = threadId
                ?.let { runCatching { chats.history(it) }.getOrDefault(emptyList()) }
                .orEmpty()
                .filter { it.content.isNotBlank() }
                .map { entity ->
                    PromptTurn(
                        role = if (entity.role == ChatRepository.ROLE_USER) PromptTurn.Role.USER
                        else PromptTurn.Role.ASSISTANT,
                        text = entity.content,
                    )
                }

            var assistantMessageId: Long? = null
            if (threadId != null) {
                runCatching {
                    val parent = chats.currentLeafId(threadId)
                    val userId = chats.addUserMessage(threadId, question, null, parent)
                    assistantMessageId = chats.startAssistantMessage(threadId, userId)
                }
            }

            val builder = StringBuilder()
            try {
                entryPoint.orchestrator().chat(
                    history = history,
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                // This runs in a window the system owns. An exception escaping here
                // takes the whole assistant down with no dialog and no way back in.
                Log.e(TAG, "Answering failed", error)
                state.update { it.copy(error = error.message ?: "Something went wrong") }
            } finally {
                assistantMessageId?.let { id ->
                    runCatching { chats.updateAssistantMessage(id, state.value.answer) }
                }
                state.update { it.copy(isAnswering = false) }
            }
        }
    }

    /** The badge above the sheet, tapped. Asks the obvious question for it. */
    private fun askAboutScreen() {
        val question = localisedContext(context)
            .getString(nl.markmaaktmedia.markmaaktai.R.string.assist_ask_screen)
        state.update { it.copy(query = question) }
        ask()
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
     * Hands the thread over to the app and opens it there.
     *
     * Everything asked in the sheet is already written to a conversation as it
     * happens, so this is only a matter of saying which one. With nothing asked yet
     * there is nothing to carry, and the app simply opens.
     */
    private fun openFullApp() {
        val threadId = conversationId
        state.update { it.copy(closing = true) }

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (threadId != null) {
            intent.putExtra(MainActivity.EXTRA_CONVERSATION_ID, threadId)
        }
        runCatching { context.startActivity(intent) }
        scope.launch {
            kotlinx.coroutines.delay(120)
            hide()
        }
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
        const val TAG = "MarkAssistSession"
        const val MAX_TREE_DEPTH = 24
        const val MAX_STRUCTURE_CHARS = 6000
    }

}
