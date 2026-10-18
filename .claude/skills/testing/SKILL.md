---
name: testing
description: Testing rules for mediatheQ. Use whenever you write or change code (every layer needs tests), add a screen or component (screenshot tests), touch Gradle test setup, fix a bug (regression test first), or prepare a PR with /end.
---

# mediatheQ - Testing

Every change ships with tests. A PR without the tests required below is not done.
Tests are the safety net for vibecoding: the owner does not read every line, so tests must prove behavior.

## 0. Toolchain (decided 2026-10-18)

- AGP: **stable** line (9.4.x as of 2026-10). No AGP alpha/beta, no Android Studio Canary.
- JDK 21 for the project (AGP minimum is 17).
- Screenshot testing: **Compose Preview Screenshot Testing, standalone plugin** (`com.android.compose.screenshot`, `0.0.1-alpha16` or newer).
  - Google marks the standalone plugin as deprecated in favor of AGP test suites (AGP 9.5+). We stay on standalone until AGP 9.5 is stable, then migrate (one issue, one ADR). Migration is mostly config + task names.
- Not used: Roborazzi, Paparazzi, Mockito.
- Versions live only in `gradle/libs.versions.toml`. Before adding a test library, check its latest stable version on Maven Central / Google Maven. Never guess a version.

## 1. Test pyramid and where tests live

| Layer | What | Source set | Runs on | Speed |
|---|---|---|---|---|
| Unit | domain logic, mappers, ViewModels, repositories with fakes | `src/test` | JVM | ms |
| Contract | Jellyfin/Subsonic HTTP behavior against recorded fixtures | `src/test` | JVM + MockWebServer | ms |
| Database | Room 3 DAOs, FTS5 search, migrations | `src/test` | JVM (bundled SQLite driver) | 100s ms |
| Playback | Media3 player logic, queue, session | `src/test` | Robolectric + media3 test utils | s |
| Compose UI behavior | clicks, state changes, semantics, motion timing | `src/test` | Robolectric | s |
| Screenshot | visual regressions of components and screens | `src/screenshotTest` | JVM (layoutlib) | s |
| Device | things only real hardware can prove | `src/androidTest` + manual `/test` | Pixel 9 / Streamer | min |

Rule: push every test as low in the pyramid as it can go. Device tests only for what JVM cannot prove.

## 2. General rules

- **Fakes over mocks.** Each interface in `:core:*` gets a `Fake*` implementation in a `testFixtures` / `:core:testing` module. Mocking libraries only when a framework class cannot be faked; then MockK, never Mockito.
- **Deterministic.** No real network, no real time, no `Thread.sleep`, no random without fixed seed. Inject `Clock` / `TimeSource` / dispatchers.
- **Coroutines:** `kotlinx-coroutines-test` with `runTest`. One shared `MainDispatcherRule` in `:core:testing`. Inject `CoroutineDispatcher`s, never hard-code `Dispatchers.IO`.
- **Flows:** Turbine (`flow.test { awaitItem() ... }`). No `toList()` on infinite flows.
- **Assertions:** Truth (`assertThat(x).isEqualTo(y)`). One behavior per test.
- **Naming:** backtick names that read as a sentence: `` `playlist add chunks requests above 200 ids`() ``.
- **Bug fix = regression test first.** Write a failing test reproducing the bug, then fix. The PR shows both.
- **No logic in tests you cannot read.** If a test needs a helper > 20 lines, move the helper to `:core:testing` and test the helper.

## 3. Unit tests (ViewModels, domain)

- ViewModels expose `StateFlow<UiState>`; test by feeding fake repositories and asserting emitted `UiState`s with Turbine.
- Cover for every ViewModel: initial/loading, success, empty, error, retry, and each user action.
- No Android framework in domain modules -> plain JVM tests, no Robolectric.

## 4. Contract tests (Jellyfin, later Subsonic/Plex)

- OkHttp `MockWebServer`. Responses come from **recorded fixtures** of a real Jellyfin 12 server, stored in `src/test/resources/jellyfin/<endpoint>/<case>.json`. Strip personal data (names, paths, tokens) before committing.
- Every test asserts both sides: the **request** we send (path, query, headers, body) and how we **parse** the response.
- Every known server behavior has a named regression test. The list (R1, R2, ...) lives in the `jellyfin` skill, section 12 - that is the single source of truth; do not duplicate it here.
- Test names reference the item: `` `R3 playlist add chunks requests above 200 ids`() ``.
- When a new server quirk is found: fixture + test + new `R<n>` line in the provider skill, same PR.

## 5. Database tests (Room 3)

- Plain JVM tests: in-memory database (`Room.inMemoryDatabaseBuilder`) with the bundled SQLite driver (`androidx.sqlite:sqlite-bundled`). Room 3 requires a driver, and the bundled one runs on the JVM, so DB tests need no Robolectric.
- Test every DAO query used by the app, including FTS5 search **without diacritics** (`"Mosoi"` finds `"Moșoi"`).
- Migrations: every schema version bump adds a migration test using the Room 3 testing artifact (check its current name and helper class in the Room 3 release notes). Exported schemas (Room 3 Gradle plugin, `schemaDirectory`) are committed in `schemas/`.

## 6. Playback tests (Media3)

- Use `androidx.media3:media3-test-utils` (+ `-robolectric`): `TestExoPlayerBuilder`, `FakeClock`, `TestPlayerRunHelper` to advance until a state.
- Test our logic around the player, not ExoPlayer itself: queue building, shuffle/repeat, next/previous rules, prefetch decisions, quality selection (Wi-Fi original vs mobile 256), Atmos preference -> track selection parameters, progress reporting cadence.
- Audio passthrough / offload / Atmos output **cannot be proven on JVM**. Unit-test the decision (which format, which flags); prove the output manually (section 10).

## 7. Compose UI behavior tests

- `createComposeRule()` on Robolectric (`testOptions.unitTests.isIncludeAndroidResources = true`).
- Test stateless screen composables with fixed `UiState` + recording lambdas. No Hilt, no real ViewModel.
- Find nodes by semantics (text, `contentDescription`, role). `testTag` only when nothing semantic exists.
- Motion: `composeTestRule.mainClock.autoAdvance = false`, then `advanceTimeBy(...)` to assert intermediate and final states (mini player -> Now Playing, nav transitions).
- Accessibility: assert every icon-only button has a `contentDescription`.

## 8. Screenshot tests (Compose Preview Screenshot Testing, standalone)

### Setup (once, via a convention plugin `mediatheq.android.screenshot` in `build-logic`)

1. Root `gradle.properties`:
   ```
   android.experimental.enableScreenshotTest=true
   android.compose.screenshot.maxHeapSize=4g
   ```
2. Module `build.gradle.kts` (applied by the convention plugin):
   ```kotlin
   plugins {
       alias(libs.plugins.screenshot)        // com.android.compose.screenshot
   }
   android {
       experimentalProperties["android.experimental.enableScreenshotTest"] = true
   }
   dependencies {
       screenshotTestImplementation(libs.screenshot.validation.api) // com.android.tools.screenshot:screenshot-validation-api
       screenshotTestImplementation(libs.androidx.ui.tooling)
   }
   ```
3. Image difference threshold: set one small global threshold per module (check the current DSL name in the plugin release notes before writing it). Never raise it to make a test pass.

### Writing them

- Location: `<module>/src/screenshotTest/kotlin/...`, file name `<Thing>ScreenshotTest.kt`.
- Each test is a preview function annotated `@PreviewTest` (`com.android.tools.screenshot.PreviewTest`) plus the shared `@MqPreviews` multipreview (light/dark, fontScale 1.0/2.0, Pixel 9 size - defined in `:core:designsystem/preview`).
- Wrap content in `MediatheqTheme`. Use deterministic fixtures from `:core:designsystem/preview` (no network images: use a local placeholder bitmap / BlurHash fixture).
- Required coverage:
  - every `Mq*` component: all its visual states (enabled/disabled, selected, loading, error)
  - every screen: loading, content, empty, error (offline where relevant)
- Renaming a `@PreviewTest` function breaks its reference image -> regenerate in the same PR.

### Running (Windows, from the project folder)

- Generate / update references: `.\gradlew.bat :<module>:updateDebugScreenshotTest`
- Validate: `.\gradlew.bat :<module>:validateDebugScreenshotTest` (or without module prefix for all)
- References: `<module>/src/screenshotTestDebug/reference/` - **committed to git**.
- Report: `<module>/build/reports/screenshotTest/preview/debug/index.html`
- Android Studio (stable) gutter icons can also run/update; Gradle tasks are the source of truth.

### Reference image discipline

- References change **only** together with an intended UI change, in the same PR, and the PR description lists which images changed and why.
- Canonical environment for references: **CI (GitHub Actions, Linux)**. Rendering can differ slightly between Windows, macOS and Linux. If local Windows references fail on CI, regenerate them on CI (workflow artifact) instead of raising the threshold.
- Never "update all" blindly to make red go green. Open the HTML report first.

## 9. Device tests (`src/androidTest`)

- Only for: system integration (MediaSession/notification, audio focus, Android Auto browse tree via `MediaBrowser`), real decoder capability queries, and smoke test of app start.
- Run on the Pixel 9 via adb over Wi-Fi/Tailscale: `.\gradlew.bat :app:connectedDebugAndroidTest`.
- Keep the suite small (< 5 min). Use the test orchestrator.

## 10. Manual tests (`/test`)

What automation cannot prove gets a manual checklist item, queued by `/end` and executed with `/test` on real hardware:

- Audio: original quality on Wi-Fi, 256 kbps on mobile data, gapless, offload active (battery), USB DAC bit-perfect.
- Atmos chain (phase TV): Streamer -> TV -> eARC -> LG DSN11RG shows Atmos for E-AC-3 JOC music.
- Motion and haptics feel on Pixel 9.
- Android Auto (later phase): browse, play, voice.

Each manual item: device, steps, expected result, pass/fail, date. Results go in the issue as a comment.

## 11. CI

- GitHub Actions on every PR: `assembleDebug`, `testDebugUnitTest`, `validateDebugScreenshotTest`, `lint`.
- On screenshot failure, upload the HTML report as an artifact.
- Red CI = no merge. Flaky test = bug: fix or delete within the same week, never retry-until-green.

## 12. Done checklist (run in `/end`)

- [ ] New/changed logic has unit tests (all branches of `UiState`).
- [ ] New/changed network call has a contract test with a fixture.
- [ ] New/changed DAO query or schema has DB tests / migration test.
- [ ] New/changed component or screen has `@PreviewTest` + references committed.
- [ ] Bug fixes include a regression test that failed before the fix.
- [ ] `testDebugUnitTest` and `validateDebugScreenshotTest` pass locally.
- [ ] Manual checklist items queued for anything only hardware can prove.
