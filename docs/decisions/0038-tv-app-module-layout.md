# 0038. TV app module layout: `:app-tv`, `:feature-tv:*`, shared core

- Status: Accepted
- Date: 2026-11-11

## Context

The architecture skill reserves `:app-tv` + `:feature-tv:*` for a later phase and says a module is created in the issue
that first needs it. TV 1 is that issue set. Google recommends one app for mobile and TV
(https://developer.android.com/training/tv/start/start), but the phone UI (Material 3 Expressive) and the TV UI
(tv-material, ADR 0037) cannot share a theme, the owner's app is not on Google Play, and a TV box and a phone never
share an install. The data, playback and provider layers have no UI and already serve any client.

## Decision

- **A second application module `:app-tv`** with `applicationId = "io.github.kentprodan.mediatheq.tv"`, its own
  `TvActivity` (`MAIN` with both `LEANBACK_LAUNCHER` and `LAUNCHER` categories, so
  `PackageManager.getLaunchIntentForPackage`, which the playback service uses for the session activity, keeps working),
  `android:banner` (xhdpi 320 x 180), `android.software.leanback` and `android.hardware.touchscreen` not required,
  `android:colorPrimary` for the launch animation, `allowBackup=false` and the cleartext rule of ADR 0013. It applies
  the same convention plugins as `:app` (application, hilt, screenshot) and AboutLibraries (ADR 0022). No
  `:baselineprofile` variant for TV.
- **Feature modules `:feature-tv:<screen>`** with a new convention plugin `mediatheq.android.feature.tv` that allows:
  `:core:model`, `:core:common`, `:core:navigation`, `:core:data`, `:core:playback` (controller API),
  `:core:designsystem-tv`, `:core:ui-tv`, plus the lifecycle and Hilt ViewModel libraries and `:core:testing` for
  tests. Forbidden, in addition to the rules of the architecture skill: `:feature-tv:*` -> `:core:designsystem`,
  `:core:ui`, `:feature:*`; and `:feature:*` or `:app` -> any TV module.
- **Shared, unchanged:** `:core:model`, `:core:common`, `:core:navigation`, `:core:network`, `:core:server`,
  `:provider:jellyfin`, `:core:database`, `:core:datastore`, `:core:data`, `:core:playback`, `:core:download` (wired
  for the download cache the player needs; no Downloads screen on TV 1), `:core:testing`.
- **Pure helpers move down:** `DurationFormat`, `NameOrder` and the quality-label text in `:core:ui` are plain Kotlin;
  they move to `:core:common` so both UI layers read one implementation.
- Each TV screen keeps the 4-file pattern (`XViewModel`, `XUiState`, `XScreen`, `XEntry`) and the ViewModel rules of
  the architecture skill; TV ViewModels are written against the same repositories and are not shared with the phone
  (ADR 0040 for onboarding, the same for every screen).
- CI stays one workflow: the tasks run without a module prefix, so `:app-tv` and the TV modules are built, unit tested,
  screenshot validated and linted from their first PR.

## Consequences

- Two APKs from one repository; the `versionName` is shared through the version catalog.
- Repository and playback changes reach both apps at once; UI work is done twice, once per toolkit.
- The architecture skill's module map and dependency table must list the TV modules and the new plugin.
- A device with both APKs installed (a tablet, for testing) has two Jellyfin devices (two `DeviceId`s), which is the
  normal case for two installs (R9).
