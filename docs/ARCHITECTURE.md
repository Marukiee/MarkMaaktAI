# Architecture

```
  UI (Jetpack Compose, Material 3)
  chat  screenshots  overview  settings  assistant overlay
        |
  ViewModels (MVVM, Hilt)
        |
  +-----+---------------------------+
  |                                 |
  Repositories                      AiOrchestrator
  chat, notifications, screenshots, picks a model per task
  models, updates                         |
  |                                 InferenceEngine
  Room + FTS4     DataStore         +-------+--------+
  conversations   settings          LiteRT          llama.cpp
  notifications                     (active now)    (stub)
  screenshots
        |
  Background (WorkManager)
  summarise notifications, index screenshots
```

The whole AI part sits behind one interface. LiteRT runs today. The llama.cpp layer is a stub behind the same interface, so switching to GGUF later is one binding in `AppModule` and nothing else.

## Stack

| Layer | Used |
| --- | --- |
| UI | Jetpack Compose, Material 3, its own squircle and spring layer |
| Architecture | MVVM, Hilt, Coroutines and Flow |
| Storage | Room with FTS4, DataStore |
| AI | LiteRT (MediaPipe GenAI), ML Kit bundled OCR, Vosk |
| Background | WorkManager, NotificationListenerService |
| Assistant | VoiceInteractionService with screen context |
| Release | GitHub Actions, in app update check |
