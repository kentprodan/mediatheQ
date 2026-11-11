# 0045. Subsonic streaming: `stream` with raw or Opus, progressive only, scrobble reporting

- Status: Accepted
- Date: 2026-11-11

## Context

On Jellyfin the server decides (`PlaybackInfo` with our `DeviceProfile`) and conversions stream as HLS (ADR 0019).
Subsonic's `stream` endpoint takes `format` (`raw` disables transcoding) and `maxBitRate`; Navidrome converts to
its `DefaultDownsamplingFormat` (`opus` by default) or the requested format, progressive, without `Content-Length`
and with `Accept-Ranges: none` ([navidrome#6170](https://github.com/navidrome/navidrome/issues/6170)). The
OpenSubsonic `transcoding` extension (`getTranscodeDecision`, Jellyfin-like, in Navidrome 0.64) exists but adds a
request and a second decision model for the same three cases. Reporting on Subsonic is `scrobble`
(`submission=false` now playing, `true` a play); the `playbackReport` extension adds timeline reports.

## Decision

- `SubsonicPlaybackSource.stream` calls `getSong` once and decides with a pure function on the existing
  `DeviceProfile`: container and codec in the direct-play lists and bitrate under `maxStreamingBitrate` ->
  `stream?id=&format=raw` (`DirectPlay`, `canSeek = true`, `sizeBytes = song.size`); otherwise
  `stream?id=&maxBitRate=<target kbps>&format=<target codec>` (`Transcode`, `canSeek = false`, `segmented = false`).
  Converted downloads (ADR 0024) use the same request and stay one progressive file.
- `mediaSourceId = itemId`; `playSessionId` = a fresh UUID per stream, so cache keys (`transcode:<playSessionId>`,
  ADR 0018) stay unique and reporting has a session to refer to.
- Reporting: start -> `scrobble` with `submission=false`; stop -> `scrobble` with `submission=true` and `time` when
  the track played at least half its length or four minutes (Last.fm rule); progress -> nothing
  (`ServerCapabilities.reportsProgress = false`). `reportPlayback` is a later issue.
- The `transcoding` extension and HLS are not used in Servers 1. `transcodeOffset` (seek by restarting at
  `timeOffset`) is polish, not scope.
- No spatial audio information on Subsonic (`spatialAudioInfo = false`): no Atmos rule and no Atmos note.

## Consequences

- `StreamResolver`, `DeviceProfileBuilder`, prefetch, cache and download code are unchanged.
- Converted streams on Subsonic do not seek (the player shows the state it already has for `canSeek = false`).
- A server-side Max Bitrate per player (Navidrome Settings -> Players) can lower the quality below the request; the
  app shows the requested quality and the `subsonic` skill documents the setting.
