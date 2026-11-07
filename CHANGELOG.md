# changelog

All notable changes to Foyer, newest first. Versions follow
`versionName (versionCode)` as uploaded to Google Play.

## 1.0 (1) — 2026-11-07

First release. Less phone, on purpose.

### the home screen
- Text-first home: up to 10 favorites as a plain-text list — no icon grid,
  no widgets, no badges — plus two corner shortcuts.
- Four clock styles (digital, analogue, minimal, lock) or none; tap any of
  them to open your clock app.
- The full app list is one swipe away, with an alphabetical fast scroll,
  work-profile apps marked inline, and hidden apps tucked behind a single row.

### the assistant
- "ask me" — type or speak natural language ("turn on wifi and open spotify")
  and it happens. Obvious commands resolve instantly in deterministic code;
  everything else goes to a local LLM that returns a structured action.
- Runs entirely on your phone: no server, no account, airplane mode changes
  nothing.
- Bring your own model — Foyer ships no weights. Paste a direct link to any
  MediaPipe `.task` model, or use the one-tap suggestions for Qwen 2.5 and
  Gemma 3; it downloads once, self-tests, and stays on the device.
- Multi-turn conversation, "did you mean…?" disambiguation, and app-inventory
  answers from the local index.

### mindful usage
- Screen time and unlock count sit quietly under the clock.
- Per-app daily limits with a conscious 5-minute bypass, mark-as-distracting,
  and a focus mode with a countdown that hides distracting favorites.
- A usage summary screen for the day.

### stays out of your way
- App hiding, user-taught aliases, and launch-by-typing.
- Gestures: swipe up / swipe down / double-tap, each configurable, plus an
  optional lock-screen gesture (accessibility service used for that alone).
- Curated background tones, matte dark and light themes, font choices.
- Backup and restore to a JSON file you own.

### privacy
- Everything stays on the phone. No analytics, no accounts, no network calls
  except the model download you explicitly start.
- Permissions are requested at the point of use and all optional: microphone
  for voice, usage access for screen time, notifications for the limit nudge.
