package nl.markmaaktmedia.markmaaktai.service.assist

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** Builds a fresh session each time the assistant is summoned. */
class MarkAssistSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession = MarkAssistSession(this)
}
