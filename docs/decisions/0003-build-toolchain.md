# 0003. Build toolchain: stable AGP, JDK 21, convention plugins

- Status: Accepted
- Date: 2026-10-18

## Context

The owner does not debug build systems. Preview toolchains (AGP alpha/beta, Android Studio Canary) cause failures unrelated to app code.

## Decision

- Android Gradle Plugin: latest **stable** line (9.4.x on 2026-10-18; requires Gradle 9.6+). No previews.
- JDK 21 for the project.
- Kotlin with the Compose compiler plugin; KSP for code generation (no KAPT).
- Versions only in `gradle/libs.versions.toml`.
- Shared build configuration in convention plugins (`build-logic` included build), one per module type (`mediatheq.android.library`, `mediatheq.android.feature`, `mediatheq.android.screenshot`, ...).
- Develop on Windows (PC) and macOS (MacBook Air M1); CI on GitHub Actions (Linux) is the reference environment.

## Consequences

- Upgrades happen in dedicated issues, one toolchain bump at a time.
- Some new tools (e.g. screenshot test suites, see 0007) arrive later than on preview toolchains.
