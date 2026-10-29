# privacy

Everything stays on the phone. This page is the complete list of what Foyer
does with data — it's short because there is almost nothing to say.

## Network

The **only** network call Foyer ever makes is the model download you
explicitly start, from the URL you chose. There is no server, no account, no
analytics, no telemetry, no crash reporting. The assistant works in airplane
mode.

## Permissions

Every permission is requested at the point of use, with the reason visible:

| Permission | Used for | When asked |
|---|---|---|
| Microphone | "ask me" voice input | first voice use |
| Usage access | screen time, limits, usage summary | when you open screen-time features |
| Notifications | the limit-reached nudge | when you set a daily limit |
| Contacts | resolving "message mom" to a number | first message command |
| Accessibility service | the lock-screen gesture **only** — it processes no events and reads no screen content | when you pick the lock gesture |

## What's stored, where

All state lives in local app storage: favorites, hidden apps, aliases,
limits, theme — SharedPreferences; the downloaded model file — app-private
storage. Backups are JSON files written to a location *you* pick with the
system file picker; they never leave the device by Foyer's hand.

Screen-time numbers are read live from Android's own `UsageStatsManager` and
are not copied or uploaded anywhere.
