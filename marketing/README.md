# Marketing assets

Play Store listing assets, social banners, and the copy that goes with them.
Everything in `out/` is generated from real app screenshots — no mockups.
Google Play only; there is no iOS app, so no App Store sizes.

## Layout

```
copy/        Listing text + captions: play-store.md
templates/   HTML templates the assets are rendered from
  foyer.css            shared design language, lifted from website/style.css
  ui-home.js           the rebuilt home screen fragment (same markup as the site hero)
  panel-home.html      full home screen, rebuilt in HTML (?theme=dark|light)
  panel-command.html   "you say / it does" — the assistant as a component
  panel-drawer.html    a piece of the drawer: search match + a–z rail
  panel-clocks.html    the four clock faces side by side (HTML/SVG, not crops)
  panel-appearance.html  accent dots, font rows, background tones
  panel.html           screenshot-in-a-frame fallback (?img=shot-*.png) for
                       screens better shown whole
  feature-graphic.html, social-banner.html, github-preview.html
out/
  play/      icon-512.png, feature-graphic.png (1024×500) + six 1080×1920 panels
  social/    banner-1200x630.png (OG/post image), github-preview-1280x640.png (+@2x masters)
```

Like the website, the panels don't frame screenshots — the app UI in them is
rebuilt in HTML/SVG from the same design tokens ("not a screenshot" applies to
the store listing too). Each panel shows the one component it's about: the four
clock faces, the drawer rail, the command flow — not six full screens.
`panel.html` still takes any `website/assets/shot-*.png` for cases where the
real full screen is the point.

The six panels, in listing order: home dark, you-say-it-does, app drawer,
four clocks, appearance, home light. Captions are lowercase on purpose — they
match the app's and website's voice.

`generate.mjs` also mirrors the finished Play assets into
`fastlane/metadata/android/en-US/images/` (phoneScreenshots/, featureGraphic.png,
icon.png), and the listing text lives beside them as title.txt,
short_description.txt, full_description.txt — so `fastlane supply` can push the
whole listing, or you can drag the same files into the Play Console.

Two more places consume these renders: `website/assets/og-image.png` is a copy
of `out/social/banner-1200x630.png` (refreshed on every run; the OG meta tags in
`website/index.html` point at it), and the repo README embeds
`out/play/feature-graphic.png` plus the six panels as its screenshot row.

## Regenerating everything

```bash
npm i --prefix marketing playwright-core   # once
node marketing/generate.mjs                # renders out/ + syncs fastlane images
```

Panel captions live in the `PANELS` array at the top of the script; the feature
graphic and banner text is edited directly in `templates/*.html`.

## Size rules worth remembering

- Play screenshots: the long side can be at most **twice** the short side, so
  device-native 1344×2992 captures can't be uploaded as-is. Panels render at
  1080×1920 (9:16), which is also what featuring/promotion asks for.
- Feature graphic: exactly 1024×500.
- Icon: full-square 512×512; Google applies its own mask.

## How the raw captures were made

`adb exec-out screencap -p` on the Pixel 10 Pro (1344×2992) with the demo
launcher state, saved to `website/assets/` as `shot-<screen>[-dark].png`.
New screens only need a capture dropped there plus a `PANELS` entry.

## Still worth doing later

- Upload `out/social/github-preview-1280x640.png` as the GitHub repo's social
  preview (Settings → General → Social preview) when the repo goes public.
- Tablet screenshots (7"/10") if the app ever targets tablets.
