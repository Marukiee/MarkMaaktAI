# MarkMaaktAI

[![Release](https://img.shields.io/github/v/release/Marukiee/MarkMaaktAI?label=release)](https://github.com/Marukiee/MarkMaaktAI/releases/latest)
[![Build](https://github.com/Marukiee/MarkMaaktAI/actions/workflows/release.yml/badge.svg)](https://github.com/Marukiee/MarkMaaktAI/actions/workflows/release.yml)
[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)](#)
[![Licentie](https://img.shields.io/badge/licentie-AGPL--3.0-blue)](#licentie)

Een AI-assistent die volledig op je eigen telefoon draait. Chatten, foto's laten
lezen, je screenshots doorzoekbaar maken en je meldingen tot rust brengen, zonder
dat er iets naar een server gaat.

Geen account, geen abonnement, geen Google Play Services. Je installeert de APK,
tikt een keer op downloaden voor een taalmodel, en dat is het.

## Wat het doet

- 💬 **Chat die op je toestel blijft**. Het model draait lokaal via LiteRT, met
  hardwareversnelling op een Snapdragon 8 Gen 3 of een Tensor G4. Gesprekken staan
  in een database op je telefoon en nergens anders
- 📸 **Foto's en documenten lezen**. Voeg een foto toe en stel er een vraag over.
  Tekst wordt altijd uitgelezen met offline OCR, ook als je geen vision-model hebt
  geïnstalleerd
- 🔎 **Screenshots doorzoekbaar**, zoals Pixel Screenshots maar dan lokaal. Elke
  screenshot wordt een keer gelezen, krijgt een titel en is daarna terug te vinden
  op wat erin staat. De foto's zelf blijven in je galerij en worden nooit gekopieerd
- 🔕 **Meldingen die tot bedaren komen**. Een reeks berichten wordt één rustige
  samenvatting met actiepunten. Spoed krijgt een melding met knoppen voor een
  antwoordvoorstel en voor de agenda
- 🎙️ **Assistent via de aan-uitknop**. Stel MarkMaaktAI in als standaardassistent
  en er verschijnt een zwevend paneel over de app waar je in zit, met een vraag over
  wat er op je scherm staat
- 🌐 **Webzoeken als je het wilt**. Eén schakelaar in de invoerbalk. Staat hij uit,
  dan gaat er geen enkel pakketje naar buiten
- 🎨 **Material You**. Het hele palet volgt je achtergrond, inclusief het app-icoon,
  met een diepzwarte modus voor OLED-schermen

## Installeren

1. Pak `MarkMaaktAI.apk` uit de
   [nieuwste release](https://github.com/Marukiee/MarkMaaktAI/releases/latest).
   Ongeveer 63 MB, alleen arm64, dus elke telefoon van de laatste jaren
2. Installeer hem (sta installeren uit onbekende bron toe)
3. Tik bij het instellen op **Download Qwen 2.5 1.5B**. Ongeveer 1,5 GB, geen
   account nodig, en daarna werkt alles offline

Heb je zelf al een `.task` of `.litertlm` bestand, kies dan **Ik heb al een
modelbestand** en wijs het aan. De app kopieert het naar zijn eigen opslag.

De app blijft bruikbaar zonder model: screenshots en foto's worden dan met OCR
gelezen, wat volledig zonder AI-model werkt.

### Welke modellen

Alleen modellen die zonder account of licentie-acceptatie downloaden. Gemma en Llama
staan er bewust niet bij: die geven een 401 tenzij je ingelogd bij Hugging Face hun
voorwaarden hebt aangeklikt, en dat is precies de drempel die deze app niet wil.

| Model | Grootte | Waarvoor |
|-------|---------|----------|
| Qwen 2.5 1.5B | 1,5 GB | De aanbevolen keuze, goed in Nederlands |
| Qwen 2.5 0.5B | 0,5 GB | Snel, prima voor samenvattingen |
| DeepSeek R1 Distill 1.5B | 1,7 GB | Redeneert door, dus trager en grondiger |
| SmolLM2 135M | 136 MB | Voor een telefoon met weinig ruimte |
| FastVLM 0.5B | 1,1 GB | Beschrijft wat er op een foto staat |
| Vosk NL of EN | 40 MB | Offline spraakherkenning |

## Rechten en waarom

| Recht | Waarvoor | Verplicht |
|-------|----------|-----------|
| `INTERNET` | Model downloaden, webzoeken, updatecontrole | Nee, alles blijft werken zonder |
| Meldingstoegang | Berichten lezen om samen te vatten | Nee, alleen voor het overzicht |
| `READ_MEDIA_IMAGES` | Screenshots inlezen | Nee, alleen voor de screenshots-tab |
| `RECORD_AUDIO` | Inspreken | Nee |
| `CAMERA` | Foto maken voor een vraag | Nee |
| Batterijuitzondering | Meldingenlezer in leven houden | Nee, wel aangeraden |

## Draait op een schone telefoon

Gebouwd en getest met GrapheneOS en de strengere Sony-builds in gedachten:

- Geen enkele afhankelijkheid van Google Play Services. De tekstherkenning is de
  gebundelde ML Kit-variant die het model in de APK meeneemt
- Spraakherkenning gaat via Vosk, met de systeem-recognizer alleen als die er is
- Alle netwerkverkeer is optioneel en zichtbaar: zoeken staat standaard uit,
  downloads start je zelf, en de updatecontrole draait hooguit één keer per dag
- De batterijuitzondering wordt gevraagd, niet afgedwongen, want zonder is alleen
  het achtergronddeel minder betrouwbaar

## Architectuur

```
  UI (Jetpack Compose, Material 3)
  chat  screenshots  overzicht  instellingen  assistent-overlay
        |
  ViewModels (MVVM, Hilt)
        |
  +-----+---------------------------+
  |                                 |
  Repositories                      AiOrchestrator
  chat, meldingen, screenshots,     kiest model per taak
  modellen, updates                        |
  |                                 InferenceEngine
  Room + FTS4     DataStore         +-------+--------+
  gesprekken      instellingen      LiteRT          llama.cpp
  meldingen                         (nu actief)     (stub)
  screenshots
        |
  Achtergrond (WorkManager)
  meldingen samenvatten, screenshots indexeren
```

Het hele AI-deel zit achter één interface. LiteRT draait vandaag, de llama.cpp-laag
staat er als stub achter dezelfde interface, zodat overstappen op GGUF later één
binding in `AppModule` is en verder niets.

## Zelf bouwen

Nodig: JDK 21 en de Android SDK met platform 36.

```bash
git clone https://github.com/Marukiee/MarkMaaktAI.git && cd MarkMaaktAI
./gradlew assembleDebug
```

De APK komt in `app/build/outputs/apk/debug/`. De build levert alleen arm64: de
AI-runtime, de tekstherkenning en Vosk hebben elk een eigen native library per
architectuur, en x86 meenemen voor de emulator maakt de download drie keer zo groot
voor hardware waar niemand dit op draait.

Voor een getekende release maak je een `keystore.properties` in de root:

```properties
storeFile=/pad/naar/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Zonder dat bestand valt `assembleRelease` terug op debug-signing, zodat de build
nooit stukloopt op een ontbrekende sleutel.

Elke tag die met `v` begint bouwt automatisch een release en hangt de APK eraan.

## Screenshots

_Volgen zodra de eerste build op een toestel staat._

| Chat | Screenshots | Overzicht | Assistent |
|------|-------------|-----------|-----------|
| ![Chat](docs/screenshots/chat.png) | ![Screenshots](docs/screenshots/shots.png) | ![Overzicht](docs/screenshots/digest.png) | ![Assistent](docs/screenshots/assist.png) |

## Techniek

| Laag | Gebruikt |
|------|----------|
| UI | Jetpack Compose · Material 3 · eigen squircle- en springlaag |
| Architectuur | MVVM · Hilt · Coroutines en Flow |
| Opslag | Room met FTS4 · DataStore |
| AI | LiteRT (MediaPipe GenAI) · ML Kit bundled OCR · Vosk |
| Achtergrond | WorkManager · NotificationListenerService |
| Assistent | VoiceInteractionService met schermcontext |
| Uitrollen | GitHub Actions · in-app updatecontrole |

## Licentie

AGPL-3.0
