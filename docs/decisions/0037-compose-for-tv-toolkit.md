# 0037. Compose for TV: tv-material 1.1.0, Compose Foundation lists, own TV design system

- Status: Proposed
- Date: 2026-11-11

## Context

TV 1 · Music + Atmos (ADR 0025) adds a TV app for the Google TV Streamer, driven with a D-pad from ten feet. The phone
app's UI is Material 3 Expressive (`material3` 1.5.x, ADR 0004) in `:core:designsystem` and `:core:ui`. Google's
Compose for TV guide says the TV material components exist "instead of" `androidx.compose.material3:material3` and
that mixing the two libraries' `MaterialTheme` objects gives inconsistent colors, typography and shapes
(https://developer.android.com/training/tv/playback/compose, read 2026-11-11).

As of 2026-11-11 (https://developer.android.com/jetpack/androidx/releases/tv):
- `androidx.tv:tv-material:1.1.0` is stable (2026-05-06). Stable since 1.0: `MaterialTheme`, `ColorScheme`,
  `Typography`, `Shapes`, `Surface` with `Border` / `Glow` / scale, `Button`s, `Card`s, `ListItem`,
  `NavigationDrawer`, `TabRow`, `Switch`, `Checkbox`, `RadioButton`, `Icon`, `Text`, `Carousel`.
- `androidx.tv:tv-foundation:1.0.0` is stable (2026-05-06) but its lazy layouts were deprecated in 1.0.0-alpha11 and
  removed in alpha12; Google's lists guide uses Compose Foundation `LazyRow` / `LazyColumn` / `LazyVerticalGrid`
  (1.7.0+) with `LocalBringIntoViewSpec` for a pivot
  (https://developer.android.com/training/tv/playback/compose/lists).
- The guide's setup uses `compose-bom:2026.09.00` and `activity-compose:1.13.0`, which the project already pins.

## Decision

- TV UI is built with **`androidx.tv:tv-material:1.1.0`** and Compose Foundation lazy layouts. `tv-foundation` is not
  added. Versions live in `libs.versions.toml` (`tvMaterial`).
- A **separate TV design system**, `:core:designsystem-tv`: `MediatheqTvTheme` (a tv-material `ColorScheme` generated
  from the same Iris seed with material-color-utilities, dark by default; the same downloadable fonts, ADR 0026),
  `MqTv*` components (one file each), `@MqTvPreviews` and deterministic fixtures. Domain-aware TV composables live in
  `:core:ui-tv`. Neither depends on `:core:designsystem` or `:core:ui`, and no TV module imports
  `androidx.compose.material3`.
- Focus is the design's first concern: every interactive `MqTv*` component shows tv-material's focus border, glow or
  scale; containers use `focusRestorer()`; screens request initial focus in a `LaunchedEffect`; lists are plain
  `LazyRow` / `LazyColumn` with a `BringIntoViewSpec` pivot where rows need a consistent focus line.
- The Iris seed color constant is duplicated in `:core:designsystem-tv` (one value, documented) rather than moved, so
  the phone design system stays as it is.
- Screenshot tests follow ADR 0007 with TV variants: `@MqTvPreviews` = 1080p TV device, dark theme, font scale 1.0 and
  1.3 (the phone's 2.0 breaks every 10-foot layout).

## Consequences

- Two component sets to maintain; they share tokens by value (seed, fonts), not by module. A change of the Iris seed
  must be made in both design systems (a unit test in `:core:designsystem-tv` compares the two constants is not
  possible without a module edge, so the ADR is the reminder).
- The `expressive-design` skill applies to the phone; TV UI follows this ADR and tv-material's guidance until a
  `tv-design` skill exists.
- tv-material has no search bar, slider or dialog: Compose `Dialog`, `BasicTextField` and a focusable progress row with
  D-pad seeking cover TV 1.
