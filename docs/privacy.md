# Privacy Policy

Foyer is developed by Redigit. Everything stays on the phone. This page is
the complete list of what Foyer does with data — it's short because there is
almost nothing to say.

*Effective date: November 7, 2026.*

## Network

The **only** network call Foyer ever makes is the model download you
explicitly start, from the URL you chose. There is no server, no account, no
analytics, no telemetry, no crash reporting. The assistant works in airplane
mode.

Foyer collects no personal data and shares none — there is nowhere for it to
go.

## Permissions

Every permission is requested at the point of use, with the reason visible:

| Permission | Used for | When asked |
|---|---|---|
| Microphone | "ask me" voice input | first voice use |
| Usage access | screen time, limits, usage summary | when you open screen-time features |
| Notifications | the limit-reached nudge | when you set a daily limit |
| Contacts | resolving "message mom" to a number | first message command |
| Set alarm | "set an alarm / timer" commands, handed to the system clock app | never — a harmless install-time grant, Android shows no prompt |

Voice audio and contact lookups are processed on the device and are never
transmitted, stored, or shared.

## What's stored, where

All state lives in local app storage: favorites, hidden apps, aliases,
limits, theme — SharedPreferences; the downloaded model file — app-private
storage. Backups are JSON files written to a location *you* pick with the
system file picker; they never leave the device by Foyer's hand.

Screen-time numbers are read live from Android's own `UsageStatsManager` and
are not copied or uploaded anywhere.

## Deleting your data

Uninstalling Foyer removes everything, because nothing exists off-device.
The only exception is a backup file you exported yourself — delete it like
any other file.

## Changes and contact

If this policy ever changes, the new version appears here and in the app,
with an updated effective date. Questions about privacy or this policy:
[akshay@redigit.io](mailto:akshay@redigit.io).