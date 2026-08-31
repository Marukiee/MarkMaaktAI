package nl.markmaaktmedia.markmaaktai.service.assist

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Registers the app as the phone's digital assistant.
 *
 * There is nothing to do here beyond existing: the system binds this once the user
 * picks MarkMaaktAI in Settings, and every actual interaction happens in the session
 * that MarkAssistSessionService hands back.
 */
class MarkAssistVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "Assistant service ready")
    }

    private companion object {
        const val TAG = "MarkAssist"
    }
}
