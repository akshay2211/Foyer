# the assistant

Foyer's core rule: **AI decides, code executes.** The model never authors an
Android Intent — it picks an action id from a typed capability catalog and
fills its slots; Kotlin validates and executes. A hallucinated reply can at
worst fail to build, never fire something arbitrary.

## The two-tier pipeline

```
"open spotfy"
     │
     ▼
Normalizer           cheap text cleanup (whitespace, repeated verbs)
     │
     ▼
FastRouter           tier 0 — deterministic. Exact/fuzzy app resolution
     │               (AppIndex), settings phrases (ActionRegistry),
     │               inventory questions (AppCatalog). No model, instant.
     ▼ (no match)
LlmEngine            tier 1 — the user's own MediaPipe .task model
     │               (Qwen, Gemma, …). Prompt is generated from the
     │               capability catalog and grounded with installed apps.
     ▼
parsePlan            tolerant JSON extraction → validated AiIntents
     │
     ▼
Plan.toCommand       builds real Intents, validates against the device,
                     asks for confirmation on low-confidence steps
```

If no model is installed or the model fails, the assistant degrades to a web
search — the pipeline never breaks.

## Bring your own model

Foyer ships no model weights and hosts no downloads. The user pastes a direct
link to any MediaPipe `.task` file (one-tap suggestions are provided for
ungated Qwen 2.5 builds and the gated Gemma 3 1B). The download is
user-initiated, streamed to app storage with a storage pre-check, and
self-tested before setup is called done.

## Conversation

`Assistant.converse` keeps a short rolling history, can ask "did you mean…?"
with candidate buttons, answers app-inventory questions locally, and treats a
stale confirmation as a fresh request after a timeout.

## Debugging

All AI decisions are logged under the `FoyerAI` tag:

```sh
adb logcat -s FoyerAI:V
```

Every turn logs which route handled it (`DETERMINISTIC` vs
`SEMANTIC_FALLBACK`), the raw model reply, and the parsed plan. Limit-nudge
decisions log under `FoyerLimits`.
