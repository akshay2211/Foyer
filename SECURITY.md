# Security Policy

Foyer's entire privacy pitch is "everything stays on the phone" — see the
[Privacy](README.md#privacy) section of the README. A vulnerability that
breaks that promise (data leaving the device, a permission doing more than
its point-of-use explanation says, a way to execute something the user
didn't ask for) is treated as high priority.

## Reporting a vulnerability

Please use GitHub's
[private vulnerability reporting](https://github.com/akshay2211/Foyer/security/advisories/new)
(Security tab → "Report a vulnerability") rather than a public issue. That
opens a private advisory only the maintainer can see until a fix is ready.

Include what you'd include in any good bug report: the affected version or
commit, Android version/device, steps to reproduce, and what you'd expect to
happen instead.

There's no bug bounty — this is a single-maintainer open-source project —
but reporters are credited in the advisory and release notes unless they
ask not to be.

## What's in scope

- The app itself: `app/src/main` (launcher, settings, AI pipeline, storage).
- The build and release pipeline (`.github/workflows`).
- `foyerlauncher.com` (static site, no backend, no accounts, no cookies).

## What's out of scope

- The behavior or output of a model you supplied yourself under "bring your
  own model." Foyer ships no model weights and can't vet third-party ones —
  see [CONTRIBUTING.md](CONTRIBUTING.md) for what the deterministic router
  vs. the model is each responsible for.
- Vulnerabilities that require a rooted device, a malicious accessibility
  service already granted by the user, or physical access with the device
  unlocked.

## Response

This is maintained by one person in spare time — there's no SLA, but
security reports get priority over everything else in the backlog. Expect an
acknowledgment within a few days.
