# 0007. Compose Preview Screenshot Testing (standalone plugin)

- Status: Accepted
- Date: 2026-10-18

## Context

The old UI layer had almost no tests. Screenshot tests catch visual regressions without a device. The official Google tool is Compose Preview Screenshot Testing. Since plugin `0.0.1-alpha16` and AGP 9.5.0-alpha03, Google recommends configuring it through AGP test suites and marks the standalone plugin as deprecated; AGP 9.5 is not stable yet (see 0003).

## Decision

- Use the official tool, **standalone plugin** `com.android.compose.screenshot` (`0.0.1-alpha16` or newer).
- Not Roborazzi, not Paparazzi.
- Every design component and every screen has `@PreviewTest` screenshots; references are committed; CI (Linux) is the canonical rendering environment.
- Migrate to AGP test suites in one issue once AGP 9.5 is stable.
- Details in the `testing` skill.

## Consequences

- A planned migration later (mostly config and task names).
- Local Windows/macOS rendering may differ slightly from CI; references are regenerated on CI, never fixed by raising the threshold.
