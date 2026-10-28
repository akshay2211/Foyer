# Launcher Design Guide

A comprehensive guide combining the best aspects of Niagara Launcher (efficiency, single-list UX, notification integration) and Minimalist Phone (distraction reduction, app hiding, text-based focus).

## 1. Core Feature Set

### From Niagara Launcher (Efficiency & UX)

- **Single-List Interface:** A clean, vertically scrollable home screen displaying essential apps in plain text or minimalist icons.
- **Embedded Notifications:** Direct notification previews below app names on the home screen with quick-reply support.
- **Alphabetical Scrollbar:** A right-hand fast-scroll gesture (A–Z) to immediately reach non-favorite apps without launching a separate app drawer.
- **One-Handed Ergonomics:** Adaptive alignment and gesture-friendly interactions optimized for single-thumb usage.

### From Minimalist Phone (Focus & Mindful Usage)

- **Monochrome / Text-Only Theme:** Option to force all app titles into plain text, eliminating colourful app icons that trigger dopamine loops.
- **Intentional Friction:** Blocking or adding delays (e.g., 5–15 second countdowns or confirmation prompts) before opening distracting apps.
- **App Hiding & Renaming:** Ability to hide apps entirely or rename them to less appealing labels.
- **In-App Notification Filtering:** Muting low-priority notifications while retaining critical alerts.

## 2. Technical Dos (Best Practices)

### Architecture & Frameworks

- **Do** use Jetpack Compose for building fluid, reactive, and minimalist UI components.
- **Do** follow modern Android architecture (MVVM/Clean Architecture with Kotlin Coroutines and Flow).

### Performance

- **Do** keep the home screen launch instant by pre-caching installed apps using PackageManager asynchronous querying.
- **Do** perform heavy queries (e.g., app usage statistics, icon caching) off the main thread using background Coroutines.

### User Experience

- **Do** provide granular controls for intentional friction settings (e.g., custom timers per app).
- **Do** keep memory footprint extremely low since a launcher remains persistent in device RAM.

### Localization & Maintainability

- **Do** externalize all user-facing text into `strings.xml` for seamless localization.

## 3. Technical Don'ts (Anti-Patterns)

### UI & Design

- **Don't** clutter the screen with traditional widgets, live wallpapers, or complex grid layouts by default.
- **Don't** introduce non-standard gestures that conflict with system-level Android swipe gestures.

### Performance & System Integration

- **Don't** execute database operations or package querying directly on the MainThread (UI thread).
- **Don't** request sensitive permissions (e.g., Notification Access, Usage Stats) up-front before explaining their value to the user.
- **Don't** run persistent foreground services without clear justification, as this unnecessarily consumes battery.

## 4. Recommended Tech Stack

| Component | Recommendation |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose |
| State Management | ViewModel + StateFlow |
| Local Storage | Room Database / DataStore |
| App Querying | PackageManager |
| Notification Integration | NotificationListenerService |
