# Google Play listing

Assets: `marketing/out/play/` — icon-512.png, feature-graphic.png (1024×500),
01–06 screenshots (1080×1920, 9:16). The same files are mirrored into
`fastlane/metadata/android/en-US/` by `generate.mjs`, so `fastlane supply`
(or a manual Console upload) picks them up as-is.

## App name (max 30 chars)

> Foyer: quiet AI launcher
<!-- 24 chars -->

## Short description (max 80 chars)

> A text-first launcher with a fully on-device AI assistant. No account, no ads.
<!-- 78 chars -->

## Full description (max 4000 chars)

> **A quiet home for your phone.**
>
> Foyer replaces your home screen with the things you actually use: a clock, your
> favourite apps as plain text, and nothing else. No grids, no folders, no feeds,
> no icons shouting for attention.
>
> **AI that never phones home.** Ask it to open apps, place calls, or set things in
> motion — by voice or by typing. The assistant runs on a small language model that
> lives entirely on your phone. No server, no account, no analytics: airplane mode
> changes nothing.
>
> **Bring your own model.** Pick a small model in the app or load one you trust.
> Your phone, your weights.
>
> **Every app, one flick away.** The drawer is a plain text list with an a–z
> fast-scroll rail built for your thumb. Search is one tap away when you need it.
>
> **Clocks with character.** Digital, bold, dial, or hands — pick the face your day
> starts with, or turn the clock off entirely.
>
> **Tune it, quietly.** Three fonts (inter, mono, serif), a handful of accents, and
> matte backgrounds in dark or light. Quiet by default, yours by choice.
>
> **Free and open source.** Foyer is GPL-3.0 with no ads, no tracking, and no
> engagement tricks. The entire app is on GitHub.
>
> Website: https://foyerlauncher.com
> Source: https://github.com/akshay2211/Foyer
<!-- ~1240 chars, well under 4000 -->

## Screenshot captions (already baked into the panels)

1. 01-home.png — a quiet home for your phone
2. 02-say.png — you say. it does.
3. 03-drawer.png — every app, one flick away
4. 04-clocks.png — four ways to tell time
5. 05-appearance.png — curated, not customised
6. 06-light.png — lights on, if you like

## Notes

- Icon: Play requires the full-square 512×512 (`icon-512.png`, from
  `art/ic_launcher_512.png`); Google applies its own mask.
- Feature graphic: 1024×500, shown behind the play button on the listing.
- Screenshot rule: Play rejects images whose long side is more than twice the
  short side — that's why panels are 1080×1920, not device-native 1344×2992.
- The listing text above is duplicated in `fastlane/metadata/android/en-US/`
  (title.txt, short_description.txt, full_description.txt) — keep both in sync.
