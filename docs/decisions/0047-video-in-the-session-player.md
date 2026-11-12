# 0047. Video plays in the session's player; a video replaces the music queue and hands it back

- Status: Accepted (owner, 2026-11-12)
- Date: 2026-11-12

## Context

Mobile 3 · Video (ADR 0025) adds movies and episodes to a music app whose single `ExoPlayer` lives in
`MediatheqPlaybackService` (`MediaLibraryService`, architecture skill section 7, media3 skill "no second player
instance"). That player already carries the notification, media buttons, audio focus (ADR 0031), per-output audio
profiles (ADR 0032), progress reporting, the saved queue (ADR 0020) and the queue rules (ADR 0030). A video needs a
surface, a different screen and no shuffle, but the same audio path, reporting and system integration.

Options: a second `ExoPlayer` owned by the video screen (two notifications, two focus owners, duplicated reporting
and output rules, forbidden by the media3 skill); a separate video service (the same duplication one process away);
or the one session player with a video item on it.

## Decision

- **One player.** `PlaybackConnection.playVideo(video, choice, startPosition)` replaces the session player's
  playlist with one video item (the next episode is appended near the end when auto-play is on). The music queue is
  not touched on disk: `QueuePersistence` never saves a video item, so the last saved music queue and its
  SharedPreferences flag (ADR 0020) stay as they were before the video.
- **Hand-back.** When the video item ends, is cleared, or the user stops it from the player, the service restores
  the saved music queue paused at its saved entry and position through the cold-start restore path. The Now Playing
  overlay collapses when a video starts and returns with the music queue.
- **The surface** is attached by the video screen through the `MediaController` (`PlayerSurface(controller,
  SURFACE_TYPE_SURFACE_VIEW)` from `media3-ui-compose`); the controller forwards the surface command to the service's
  player, which runs in the same process. Leaving the screen clears the surface; audio continues only in
  picture-in-picture or when the "keep audio" setting is on (default: pause).
- **Audio rules apply unchanged**: the video's audio goes through `AudioPolicy` (output profiles, EQ, normalization
  off for video, no fades), with one new rule: no audio offload while a video item plays. Queue rules (ADR 0030) do
  not apply to a video item (no shuffle, no repeat, next / previous = next episode / restart).
- Progress reports for video use the existing `PlaybackReporter` with `AudioStreamIndex` and `SubtitleStreamIndex`
  added; positions are the server's (no app-side resume table, unlike music, R20).

## Consequences

- Music and video never play together; starting one stops the other, which is what a phone does anyway.
- The video screen never creates a player; screenshot tests replace the surface with a slot.
- A fallback exists if the controller path to the surface fails on a device: the activity binds the service and
  hands the surface directly; the issue that first plays a video proves the controller path with a device test.
- The notification shows the video's poster and title; media buttons work for video as for music.
- If daily use shows that music should resume by itself after a video, that is one setting in a later issue.
- In the `Video` app mode (ADR 0051) the hand-back does not happen: the saved music queue stays saved and is
  restored only after a switch back to `Music` or `Both`.
