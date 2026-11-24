# changelog

All notable changes to Foyer, newest first. Versions follow
`versionName (versionCode)` as uploaded to Google Play.

## 1.1.0 (4) — 2026-11-23

### the home screen
- The clock and the favorites list can now be moved and resized. Long-press
  the home background (or home menu → edit home) for a Pixel-style edit mode:
  drag either block anywhere on the grid, pull its edge handles to resize,
  and tap done. The clock scales with its box; the favorites list shows as
  many rows as fit. Everything snaps to the grid and springs back rather
  than overlapping.
- Pick the grid density right inside edit mode — 5×8, 6×10 or 7×12 cells.
  Your layout re-fits when you switch.
- Set a wallpaper from your photos (home menu → wallpaper, or settings →
  appearance). The photo is picked through Android's photo picker — Foyer
  never gets access to your library, only the one image — then sized down
  and stored privately, drawn under a soft scrim so the text stays readable.

### appearance
- Opt in to "theme from wallpaper": Foyer pulls the photo's dominant color
  and one vivid accent, derives a full palette with guaranteed contrast,
  and recolors the whole launcher — home, drawer, settings, everything.
  It's a switch in appearance settings; turn it off and your chosen theme
  comes back untouched. While it's on, the overridden theme, accent and
  background pickers step out of the way.

## 1.0.2 (3) — 2026-11-17

### the app list
- Drawer search now matches fuzzily. Type letters in order and an app still
  surfaces even when they aren't next to each other ("wsp" finds WhatsApp),
  with prefix and word-start matches ranked first. The exact letters you
  typed are highlighted in every result.

### mindful usage
- Apps marked as distracting now appear dimmed in the app list, so they pull
  less attention while you browse. (They already vanish while focus is active.)

### privacy & security
- Your model-host access token stays on the device: it's kept out of
  Android's cloud auto-backup and device-to-device transfer, and redacted
  from logs.
- Downloaded models are verified by SHA-256 checksum and fetched over HTTPS
  only, so a corrupted or tampered file is rejected before it's ever loaded.

## 1.0.1 (2) — 2026-11-12

- Removed the lock-screen gesture and with it the accessibility service and
  the `BIND_ACCESSIBILITY_SERVICE` permission — Foyer now requests nothing
  that Play's accessibility-API policy applies to. A gesture previously set
  to "lock screen" quietly falls back to its default.
- Fixed the dial clock's minute lens clipping at the widget's right edge.

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
