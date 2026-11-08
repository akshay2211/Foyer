# foyer

A quiet, text-first Android launcher with a fully on-device AI assistant.

Foyer is a minimal home screen in the spirit of Niagara Launcher and
Minimalist Phone — plain-text app list, no icon grid, no widgets — plus the
thing neither has: a natural-language assistant that runs **entirely on your
phone**. Type or say *"turn on wifi and open spotify"* and it happens.
Nothing is sent to a server, no account exists, and airplane mode changes
nothing.

[Get the source on GitHub](https://github.com/akshay2211/Foyer){ .md-button }

## How it's put together

```
com.foyerlauncher.foyer/
├── MainActivity                     # manifest components
├── ui/      screens, components, theme
├── data/    LauncherStore, AppEntry, UsageTracker
└── ai/      router/  (deterministic: FastRouter, CommandRouter, AppIndex…)
             engine/  (Assistant, LlmEngine via MediaPipe, ModelDownloader)
```

Single-activity Jetpack Compose, plain snapshot-state holders persisted to
SharedPreferences, and a two-tier AI pipeline — a deterministic router first,
the model as semantic fallback. The reasoning behind every choice (including
why there is no ViewModel/Hilt/Room layer) lives in the
[design guide](DESIGN_GUIDE.md).

## Building

Requirements: JDK 17+, Android Studio (current stable). Min SDK 24.

```sh
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests
./gradlew ktlintCheck lintDebug  # style + lint (CI gates all of these)
```

To try the assistant you need a MediaPipe `.task` model — enable
**on-device ai** in settings and tap a suggestion (the Qwen models download
without any account). See [the assistant](assistant.md) for how the pipeline
works.

## API reference

Contributor-facing API documentation is generated with Dokka on every push:
[api reference](/api/).
