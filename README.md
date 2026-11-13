<p align="center">
  <img src="marketing/out/play/feature-graphic.png" width="100%" alt="foyer — a quiet home for your phone. A text-first launcher with a fully on-device AI assistant, shown on a phone with a thin clock and a plain text app list."/>
</p>

<p align="center">
  a quiet, text-first Android launcher with a fully on-device AI assistant.<br/>
  <a href="https://foyerlauncher.com">foyerlauncher.com</a>
</p>

<p align="center">
  <a href="https://github.com/akshay2211/Foyer/actions/workflows/ci.yml"><img src="https://github.com/akshay2211/Foyer/actions/workflows/ci.yml/badge.svg" alt="CI status"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue.svg" alt="License: GPL-3.0"/></a>
  <a href="#building"><img src="https://img.shields.io/badge/min%20sdk-24-success.svg" alt="Min SDK 24"/></a>
</p>
<p align="center">
  <img src="marketing/out/play/01-home.png" width="16%" alt="Home screen: a thin clock, today's screen time, and a plain-text list of favourite apps"/>
  <img src="marketing/out/play/02-say.png" width="16%" alt="The assistant: you type 'turn on wifi and open spotify' and Foyer runs both steps"/>
  <img src="marketing/out/play/03-drawer.png" width="16%" alt="App drawer: text search matching 'in' with an a-to-z fast-scroll rail"/>
  <img src="marketing/out/play/04-clocks.png" width="16%" alt="The four clock styles: digital, bold, rotating dial, and plain hands"/>
  <img src="marketing/out/play/05-appearance.png" width="16%" alt="Appearance settings: accent swatches, three fonts, and background tones"/>
  <img src="marketing/out/play/06-light.png" width="16%" alt="The same home screen in the full light theme"/>
</p>
<p align="center"><sub>The app UI above is rebuilt in HTML from the same design tokens, not screenshotted — the <a href="https://foyerlauncher.com">live demo</a> runs it for real.</sub></p>

---

Foyer is a minimal home screen in the spirit of Niagara Launcher and
Minimalist Phone — plain-text app list, no icon grid, no widgets, no noise —
plus the thing neither has: a natural-language assistant that runs **entirely
on your phone**. Type or say "turn on wifi and open spotify" and it happens.
Nothing is sent to a server, no account exists, and airplane mode changes
nothing.

## Features

- **Text-first home** — up to 10 favorites plus two corner shortcuts; the
  full app list is one swipe away, with a configurable alphabetical fast
  scroll.
- **On-device assistant** — voice ("ask me") or the typed command bar. Obvious
  commands are resolved instantly by deterministic code; everything else goes
  to a local LLM that returns a structured action which plain code executes.
  Multi-turn conversation, disambiguation ("did you mean…?"), and app
  inventory answers from the local index.
- **Bring your own model** — Foyer ships no model weights. Paste a direct
  link to any MediaPipe `.task` model (one-tap suggestions included for
  Qwen 2.5 and Gemma 3) and it downloads, self-tests, and runs locally.
- **Mindful usage** — screen time and unlock counts under the clock, per-app
  daily limits with a conscious 5-minute bypass, mark-as-distracting,
  focus mode with a countdown, and a usage summary screen.
- **Stays out of your way** — app hiding, user-taught aliases, work/private
  profile support, gestures (swipe, double-tap), curated
  background tones, digital or analogue clock, backup/restore to a JSON
  file you own.

## Privacy

Everything stays on the phone. No network calls except the model download
you explicitly start. No analytics, no accounts, no notification access.
The only permissions requested are explained at the point of use
(microphone for voice, usage access for screen-time features, notifications
for the limit-reached nudge).

## Architecture

Single-activity Jetpack Compose (foundation-only home; Material 3
Expressive settings themed from the app palette), plain
snapshot-state holders persisted to SharedPreferences, and a two-tier AI
pipeline — a deterministic router first, the model as semantic fallback:

```
com.foyerlauncher.foyer/
├── MainActivity                     # manifest components
├── ui/      screens, components, theme
├── data/    LauncherStore, Apps, UsageTracker
└── ai/      router/  (deterministic: FastRouter, CommandRouter, AppIndex…)
             engine/  (Assistant, LlmEngine via MediaPipe, ModelDownloader)
```

The choices (including why there is no ViewModel/Hilt/Room layer) are
deliberate and documented in [docs/DESIGN_GUIDE.md](docs/DESIGN_GUIDE.md).

## Building

Requirements: JDK 17+, Android Studio (current stable). Min SDK 24,
target SDK 36, Kotlin 2.2, AGP 9.2.

```sh
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # android lint
```

To try the assistant you'll need a MediaPipe `.task` model — enable
"on-device ai" in settings and tap one of the suggestions (the Qwen models
download without any account).

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for the
ground rules (DCO sign-off required) and
[docs/DESIGN_GUIDE.md](docs/DESIGN_GUIDE.md) for the design principles that
pull requests are reviewed against.

## Security

Found a vulnerability? Please don't open a public issue — see
[SECURITY.md](SECURITY.md) for how to report it privately.

## License

[GPL-3.0](LICENSE) © Akshay Sharma
