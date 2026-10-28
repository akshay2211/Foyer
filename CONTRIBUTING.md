# Contributing to Foyer

Thanks for your interest! Foyer is small on purpose — the bar for merging is
that a change fits the product and the codebase, not just that it works.

## Before you start

- Read [docs/DESIGN_GUIDE.md](docs/DESIGN_GUIDE.md). It defines the product
  principles (text-first, on-device only, bring-your-own-model) and the
  architecture, including the deliberate choices — e.g. plain snapshot-state
  holders instead of ViewModel/Hilt/Room for boot-critical state. PRs that
  "modernize" against those documented decisions will be declined; PRs that
  argue a decision should change are welcome as issues first.
- For anything bigger than a bugfix, open an issue and get a nod before
  writing code.

## Ground rules

- **Privacy is non-negotiable.** No network calls (beyond the user-initiated
  model download), no analytics, no new permissions without a point-of-use
  explanation screen.
- **No new dependencies** without prior discussion — the app is deliberately
  lean. Home/drawer surfaces are foundation-only Compose; Material 3 is used
  on settings pages only, themed via `FoyerMaterialTheme`.
- **AI decides, code executes.** Don't add hardcoded regex/keyword rules to
  the semantic path; teach the deterministic router (`ai/router`) or improve
  the prompt instead.
- **Match the existing style** — lowercase UI copy, KDoc on public APIs,
  comments explain *why*, not *what*. Formatting is enforced by ktlint
  (`./gradlew ktlintFormat` fixes most things).
- **Tests:** changes to `ai/router`, `Normalizer`, or persistence codecs must
  come with unit tests. `./gradlew ktlintCheck testDebugUnitTest lintDebug`
  must pass (CI enforces all three).

## Building

JDK 17+, Android Studio current stable. `./gradlew assembleDebug` builds;
see the README for trying the assistant with a local model.

## Commits and pull requests

- Small, focused commits with imperative subject lines
  ("Add per-app limit picker", not "added stuff").
- Describe *why* in the PR body; screenshots for UI changes.

## Developer Certificate of Origin (DCO)

Every commit must be signed off:

```sh
git commit -s
```

The `Signed-off-by:` line certifies the [DCO 1.1](https://developercertificate.org/)
— that you have the right to submit the work under the project's license.

## License of contributions

Foyer is licensed under **GPL-3.0**. By submitting a contribution you agree
that it is provided under GPL-3.0, **and** you additionally grant the project
maintainer (Akshay Sharma) a perpetual, worldwide, non-exclusive, royalty-free
license to relicense your contribution as part of Foyer under other license
terms. This keeps future licensing options (e.g. a separately licensed build)
open without a separate CLA process. If you can't agree to this grant, please
say so in the PR instead of signing off.
