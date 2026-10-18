# Copilot instructions for mediatheQ

mediatheQ is an Android client for self-hosted media servers (Jellyfin 12+ first), built with Kotlin, Jetpack Compose and Material 3 Expressive.

The full rules live in the project skills and ADRs. Read the ones that apply before writing or reviewing code:

- Architecture (modules, layers, dependency rules, errors, DI): [`.claude/skills/architecture/SKILL.md`](../.claude/skills/architecture/SKILL.md)
- Testing (what every change must test, tools, screenshot tests): [`.claude/skills/testing/SKILL.md`](../.claude/skills/testing/SKILL.md)
- UI and design (Material 3 Expressive): [`.claude/skills/expressive-design/SKILL.md`](../.claude/skills/expressive-design/SKILL.md)
- Jellyfin API: [`.claude/skills/jellyfin/SKILL.md`](../.claude/skills/jellyfin/SKILL.md)
- Playback (Media3): [`.claude/skills/media3/SKILL.md`](../.claude/skills/media3/SKILL.md)
- Decisions: [`docs/decisions/`](../docs/decisions/README.md); your role is in [ADR 0011](../docs/decisions/0011-github-copilot-role.md)

## Key rules

- Code, comments, commits, issues and PRs in English. Commit messages: `#<issue>: <what>`.
- Versions only in `gradle/libs.versions.toml`; never guess a version. AGP stable only, JDK 21, KSP (no KAPT).
- Module dependencies follow the architecture skill, section 2. No `:feature:*` -> `:feature:*`, no feature -> provider/database/network. `:core:model` and `:core:common` are pure Kotlin.
- One-shot calls return `MqResult<T>`; errors are the closed `MqError` set. Never swallow exceptions, never catch `CancellationException`.
- Screens: `XViewModel` + `XUiState` + stateless `XScreen` + `XEntry`. Features use `Mq*` components from `:core:designsystem`, not raw material3 tokens.
- Every change ships with tests: fakes over mocks (MockK only if unavoidable, never Mockito), Truth, Turbine, `runTest`. Bug fix = failing regression test first. New UI = `@PreviewTest` screenshot tests with committed references.
- No Firebase, analytics, RxJava, LiveData, Gson, Moshi, KAPT, Room 2.x. Never log tokens.
- Before finishing: `./gradlew spotlessApply` then `./gradlew assembleDebug testDebugUnitTest lint`.

## Coding agent

Only work on issues labeled `copilot`. Stay inside the modules the issue names, add no new modules, module dependencies or UI, and stop with a comment if the issue is not fully specified.

## Code review

Review against the rules above and the linked skills. Prefer real defects (bugs, rule violations, missing tests) over style; formatting is enforced by Spotless.
