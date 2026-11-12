# 0049. Video player UI: `media3-ui-compose` surface, own Expressive controls, `SubtitleView` for cues, a full-screen destination, platform PiP

- Status: Accepted (owner, 2026-11-12)
- Date: 2026-11-12

## Context

The phone UI is Material 3 Expressive (ADR 0004, expressive-design skill) in Compose, with Navigation 3 (ADR 0005)
and Now Playing as an overlay owned by the shell. Media3 1.11.1 (stable, checked 2026-11-12) ships
`media3-ui-compose` with `PlayerSurface`, `ContentFrame`, `PresentationState` and button state holders, all
`@UnstableApi`, and `media3-ui-compose-material3` with a ready `Player` composable that depends on plain Material 3
and `com.google.android.material`. There is no Compose cue composable in 1.11.1 (`CuesState` arrives in 1.12);
`androidx.media3.ui.SubtitleView` renders text and bitmap cues with the system caption style. Google prefers
`SurfaceView` (power, frame timing, HDR output) and warns about `PlayerView` inside `AndroidView` on API 34. PiP on
API 31+ is `PictureInPictureParams.Builder().setAutoEnterEnabled(true)` with a source rect hint; the Jetpack PiP
library (`androidx.core:core-pip`) is at 1.0.0-alpha02.

## Decision

- **Surface:** `PlayerSurface(player = controller, surfaceType = SURFACE_TYPE_SURFACE_VIEW)` with
  `rememberPresentationState` for the shutter and aspect ratio, wrapped once in `:core:ui` so the `@UnstableApi`
  opt-in stays in one file (checked at every Media3 bump, like `LazyStreamMediaSource`).
- **Controls:** our own composables in `:core:ui` / `:feature:video` on the design system (play / pause morph, seek
  bar with chapter marks, times, +-10 s, title line, tracks sheet, chapters sheet, next-episode countdown), driven by
  `PlaybackConnection.videoState`; not `media3-ui-compose-material3`, not `PlayerView`.
- **Cues:** `MqSubtitles` wraps `SubtitleView` in an `AndroidView`, fed by `Player.Listener.onCues` through the
  connection; it follows `CaptioningManager` (user size and style) and draws bitmap cues (PGS). When Media3 1.12 is
  stable, `rememberCuesState` replaces the listener inside the wrapper.
- **Destination, not overlay:** the player is `VideoPlayerKey(id, startPosition?)`, a full-screen entry pushed on
  the current tab's back stack; the Now Playing overlay collapses while a video plays and the mini player shows the
  video (poster, title) and opens the player. The screen locks landscape for wide videos, hides system bars, keeps
  the screen on while playing, and restores all of it on leave.
- **PiP:** platform API only. `MainActivity` declares `supportsPictureInPicture` and the `configChanges`; a
  `PipController` in `:app` updates `PictureInPictureParams` (auto-enter, source rect from the surface bounds, aspect
  ratio, seamless resize, play / pause and next actions) while the player screen is shown and the setting is on; in
  PiP mode only the surface and the cues compose.

## Consequences

- Screenshot tests take the surface and the cues as slots (a dark `Box`, a text stand-in): layoutlib renders no
  `SurfaceView`.
- Subtitle styling is the system's (accessible, no own settings page); ASS/SSA styling is lost unless burned in
  (ADR 0050).
- A single activity in PiP shows only what the player screen composes in that mode; Back from the player while PiP
  auto-enter is on still pauses (PiP is for the home gesture).
- The Jetpack PiP library can replace `PipController` once stable; the controller is one class.
