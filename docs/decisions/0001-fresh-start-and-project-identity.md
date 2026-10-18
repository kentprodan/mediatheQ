# 0001. Fresh start and project identity

- Status: Accepted
- Date: 2026-10-18

## Context

The first Android build (~11k lines Kotlin) had well-tested non-UI layers but a weak UI layer: ~4.7k lines, almost no tests, Material 3 Expressive only partly applied. Its GitHub repository was deleted. The code still exists on the Mac (`~/Documents/mediatheQ`).

## Decision

- Start a new repository from zero. The old code is **reference only**: read it for lessons, never copy it.
- Lessons from the old builds are carried forward as written rules (project skills) and regression tests, not as code.
- applicationId and base package: `io.github.kentprodan.mediatheq`.
- License: **GPL-3.0-or-later**. This allows reusing code from GPL-3.0 projects (Findroid, Gramophone, Auxio, ...) with attribution; anything copied keeps its license header and is listed in `THIRD_PARTY.md`.
- Language: code, comments, commits, issues, ADRs and docs in English.

## Consequences

- More initial work than keeping the old core, but every layer is built against the new rules (architecture, testing, design) from day one.
- `LICENSE` (GPL-3.0 text) is added in the first commit with code.
- Code from Apache/MIT projects may also be reused (compatible with GPL-3.0); code from proprietary apps may not.
