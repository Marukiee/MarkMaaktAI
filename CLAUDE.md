# MarkMaaktAI, working notes

Vaste afspraken voor dit project. Lees dit voordat je iets aanpast.

## Schrijfstijl

- **Nooit em-dashes** (`—`). Ook geen en-dash als koppelteken. Gebruik een gewone
  komma, een punt, of haakjes. Dit geldt overal: code, comments, commits, README,
  UI-teksten, en antwoorden in de chat.
- Geen AI-riedels. Geen "moreover", "delve", "seamlessly", "it's important to note".
- Kort houden. Een zin die niets toevoegt gaat eruit.
- Comments in de code leggen uit **waarom**, niet wat. Als de regel zichzelf uitlegt
  staat er geen comment bij.
- Nederlands in de UI (`values-nl`), Engels als basis (`values`).

## Design en animatie

Dit is waar de app op beoordeeld wordt. Nooit op afknijpen.

- Alles wat beweegt, beweegt via `MarkMotion`. Positie en grootte op een spring,
  kleur en opacity op een tween. Die twee nooit omdraaien.
- `bouncyClickable` op alles wat aantikbaar is. Overal dezelfde dip (`0.96f`).
- Verwijderen: swipe weg, daarna sluit de lijst elastisch met `shrinkVertically`.
  Nooit direct verwijderen, dan springt de lijst.
- Laadanimatie is de **pill uit het app-icoon**: draait, staat even stil, draait weer
  (`PillSpinner`). Nooit een generieke `CircularProgressIndicator`.
- De ambient glow staat alleen aan als er echt gewerkt wordt. Een animatie die altijd
  loopt zegt niets meer.
- Navigatie heeft **één bewegende pill**, niet vier losse achtergronden. Hij rekt
  uit in de bewegingsrichting naar rato van de afstand.
- Licht/donker wisselt met een crossfade over alle kleurrollen (`ColorScheme.animated()`).
- Pure black is echt `#000000` voor de achtergrond, maar containers houden een
  ladder van donkergrijs zodat diepte zichtbaar blijft.
- Referentie voor kwaliteit en toon: MarkMySteps (`github.com/Marukiee/MarkMySteps`).

## Gebruiksgemak

- **APK installeren en klaar.** Geen HuggingFace-account, geen tokens, geen licentie
  aanklikken. Daarom staan Gemma en Llama niet in de catalogus: die geven 401 zonder
  ingelogde licentie-acceptatie. Alleen modellen die zonder account downloaden.
- Eén knop in de onboarding downloadt het aanbevolen model. De rest is optioneel.
- Zonder model werkt de app nog steeds: OCR op foto's en screenshots draait zonder
  enig AI-model.
- Moeilijke instelling? `HelpTip` met een vraagteken ernaast, één korte zin. Een
  instelling die zichzelf uitlegt krijgt er geen.

## Techniek

- Package: `nl.markmaaktmedia.markmaaktai`, minSdk 31, compileSdk 36.
- Hilt, MVVM, Room met FTS4, DataStore, Compose Material 3.
- AI achter `InferenceEngine`. LiteRT draait nu, llama.cpp is een stub achter
  dezelfde interface zodat omschakelen één binding in `AppModule` is.
- GitHub: `Marukiee/MarkMaaktAI`. De update-checker leest de releases-feed en de
  workflow hangt de APK onder een vaste naam aan de release.
- Bouwen: `JAVA_HOME=/home/Mark/toolchain/jdk-21* ./gradlew assembleDebug`.
  Systeem-JDK is 25, daar draait AGP niet op.
- Alleen `arm64-v8a` in `abiFilters`. Zonder die filter is de APK 231 MB in plaats
  van 63 MB, omdat MediaPipe, ML Kit en Vosk elk een native library per ABI hebben.
  Gevolg: draait niet op een x86-emulator, wel op elk echt toestel.

## Werkwijze

- Fase 1 vragen stellen, fase 2 doorbouwen zonder tussentijds te stoppen.
- Bij twijfel over een keuze: kies, bouw door, en zeg achteraf wat je koos.
