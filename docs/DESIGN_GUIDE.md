# Foyer Design Guide

What Foyer is, the principles behind it, and the technical rules the codebase
actually follows. The feature direction synthesizes the best of Niagara
Launcher (efficiency, single-list UX) and Minimalist Phone (distraction
reduction, text-based focus), plus the thing neither has: a fully on-device AI
assistant.

## 1. Principles

- **Text-first, lowercase, calm.** App labels as plain text, no icon grid, no
  widgets, no color noise. The launcher should feel like a quiet foyer, not a
  casino floor.
- **AI decides, code executes.** Natural-language commands (typed or spoken)
  are interpreted by an on-device model that returns a structured action;
  plain code parses and executes it. Obvious commands short-circuit through a
  deterministic resolver and never touch the model. No hardcoded
  regex/keyword intent rules in the semantic path.
- **Everything stays on the phone.** No server, no account, no analytics. The
  model runs locally; airplane mode changes nothing.
- **Bring your own model.** Foyer never ships or hosts model weights. The user
  pastes a MediaPipe `.task` URL (one-tap suggestions provided) and downloads
  it under the model's own license.
- **Friction where it helps, nowhere else.** Distraction features (limits,
  focus mode, nudges) add deliberate pauses; everything else is instant.

## 2. Feature Map

### Shipped

- Single-list home: favorites (max 10) + two corner shortcuts, drawer one
  swipe away.
- Alphabetical fast scroll in the drawer, configurable: icon-preview card or
  simple rail.
- App hiding, and user-taught aliases (also used by the AI resolver).
- Distraction tools: mark-distracting apps, per-app daily limits with a
  conscious 5-minute bypass and a limit-reached notification nudge (inexact
  alarm + fire-time usage re-check — no service, no foreground watching),
  focus mode with a countdown, usage summary (screen time, top apps, unlocks).
- On-device assistant: voice ("ask me") + typed command bar; multi-turn
  conversation, disambiguation questions, inventory answers from the local
  app index.
- Work/private profile apps, backup/restore to a user-chosen JSON file,
  wallpaper mode behind a scrim, curated background tones, digital/analogue
  clock, gestures (swipe up/down, double tap, lock screen via accessibility
  service).

### Inspiration backlog (not implemented, not committed to)

- Embedded notification previews under app names (Niagara) — would require
  `NotificationListenerService`; weigh the privacy story first.
- Notification filtering (Minimalist Phone) — same dependency.
- Grayscale/monochrome icon rendering where icons appear at all.

## 3. Architecture (as built — deliberate choices)

```
com.foyerlauncher.foyer/
├── MainActivity, LockService      # manifest components stay at the root
├── ui/screens, ui/components, ui/theme
├── data/                          # LauncherStore, Apps, UsageTracker
└── ai/
    ├── engine/                    # Assistant, LlmEngine, ModelDownloader, AiMetrics
    └── router/                    # deterministic tier: FastRouter, CommandRouter,
                                   # AppIndex, AppCatalog, ActionRegistry, Normalizer
```

- **Single activity, Compose-only, unidirectional data flow.** State flows
  down, events go up as lambdas. Home surfaces are foundation-only Compose;
  settings pages use Material 3 Expressive components themed entirely from
  the Foyer palette via `FoyerMaterialTheme` (ui/theme) — never default M3
  purple.
- **Plain state holder, no ViewModel.** `LauncherStore` is a snapshot-state
  class persisted to SharedPreferences. This is intentional: the activity
  handles `configChanges` itself (standard launcher practice), a home app's
  process lives as long as the device, and persisted state reloads on process
  death. Don't add ViewModel/Hilt/Room/DataStore to "modernize" — they solve
  problems this app doesn't have. Revisit only if a concrete need appears
  (e.g. multi-screen navigation with per-screen state).
- **Dependency direction:** `ui → data/ai`, `ai → data`, `data → nothing
  internal`. Keep it that way; the model types (`AppEntry`) live in `data`.
- **AI layering:** `ai/router` is the deterministic tier (no model, instant);
  `ai/engine` is the model tier (`LlmEngine` wraps MediaPipe `LlmInference`
  for whatever `.task` model the user downloaded). `Assistant` orchestrates:
  router first, model as semantic fallback, web search as last resort.

## 4. Technical Dos

- **Do** enumerate apps via `LauncherApps` + `UserManager` (not bare
  `PackageManager`) — it's the only way to see work/private-profile apps —
  and reload on package callbacks, off the main thread.
- **Do** keep heavy work (usage stats, model inference, downloads) in
  coroutines on `Dispatchers.IO`; the home screen must never jank.
- **Do** gate sensitive permissions behind an explanation at point of use
  (usage access, microphone, accessibility), never up-front.
- **Do** keep persisted encodings stable (`AppEntry.key`, prefs, backup JSON
  schema) — migrate explicitly when they must change.
- **Do** keep memory low: the launcher is resident. Prefer lazy init
  (model weights load on first AI use, not at launch).
- **Do** log AI decisions under the `FoyerAI` tag so routing (deterministic
  vs model) stays debuggable: `adb logcat -s FoyerAI:V`.

## 5. Technical Don'ts

- **Don't** reintroduce a bundled or self-hosted model, or a second AI
  backend. One engine, user-supplied model.
- **Don't** add hardcoded intent regexes to the semantic path — teach the
  deterministic router or improve the prompt instead.
- **Don't** block the main thread with package queries, prefs migration, or
  file IO.
- **Don't** add persistent foreground services. The one service (`LockService`)
  is an accessibility service that processes no events; keep it that honest.
- **Don't** introduce gestures that fight system back/home navigation.
- **Don't** clutter the home surface: no widgets, no icon grids, no live
  wallpaper rendering of our own (wallpaper mode just shows the system one
  behind a scrim).
- **Don't** use Material components on home/drawer surfaces — M3 is scoped
  to settings and utility pages; the home screen stays foundation-only.

## 6. Actual Tech Stack

| Component | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (foundation for home; Material 3 Expressive for settings, themed from the Foyer palette) |
| State | Compose snapshot state in a plain store class |
| Persistence | SharedPreferences (+ user-exported JSON backup) |
| App enumeration | `LauncherApps` across all user profiles |
| On-device LLM | MediaPipe `tasks-genai` `LlmInference`, BYO `.task` model |
| Speech input | Android `SpeechRecognizer` (on-device where available) |
| Usage data | `UsageStatsManager` (opt-in usage access) |

## 7. Known Gaps (pre-release checklist material)

- **Localization:** almost all UI text is hardcoded lowercase English;
  extraction to `strings.xml` is required before any localization pass
  (only the app name and accessibility-service strings are externalized
  today).
- `LauncherScreen` holds ten independent overlay booleans; collapse into one
  sealed `Overlay` state when next touched.
- Qwen/Gemma chat templating in `LlmEngine` uses Gemma-style turn tokens;
  verify template behavior per model family as more suggestions are added.
