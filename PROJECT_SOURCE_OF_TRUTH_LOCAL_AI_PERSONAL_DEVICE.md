# Open-Source Local AI Personal Device Platform
## Source of Truth (SoT)

**Version:** 1.0  
**Date:** 22 August 2026  
**Status:** Product + Architecture + Roadmap Source of Truth  
**Primary platform:** Android  
**Secondary platform:** iOS companion  
**Shared technology:** Kotlin Multiplatform (KMP)  
**Core AI principle:** Local-first, retrieval-first, deterministic execution

---

# 1. Executive Vision

This project is an **open-source, privacy-first personal AI platform for Android and iOS**.

The first product is a **minimalist Android launcher** with a much better UI/UX than conventional minimalist launchers and a prominent **Ask** interaction powered by an on-device local LLM.

The long-term vision is larger than a launcher:

> **A personal AI interface that helps users operate their phone, understand their digital habits, maintain useful personal records, and reduce unnecessary phone usage — while keeping as much intelligence and data on-device as possible.**

The project should remain:

- Free
- Open source
- Privacy-first
- Local-first
- Offline-capable for core functionality
- Extensible
- KMP-based where shared code provides real value

The project should not become a generic chatbot.

The AI is primarily an **interface to the user's device and personal data**, not a chat product.

---

# 2. Core Product Principles

## 2.1 Local First

Core functionality should work without an internet connection.

The project should prefer:

```text
Device
  ↓
Local data
  ↓
Local retrieval
  ↓
On-device AI
  ↓
Deterministic execution
```

rather than:

```text
Device
  ↓
Cloud
  ↓
LLM
  ↓
Device
```

Cloud services may be added later as explicitly optional functionality.

---

## 2.2 Retrieval First

The LLM must not receive all available device information.

Never routinely send:

- The entire installed-app list
- Complete UsageStats history
- Complete food history
- Complete personal memory
- All settings
- All device state

Instead:

```text
User request
    ↓
Classify / normalize
    ↓
Retrieve only relevant information
    ↓
Give small context to LLM
    ↓
Structured result
    ↓
Validate
    ↓
Execute/store
```

The LLM is a **reasoning layer**, not a database.

---

## 2.3 Deterministic First

Do not use an LLM when a deterministic implementation is sufficient.

Example:

> "Open camera"

should not require an LLM.

Example:

> "Take me to the Android settings where I can enable USB debugging."

may require semantic reasoning.

The intended routing hierarchy is:

```text
Exact deterministic match
        ↓
Normalized match
        ↓
Alias match
        ↓
Fuzzy/local retrieval
        ↓
Targeted LLM fallback
        ↓
Safe failure
```

---

## 2.4 LLM Never Executes Arbitrary Device Operations

The LLM may select:

```text
OPEN_CAMERA
OPEN_DEVELOPER_OPTIONS
OPEN_APP
```

It must not generate arbitrary Android package names, activity names, shell commands, or unrestricted intents.

Kotlin/platform code is responsible for execution.

---

## 2.5 Ask Only When Necessary

The assistant should not constantly ask confirmation.

Use confirmation only when ambiguity is meaningful.

Example:

```text
"Open Spotify"
→ exact match
→ open immediately
```

But:

```text
"Open blanket"
→ Blinkit score 0.71
→ ask "Did you mean Blinkit?"
```

---

# 3. Product Positioning

The project should initially be described as:

> **An open-source, privacy-first Android launcher with a local AI assistant.**

As the product expands:

> **An open-source personal AI platform for Android and iOS.**

The Android launcher is the flagship product.

The iOS product is a companion/personal-AI experience because iOS does not permit third-party apps to replace the system home screen in the same way Android does.

---

# 4. Target User Experience

The home screen should remain extremely minimal.

The main interaction is:

```text
                [ ASK ]
```

The user can speak or type.

Examples:

```text
Open Spotify
Open camera
Open developer options
Open Wi-Fi settings
Open Blinkit
What apps did I use most today?
Log my lunch
What have I eaten today?
Start a 30 minute focus session
```

The assistant should respond concisely.

The goal is:

> **Less interaction with the phone, not more interaction with an AI chatbot.**

---

# 5. Android Product

## 5.1 Android Is the Flagship

Android provides capabilities that iOS does not provide to the same degree:

- Custom launcher/home screen
- Installed app discovery
- App launching
- Extensive system settings navigation
- UsageStats
- More flexible device-level actions
- Deep integration with launcher behavior

Therefore Android should receive the deepest system integration.

---

# 6. iOS Product

iOS should use the same shared intelligence architecture but provide a platform-appropriate experience.

Potential iOS capabilities:

- Ask interface
- Local AI
- Personal memory
- Food tracking
- Habit tracking
- Widgets
- App Intents
- Siri/Shortcuts integrations
- Deep links where supported
- Focus-related integrations where available
- Personal analytics where supported by Apple APIs

Do not promise Android-equivalent system control on iOS.

The iOS product should be described as:

> **Personal AI companion**

rather than:

> **iOS launcher**

---

# 7. Kotlin Multiplatform Architecture

KMP should be used for **logic that genuinely benefits from sharing**.

Potential `commonMain`:

```text
core/
  ai/
    AssistantEngine
    AIRequest
    AIResponse
    LocalAI
    PromptBuilder

assistant/
  Intent
  Action
  ActionRegistry
  ConversationState
  PendingAction

resolver/
  FuzzyMatcher
  AppResolver
  ResolutionResult

memory/
  PersonalMemory
  MemoryEntry

food/
  FoodEntry
  NutritionEntry
  FoodLog
  NutritionCalculator

wellbeing/
  UsageSummary
  FocusSession
  HabitData

data/
  repositories
  serialization
```

Android-specific:

```text
androidMain/

launcher/
androidIntents/
usageStats/
installedApps/
geminiNano/
```

iOS-specific:

```text
iosMain/

appIntents/
shortcuts/
widgets/
localAI/
deepLinks/
```

---

# 8. AI Abstraction

Do not hard-code the product around one model.

Use an abstraction such as:

```kotlin
interface LocalAI {
    suspend fun generate(
        request: AIRequest
    ): AIResponse
}
```

Android may implement this using an appropriate Google on-device model such as Gemini Nano where supported.

iOS may use Apple's supported on-device model/API where available.

Future implementations could include:

```text
LocalAI
 ├── Android Nano
 ├── iOS on-device model
 ├── Desktop/Ollama
 └── Optional future cloud provider
```

The application logic should not depend on a specific model.

---

# 9. Assistant Architecture

The canonical request pipeline is:

```text
VOICE / TEXT
      ↓
Speech-to-text (if voice)
      ↓
Normalizer
      ↓
Pending Confirmation Check
      ↓
Deterministic Router
      ↓
Action Registry / App Resolver
      ↓
Targeted Retrieval
      ↓
Local LLM fallback
      ↓
Structured Intent
      ↓
Validator
      ↓
Deterministic Executor
      ↓
User feedback
```

---

# 10. Text Normalization

Every command should pass through a normalization layer.

Responsibilities:

- Trim whitespace
- Collapse repeated whitespace
- Normalize case for matching
- Remove harmless repeated command prefixes
- Normalize common command verbs
- Preserve original input for display/logging

Example:

```text
open open developer options
```

becomes:

```text
developer options
```

The normalizer must not destroy semantic meaning.

---

# 11. Pending Confirmation

Confirmation must be handled before the general intent pipeline.

Example:

```text
User:
Open blanket

Resolver:
Blinkit score 0.71

Assistant:
Did you mean Blinkit?

User:
Yes
```

The system should execute the pending action immediately.

It must NOT send `"yes"` to the general LLM pipeline.

Suggested model:

```kotlin
data class PendingAction(
    val action: AssistantAction,
    val question: String,
    val createdAt: Instant,
    val expiresAt: Instant
)
```

Recognize:

### Positive

```text
yes
yeah
yep
correct
that's right
open it
```

### Negative

```text
no
nope
not that
wrong one
```

If there is no pending confirmation, `"yes"` should not accidentally launch an app or trigger a random action.

---

# 12. Action Registry

Create one central registry for deterministic actions.

Example:

```kotlin
data class ActionDefinition(
    val id: String,
    val aliases: List<String>,
    val description: String,
    val category: ActionCategory
)
```

Initial actions:

```text
OPEN_CAMERA
OPEN_SETTINGS
OPEN_DEVELOPER_OPTIONS
OPEN_WIFI_SETTINGS
OPEN_BLUETOOTH_SETTINGS
OPEN_DISPLAY_SETTINGS
OPEN_SOUND_SETTINGS
OPEN_NOTIFICATION_SETTINGS
OPEN_LOCATION_SETTINGS
OPEN_ACCESSIBILITY_SETTINGS
```

Additional actions can be added later.

---

# 13. Android Settings Strategy

Use public/supported Android Settings Intent constants whenever possible.

Examples:

```text
OPEN_SETTINGS
OPEN_DEVELOPER_OPTIONS
OPEN_WIFI_SETTINGS
OPEN_BLUETOOTH_SETTINGS
```

Do not depend on undocumented/private OEM activities.

If a specific internal Android page cannot be opened reliably:

1. Try supported direct destination.
2. Fall back to the nearest supported parent settings screen.
3. Tell the user what happened if necessary.

---

# 14. Application Resolver

Installed apps should be indexed locally.

Example:

```kotlin
data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String?,
    val normalizedLabel: String,
    val aliases: List<String>
)
```

Resolution order:

```text
1. Exact normalized label
2. Alias
3. Prefix match
4. Substring match
5. Fuzzy match
6. Optional semantic fallback
```

The resolver should return a small ranked candidate set.

Example:

```text
Query: blanket

Blinkit          0.71
Blanket Notes    0.69
...
```

Do not send all 334+ applications to the LLM.

---

# 15. Confidence Policy

Initial configurable thresholds:

```text
>= 0.90
    execute automatically

0.70 - 0.89
    confirmation

0.40 - 0.69
    additional resolution / candidate selection

< 0.40
    no match
```

These are starting values, not permanent constants.

They must be tuned using real user data and tests.

---

# 16. Nano LLM Role

Nano is a **semantic fallback/router**.

It should be used when:

- The request is ambiguous
- The wording is complex
- Deterministic matching fails
- A natural-language explanation needs interpretation
- A small amount of retrieved context needs semantic reasoning

Example:

```text
User:
Take me to the Android settings where I can enable USB debugging.
```

Retrieve:

```text
OPEN_DEVELOPER_OPTIONS
OPEN_WIFI_SETTINGS
OPEN_BLUETOOTH_SETTINGS
...
```

Send only relevant actions to Nano.

Expected result:

```json
{
  "action": "OPEN_DEVELOPER_OPTIONS",
  "confidence": 0.96,
  "parameters": {}
}
```

---

# 17. LLM Output Validation

Every LLM response must be validated.

Rules:

- Action must exist in the Action Registry.
- Parameters must match the action schema.
- Package names must come from the App Resolver.
- Arbitrary intents are forbidden.
- Arbitrary component names are forbidden.
- Invalid JSON must not execute anything.
- Low-confidence results should fall back safely.

---

# 18. Structured Action Model

Use a constrained action model.

Example:

```kotlin
sealed interface AssistantAction {

    data class OpenApp(
        val packageName: String,
        val activityName: String? = null
    ) : AssistantAction

    data class OpenSystemSetting(
        val actionId: String
    ) : AssistantAction

    data class OpenDeepLink(
        val uri: String,
        val packageName: String? = null
    ) : AssistantAction
}
```

Execution is always deterministic Kotlin/platform code.

---

# 19. Deep Links

Deep links should be part of the architecture but not the first implementation priority.

Possible execution types:

```text
OPEN_APP
OPEN_SYSTEM_SETTING
OPEN_DEEP_LINK
```

Use deep links only when the target application provides a supported public link/API.

Do not invent or reverse-engineer undocumented internal activities as a core dependency.

---

# 20. RAG / Retrieval Architecture

Traditional vector-database RAG is not required initially.

The project primarily deals with structured local information.

Prefer:

```text
Query
 ↓
Determine data source
 ↓
Deterministic local retrieval
 ↓
Small context
 ↓
LLM
```

Data sources may eventually include:

```text
Installed Apps
System Actions
UsageStats
Food Database
Food History
Tasks
Calendar
Contacts
Personal Memory
Preferences
```

Only retrieve the relevant source.

---

# 21. Personal AI Memory

A long-term goal is a private local memory layer.

Potential memories:

```text
Food history
Usage patterns
Preferences
Frequent apps
Usual food portions
Focus habits
User-defined aliases
Tasks
Habits
```

Memory must be:

- Local
- User-controlled
- Inspectable
- Deletable
- Exportable
- Minimally collected

The LLM should not automatically receive all memory.

Memory is retrieved selectively.

---

# 22. Food / Nutrition Tracking

Food tracking is a planned feature, not a Phase 1 requirement.

Users should be able to say:

```text
I had two rotis, dal and curd.
```

The assistant converts it into structured data:

```json
{
  "meal": "lunch",
  "items": [
    {
      "food": "roti",
      "quantity": 2
    },
    {
      "food": "dal",
      "quantity": 1,
      "unit": "bowl"
    },
    {
      "food": "curd",
      "quantity": 1,
      "unit": "serving"
    }
  ]
}
```

Then a nutrition data layer provides estimates.

The LLM should NOT be treated as the authoritative calorie database.

---

# 23. Nutrition Database

Potential structure:

```text
Food
Serving
Calories
Protein
Carbohydrates
Fat
Source
Confidence
```

Indian food should be strongly supported.

Examples:

```text
Roti
Dal
Rice
Poha
Upma
Idli
Dosa
Samosa
Kachori
Paneer
Curd
Rajma
Chole
Sabzi
Paratha
Tea
Coffee
```

Users should eventually be able to customize serving definitions.

Example:

```text
"My roti usually uses 35g atta."
```

The system can store the user's usual portion.

---

# 24. Food Image Recognition

Potential future flow:

```text
Photo
 ↓
On-device vision capability
 ↓
Food candidates
 ↓
User confirmation
 ↓
Nutrition lookup
 ↓
Local food log
```

Example:

```text
"I see something that looks like roti, dal and aloo sabzi.
Should I log that?"
```

The user confirms.

The system estimates calories.

Important:

> Food images should produce estimates, not pretend to provide exact calorie measurements.

Portion size, oil, ghee, ingredients and preparation can significantly change calories.

Use ranges or confidence where appropriate.

---

# 25. Digital Wellbeing

A major future feature.

Use Android UsageStats where permitted.

Potential questions:

```text
What app did I use most today?

How much time did I spend on social media?

What am I wasting the most time on?

Am I using Instagram more than usual?

Start a 45-minute focus session.

Block YouTube for the next hour.
```

The system should retrieve only relevant aggregates.

Example:

```text
Instagram: 102 min
YouTube: 81 min
Chrome: 43 min
```

The LLM receives these small aggregates only when interpretation is required.

---

# 26. Personal Nudges

Nudges should be:

- Optional
- Gentle
- Factual
- Based primarily on the user's own baseline
- User-configurable

Avoid judgmental messages.

Bad:

> "You're wasting your life on Instagram."

Better:

> "You've used Instagram for 1h 47m today, compared with your 7-day average of 54m."

Then:

> "Would you like to start a focus session?"

---

# 27. Long-Term Product Model

The project should not become a collection of unrelated AI features.

The coherent product is:

```text
                 PERSONAL AI
                      │
       ┌──────────────┼──────────────┐
       ↓              ↓              ↓
     DEVICE         WELLBEING     PERSONAL
     CONTROL                        MEMORY
       │              │              │
    Apps/settings    Usage         Food
    Deep links       Focus         Habits
    Actions          Nudges        Preferences
```

The central concept is:

> **AI as an interface to your personal device and personal information.**

---

# 28. Open Source Strategy

The project should initially be fully free and open source.

Principles:

- No ads
- No forced account
- No forced cloud AI
- No artificial AI request limits
- No basic functionality paywall
- Core functionality remains usable without payment

This is a deliberate product philosophy.

---

# 29. Funding Strategy

Funding should initially come from:

```text
Community
 ├── GitHub Sponsors
 ├── One-time donations
 └── Community contributions

Later
 ├── Corporate sponsorship
 ├── Grants
 ├── Partnerships
 └── Investment
```

Do not make donations the only business plan.

If the project achieves significant adoption, investors may fund the company around the open-source ecosystem.

The project should first prove:

```text
Open source
 ↓
Adoption
 ↓
Community
 ↓
Usage
 ↓
Traction
 ↓
Funding
```

---

# 30. Monetization Philosophy

The project does NOT need to artificially lock basic functionality.

Potential future revenue can come from:

- Sponsorship
- Grants
- Corporate sponsorship
- Optional hosted services
- Optional cloud sync
- Optional cloud AI
- Hardware partnerships
- Enterprise/integration work

If paid features are ever introduced, they should provide additional ongoing value rather than artificially limiting local AI.

---

# 31. License / Trademark

Before public launch, explicitly decide:

- Software license
- Trademark ownership
- Logo policy
- Official distribution policy
- Governance
- Contribution policy

Possible open-source licenses to evaluate:

```text
Apache-2.0
GPLv3
```

Do not choose solely based on popularity.

The license should be selected based on the project's long-term open-source and funding goals.

The official brand/trademark should remain clearly distinguishable from forks.

Legal review is recommended before the public launch.

---

# 32. Repository Structure

A possible future repository:

```text
project/
│
├── core/
│   ├── ai/
│   ├── assistant/
│   ├── resolver/
│   ├── memory/
│   ├── food/
│   ├── wellbeing/
│   └── data/
│
├── android/
│   ├── launcher/
│   ├── actions/
│   ├── installed-apps/
│   ├── usage-stats/
│   └── local-ai/
│
├── ios/
│   ├── assistant/
│   ├── app-intents/
│   ├── shortcuts/
│   ├── widgets/
│   └── local-ai/
│
├── docs/
│
└── README.md
```

This is illustrative, not mandatory.

The existing project structure should be preserved where reasonable.

---

# 33. Current Known Bugs

## Bug 1 — App ambiguity

Current example:

```text
voice heard='open blanket' apps=334

fast-route:
OpenApp(
    label=Blinkit,
    packageName=com.grofers.customerapp,
    confident=false
)
```

Expected:

```text
Blinkit is a candidate
→ ask confirmation
→ preserve pending action
```

Then:

```text
User: yes
→ execute pending Blinkit action
```

No LLM call should be necessary.

---

## Bug 2 — Confirmation is not preserved

Current:

```text
voice heard='yes'
```

must be interpreted relative to the pending action.

The confirmation handler must execute before normal routing.

---

## Bug 3 — Repeated command prefix

Current:

```text
open open developer options
```

should become:

```text
developer options
```

and resolve through the system action registry.

It should not be treated as an application name.

---

# 34. Implementation Roadmap

## Phase 0 — Inspect Existing Code

Before modifying the project:

1. Inspect the existing architecture.
2. Identify Ask/voice flow.
3. Identify fast-route implementation.
4. Identify AppEntry/AppResolver.
5. Identify current action execution.
6. Identify current local LLM integration.
7. Identify current UI state handling.
8. Identify current logging.

Do not rewrite unrelated code.

---

## Phase 1 — Fix Core Routing

Implement:

- Text normalization
- Pending confirmation state
- Yes/no handling
- Action Registry
- Developer Options
- App Resolver centralization
- Confidence scoring
- Structured diagnostics

Acceptance:

```text
open blanket
→ Blinkit candidate
→ confirmation
→ yes
→ Blinkit opens
```

and:

```text
open open developer options
→ Developer Options opens
```

---

## Phase 2 — Deterministic System Actions

Add:

```text
Camera
Settings
Developer Options
Wi-Fi
Bluetooth
Display
Sound
Notifications
Location
Accessibility
```

Verify Android version compatibility.

---

## Phase 3 — Nano Fallback

Only after Phase 1/2 are stable:

```text
Deterministic routing
        ↓
If unresolved
        ↓
Targeted retrieval
        ↓
Nano
        ↓
JSON
        ↓
Validation
        ↓
Execution
```

Measure:

- LLM invocation rate
- Latency
- Failure rate
- False-positive rate
- Confirmation rate

---

## Phase 4 — Testing / Hardening

Create unit tests for:

- Normalization
- Alias matching
- Fuzzy matching
- Confidence thresholds
- Confirmation
- Action validation
- JSON parsing
- LLM fallback
- Android action execution

Add integration tests for the full voice flow.

---

## Phase 5 — Digital Wellbeing

Implement:

```text
UsageStats
 ↓
Local aggregation
 ↓
Usage summaries
 ↓
Optional AI interpretation
 ↓
Nudges
 ↓
Focus
```

---

## Phase 6 — Personal Memory

Implement:

```text
Food
Tasks
Habits
Preferences
Personal records
```

Use a local database.

---

## Phase 7 — Food / Nutrition

Implement:

- Text logging
- Voice logging
- Nutrition database
- User serving preferences
- Daily totals
- Meal history
- Nutrition summaries

---

## Phase 8 — Vision

Add image-based food recognition if a suitable on-device vision capability is available.

Never make image calorie estimates appear more precise than they are.

---

## Phase 9 — KMP Extraction / iOS

Once the shared architecture is stable:

1. Move stable domain logic to KMP.
2. Keep Android-specific launcher functionality in Android source.
3. Create iOS companion experience.
4. Add App Intents/Shortcuts/widgets where useful.
5. Share AI, memory, food, resolver and data models.

Do not delay Android launch waiting for iOS parity.

---

# 35. Testing Philosophy

Every new capability should have:

```text
Input
 ↓
Expected normalized representation
 ↓
Expected route
 ↓
Expected retrieved context
 ↓
Expected structured action
 ↓
Expected execution
```

Example:

```text
Input:
open blanket

Expected:
route = OPEN_APP
candidate = Blinkit
confidence < auto threshold
decision = CONFIRM

Then:
input = yes

Expected:
execute = Blinkit
LLM calls = 0
```

---

# 36. Observability

Logs should describe each stage.

Example:

```text
assistant.input="open blanket"
assistant.normalized="blanket"
assistant.route=OPEN_APP
resolver.query="blanket"
resolver.candidates=[Blinkit:0.71]
resolver.decision=CONFIRM
pending.action=OPEN_APP(Blinkit)
```

For LLM:

```text
llm.invoked=true
llm.reason=SEMANTIC_FALLBACK
llm.contextItems=4
llm.action=OPEN_DEVELOPER_OPTIONS
llm.confidence=0.96
validation=PASS
execution=SUCCESS
```

Do not unnecessarily log sensitive user/device information.

---

# 37. Performance Requirements

Core deterministic actions should normally require:

```text
0 LLM calls
```

Confirmation should require:

```text
0 LLM calls
```

App resolution should happen locally.

App index should be cached and updated when packages change rather than rebuilt unnecessarily for every command.

The UI must remain responsive.

Slow operations must not block the main thread.

---

# 38. Privacy Requirements

The project should follow:

> **Collect as little as possible. Process locally whenever possible.**

Personal data should be:

- Local
- Encrypted where appropriate
- User-deletable
- User-exportable
- User-inspectable

Future cloud features must be explicit and opt-in.

---

# 39. Product UX Rules

1. The launcher should feel minimal.
2. Ask should be extremely easy to invoke.
3. Responses should be concise.
4. Obvious commands should execute immediately.
5. Ambiguous commands should ask once.
6. Never overwhelm users with AI explanations.
7. Never nag users unnecessarily.
8. AI should reduce screen interaction.
9. Privacy should be a product feature, not merely a technical implementation detail.
10. Every new feature must justify its complexity by reducing friction or improving user control.

---

# 40. What NOT to Build Yet

Do not currently build:

- Full chatbot UI
- Cloud backend
- Vector database
- Multi-agent architecture
- Autonomous device control
- Huge tool registry
- Complex cloud synchronization
- iOS parity
- Advanced food vision
- Large-scale proactive notification system

First make the core loop excellent.

---

# 41. Definition of the MVP

The first public MVP should be:

```text
Minimal Android launcher
+
Ask interface
+
Offline/local AI
+
App resolver
+
Natural language app opening
+
Confirmation handling
+
System settings actions
+
Developer Options
+
Safe deterministic execution
```

It should be reliable before it becomes feature-rich.

---

# 42. Definition of Product-Market Expansion

After the launcher is stable:

```text
Launcher
 ↓
AI device control
 ↓
Digital wellbeing
 ↓
Personal memory
 ↓
Food tracking
 ↓
Vision
 ↓
Cross-platform personal AI
```

The features should feel like parts of one product.

---

# 43. Long-Term Product Statement

The most useful internal description of the project is:

> **We are building an open-source personal AI layer for mobile devices. Android starts as the flagship launcher because Android gives us deep device-level control. iOS becomes a companion experience. The AI is local-first, retrieval-first, privacy-first, and constrained to deterministic tools. Over time it can understand the user's apps, device behavior, wellbeing, food, habits and preferences without requiring those data to leave the device.**

---

# 44. Instructions for Claude / AI Coding Agents

This document is the project's source of truth.

When modifying the project:

1. Inspect the existing code before changing architecture.
2. Preserve working code.
3. Prefer incremental changes.
4. Do not rewrite the application unless explicitly required.
5. Implement one roadmap phase at a time.
6. Explain which existing files/classes map to each requirement.
7. Keep business/domain logic testable.
8. Keep platform-specific code behind interfaces where practical.
9. Do not send complete device datasets to the LLM.
10. Retrieve minimal context first.
11. Do not let the LLM execute arbitrary operations.
12. Validate all structured LLM output.
13. Add tests with each significant change.
14. Keep logs structured and privacy-conscious.
15. Do not introduce heavyweight infrastructure without evidence that it is necessary.
16. Do not prematurely optimize for iOS.
17. Do not prematurely build food/vision/wellbeing features before the core assistant is reliable.

---

# 45. Immediate Task for the Existing Project

The next implementation cycle should ONLY focus on:

```text
1. Inspect current code.
2. Map current flow to this Source of Truth.
3. Implement normalization.
4. Implement PendingAction.
5. Fix confirmation handling.
6. Implement Action Registry.
7. Implement OPEN_DEVELOPER_OPTIONS.
8. Improve App Resolver.
9. Add confidence thresholds.
10. Fix:
       "open blanket"
       → Blinkit
       → confirmation
       → "yes"
       → launch
11. Fix:
       "open open developer options"
       → Developer Options
12. Add tests.
13. Add structured diagnostics.
14. Stop and verify before moving to Nano fallback.
```

Do not implement the entire roadmap in one pass.

---

# 46. Success Criteria

The project is successful when the user can pick up the phone and naturally say:

```text
Open Spotify.

Open the camera.

Open developer options.

Open blanket.
```

and the system reliably understands them without internet.

Later:

```text
How much time did I spend on Instagram?

What did I eat today?

Log two rotis and dal.

Start a focus session.
```

The long-term success condition is:

> **The user stops thinking about which application or setting to open and simply tells the phone what they want.**

---

# 47. Final Architectural Rule

When in doubt, follow this order:

```text
DETERMINISTIC
    ↓
LOCAL RETRIEVAL
    ↓
LOCAL LLM
    ↓
STRICT VALIDATION
    ↓
DETERMINISTIC EXECUTION
```

Never reverse the order merely because an LLM can technically do something.

The project should use AI where AI adds value, and ordinary software everywhere ordinary software is better.
