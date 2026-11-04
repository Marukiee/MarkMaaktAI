# MarkMaaktAI

[![Release](https://img.shields.io/github/v/release/Marukiee/MarkMaaktAI?label=release)](https://github.com/Marukiee/MarkMaaktAI/releases/latest)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](#install)

English | [Nederlands](README.nl.md)

**A local AI hub for Android.** Chat, read photos, make your screenshots searchable and calm down your notifications, without anything going to a server. No account, no subscription, no Google Play Services. You install the APK, tap once to download a language model, and that is it.

## What it does

- **Chat that stays on your phone.** The model runs locally through LiteRT, with hardware acceleration on a Snapdragon 8 Gen 3 or a Tensor G4. Conversations live in a database on your phone and nowhere else
- **Reads photos and documents.** Add a photo and ask a question about it. Text is always read with offline OCR, also when you have no vision model installed
- **Searchable screenshots**, like Pixel Screenshots but local. Every screenshot is read once, gets a title and can then be found by what is in it. The photos themselves stay in your gallery and are never copied
- **Notifications that calm down.** A series of messages becomes one quiet summary with action points. Urgent ones get a notification with buttons for a suggested reply and for your calendar
- **An assistant on the power button.** Set MarkMaaktAI as your default assistant and a floating panel appears over the app you are in, where you can ask about what is on your screen
- **Web search when you want it.** One switch in the input bar. When it is off, no packet leaves your phone
- **Material You.** The whole palette follows your wallpaper, the app icon included, with a pure black mode for OLED screens

## Install

You need Android 12 or newer.

1. Download `MarkMaaktAI.apk` from the [latest release](https://github.com/Marukiee/MarkMaaktAI/releases/latest) on your phone. It is about 63 MB and arm64 only, so any phone of the last few years.
2. Open it. Android asks once whether the app you open it with (your browser or files app) may install apps from unknown sources. Turn that on, go back and tap Install.
3. When the app asks for a model, tap **Download Qwen 2.5 1.5B**. It is about 1.5 GB, needs no account, and after that everything works offline.

Already have a `.task` or `.litertlm` file? Choose **I already have a model file** and point to it. The app copies it to its own storage. The app also works without a model: screenshots and photos are then read with OCR, which needs no AI model.

You can also add the repository to [Obtainium](https://obtainium.imranr.dev). The app checks for updates at most once a day.

### Which models

Only models that download without an account or licence acceptance. Gemma and Llama are left out on purpose: they return a 401 unless you are logged in to Hugging Face and have accepted their terms, which is exactly the barrier this app does not want.

| Model | Size | For |
| --- | --- | --- |
| Qwen 2.5 1.5B | 1.5 GB | The recommended choice, good at Dutch |
| Qwen 2.5 0.5B | 0.5 GB | Fast, fine for summaries |
| DeepSeek R1 Distill 1.5B | 1.7 GB | Reasons through it, so slower and more thorough |
| SmolLM2 135M | 136 MB | For a phone with little space |
| FastVLM 0.5B | 1.1 GB | Describes what is in a photo |
| Vosk NL or EN | 40 MB | Offline speech recognition |

## Permissions and why

| Permission | For | Required |
| --- | --- | --- |
| `INTERNET` | Downloading the model, web search, update check | No, everything keeps working without |
| Notification access | Reading messages to summarise them | No, only for the overview |
| `READ_MEDIA_IMAGES` | Reading screenshots | No, only for the screenshots tab |
| `RECORD_AUDIO` | Dictating | No |
| `CAMERA` | Taking a photo for a question | No |
| Battery exemption | Keeping the notification reader alive | No, but recommended |

## Runs on a clean phone

Built and tested with GrapheneOS and the stricter Sony builds in mind:

- No dependency on Google Play Services. Text recognition is the bundled ML Kit variant that carries its model in the APK
- Speech recognition goes through Vosk, with the system recogniser only when there is one
- All network traffic is optional and visible: search is off by default, downloads start when you say so, and the update check runs at most once a day
- The battery exemption is asked for, not forced, because without it only the background part is less reliable

## Build

You need JDK 21 and the Android SDK with platform 36.

```bash
git clone https://github.com/Marukiee/MarkMaaktAI.git && cd MarkMaaktAI
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. The build only produces arm64: the AI runtime, the text recognition and Vosk each have their own native library per architecture, and adding x86 for the emulator makes the download three times as big for hardware nobody runs this on.

For a signed release, create a `keystore.properties` in the root:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file `assembleRelease` falls back to debug signing, so the build never breaks on a missing key. Every tag that starts with `v` builds a release and attaches the APK. More on how the app is put together is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

AGPL-3.0, see [LICENSE](LICENSE). Made by Mark Maakt Media.
