package nl.markmaaktmedia.markmaaktai.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import nl.markmaaktmedia.markmaaktai.ai.AiOrchestrator
import nl.markmaaktmedia.markmaaktai.ai.stt.SpeechInputManager
import nl.markmaaktmedia.markmaaktai.ai.vision.ImageTextExtractor
import nl.markmaaktmedia.markmaaktai.data.prefs.SettingsRepository
import nl.markmaaktmedia.markmaaktai.data.repository.NotificationRepository
import nl.markmaaktmedia.markmaaktai.data.repository.ScreenshotRepository

/**
 * Hilt cannot inject a VoiceInteractionSession or a RecognitionService, since
 * neither is one of the Android components it knows how to build. They reach the
 * same singletons through this entry point instead.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AssistEntryPoint {
    fun orchestrator(): AiOrchestrator
    fun settings(): SettingsRepository
    fun imageTextExtractor(): ImageTextExtractor
    fun speechInput(): SpeechInputManager
    fun notifications(): NotificationRepository
    fun screenshots(): ScreenshotRepository
}
