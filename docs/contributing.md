# contributing

The full ground rules live in
[CONTRIBUTING.md](https://github.com/akshay2211/Foyer/blob/main/CONTRIBUTING.md)
in the repository root. The short version:

- Read the [design guide](DESIGN_GUIDE.md) first — PRs that "modernize"
  against its documented decisions will be declined.
- **Privacy is non-negotiable**: no network calls, no analytics, no new
  permissions without a point-of-use explanation.
- No new dependencies without prior discussion.
- Formatting is enforced by ktlint (`./gradlew ktlintFormat`).
- Changes to `ai/router`, `Normalizer`, or persistence codecs need unit
  tests; CI runs `ktlintCheck`, unit tests, Android Lint, and the R8
  release build on every PR.
- Every commit needs a DCO sign-off: `git commit -s`.
