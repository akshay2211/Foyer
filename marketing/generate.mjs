#!/usr/bin/env node
// Regenerates every Play / social asset in marketing/out/ from the HTML templates
// in marketing/templates/ and the raw captures in website/assets/ (shot-*.png).
//
// One-time setup:   npm i --prefix marketing playwright-core
// Run:              node marketing/generate.mjs
//
// Panels render at 540×960 @2x = 1080×1920 (9:16) — inside Google Play's hard
// limit (long side at most twice the short side). Finished Play assets are also
// copied into fastlane/metadata/android/en-US/images/ for `fastlane supply`.
//
// Capturing fresh app screenshots is a separate, interactive step — see marketing/README.md.

import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, copyFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { homedir } from 'node:os';

const ROOT = dirname(fileURLToPath(import.meta.url));           // marketing/
const REPO = resolve(ROOT, '..');
const T = join(ROOT, 'templates');
const OUT = join(ROOT, 'out');
const FASTLANE = join(REPO, 'fastlane', 'metadata', 'android', 'en-US', 'images');

const require = createRequire(join(ROOT, 'node_modules', 'playwright-core', 'package.json'));
let chromium;
try { ({ chromium } = require('playwright-core')); }
catch { console.error('playwright-core missing — run: npm i --prefix marketing playwright-core'); process.exit(1); }

function findChromium() {
  if (process.env.CHROME_PATH && existsSync(process.env.CHROME_PATH)) return process.env.CHROME_PATH;
  const cache = join(homedir(), 'Library/Caches/ms-playwright');
  if (existsSync(cache)) {
    for (const dir of readdirSync(cache).filter(d => d.startsWith('chromium-')).sort().reverse()) {
      for (const sub of ['chrome-mac-arm64', 'chrome-mac']) {
        for (const app of ['Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing',
                           'Chromium.app/Contents/MacOS/Chromium']) {
          const p = join(cache, dir, sub, app);
          if (existsSync(p)) return p;
        }
      }
    }
  }
  const sys = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
  if (existsSync(sys)) return sys;
  throw new Error('No Chromium found. Set CHROME_PATH or run: npx playwright install chromium');
}

// ---------- what gets rendered ----------

// The Play screenshot panels, in listing order. Captions are baked into the
// images; edit here and re-run. Each panel names its template — the app UI in
// them is rebuilt in HTML (templates/foyer.css), not screenshotted; the
// screenshot-in-a-frame template (panel.html, img=shot-*.png) is still there
// for screens that are better shown whole.
const PANELS = [
  { out: '01-home',       tpl: 'panel-home.html',  theme: 'dark',
    title: 'a <em>quiet</em> home for your phone', sub: 'A clock, your favourites, nothing else. No grids, no noise.' },
  { out: '02-say',        tpl: 'panel-command.html',
    title: 'you say. <em>it does.</em>',          sub: 'An assistant that runs fully on your phone. No server, no account.' },
  { out: '03-drawer',     tpl: 'panel-drawer.html',
    title: 'every app, one <em>flick</em> away',  sub: 'A text-first drawer with an a–z rail made for your thumb.' },
  { out: '04-clocks',     tpl: 'panel-clocks.html',
    title: 'four ways to <em>tell time</em>',     sub: 'Digital, bold, a rotating dial, or plain hands.' },
  { out: '05-appearance', tpl: 'panel-appearance.html',
    title: 'curated, not <em>customised</em>',    sub: 'Three fonts, four accents, five background tones. Every pairing keeps its contrast.' },
  { out: '06-light',      tpl: 'panel-home.html',  theme: 'light',
    title: '<em>lights on</em>, if you like',     sub: 'A full light theme with the same calm.' },
];

// Flat renders: @2x master, then sips down to the exact final size.
const FLATS = [
  { tpl: 'feature-graphic.html', w: 1024, h: 500, master: 'play/feature-graphic@2x.png',      final: ['play/feature-graphic.png', 1024, 500] },
  { tpl: 'social-banner.html',   w: 1200, h: 630, master: 'social/banner@2x.png',             final: ['social/banner-1200x630.png', 1200, 630] },
  { tpl: 'github-preview.html',  w: 1280, h: 640, master: 'social/github-preview@2x.png',     final: ['social/github-preview-1280x640.png', 1280, 640] },
];

// ---------- pipeline ----------

const sips = (inFile, w, h, outFile) =>
  execFileSync('sips', ['-z', String(h), String(w), inFile, '--out', outFile], { stdio: 'pipe' });

const url = (rel) => {
  const [file, query] = rel.split('?');
  return pathToFileURL(join(T, file)).href + (query ? '?' + query : '');
};

const browser = await chromium.launch({ headless: true, executablePath: findChromium() });
async function shot(pageUrl, w, h, dpr, outFile) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: dpr });
  const page = await ctx.newPage();
  await page.goto(pageUrl, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
  mkdirSync(dirname(outFile), { recursive: true });
  await page.screenshot({ path: outFile, type: 'png' });
  await ctx.close();
  console.log('  rendered', outFile.replace(REPO + '/', ''));
}

// Play panels: 1080×1920 (9:16).
for (const p of PANELS) {
  const q = new URLSearchParams({ title: p.title, sub: p.sub });
  if (p.theme) q.set('theme', p.theme);
  if (p.img) q.set('img', p.img);
  await shot(url(p.tpl || 'panel.html') + '?' + q, 540, 960, 2, join(OUT, 'play', p.out + '.png'));
}

// Feature graphic and social banners.
for (const f of FLATS) {
  const master = join(OUT, f.master);
  await shot(url(f.tpl), f.w, f.h, 2, master);
  const [rel, w, h] = f.final;
  sips(master, w, h, join(OUT, rel));
}
await browser.close();

// Play icon: the full-square 512 already lives in art/ (Google applies its own mask).
copyFileSync(join(REPO, 'art', 'ic_launcher_512.png'), join(OUT, 'play', 'icon-512.png'));
console.log('  copied  marketing/out/play/icon-512.png');

// Mirror finished Play assets into fastlane metadata for `fastlane supply`.
mkdirSync(join(FASTLANE, 'phoneScreenshots'), { recursive: true });
for (const p of PANELS) {
  copyFileSync(join(OUT, 'play', p.out + '.png'), join(FASTLANE, 'phoneScreenshots', p.out + '.png'));
}
copyFileSync(join(OUT, 'play', 'feature-graphic.png'), join(FASTLANE, 'featureGraphic.png'));
copyFileSync(join(OUT, 'play', 'icon-512.png'), join(FASTLANE, 'icon.png'));
console.log('  synced  fastlane/metadata/android/en-US/images/');

// The social banner doubles as the website's Open Graph image.
copyFileSync(join(OUT, 'social', 'banner-1200x630.png'), join(REPO, 'website', 'assets', 'og-image.png'));
console.log('  copied  website/assets/og-image.png');
console.log('done.');
