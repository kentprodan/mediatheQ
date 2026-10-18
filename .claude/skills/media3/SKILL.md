---
name: media3
description: Media3 playback rules for mediatheQ - MediaLibraryService and session, PlaybackConnection, stream selection, DeviceProfile, audio quality per network, Opus transcoding, Dolby Atmos (E-AC-3 JOC / TrueHD) decode vs passthrough, audio offload, USB DAC bit-perfect, EQ, ReplayGain, gapless, prefetch and cache, downloads, progress reporting. Use for any code in :core:playback or :core:download, and for anything that decides how audio is played.
---

# mediatheQ - Media3 playback

Scope now: **music** (phases F1 phone, F2 TV). Video rules are added in F3 (new section or new skill).
Read together with `architecture` (section 7, playback boundary) and `jellyfin` (sections 5-6).

## 0. Version and sources of truth

- `androidx.media3:*` **1.11.x** stable (1.11.1 on 2026-10-18). All media3 artifacts share one version in `libs.versions.toml`.
- Release notes before every bump: https://developer.android.com/jetpack/androidx/releases/media3 (1.11 had breaking changes: `AudioSink.configure` takes a data class, `MediaSession.Callback.onConnectAsync`, stricter session threading).
- API reference: https://developer.android.com/reference/androidx/media3/exoplayer/package-summary
- Official demos (androidx/media, session demo) for wiring patterns.
- Never invent APIs. If unsure a class or method exists in the pinned version, check the reference first.

## 1. Architecture of :core:playback

```
MediatheqPlaybackService : MediaLibraryService      the only owner of the player
  ├─ ExoPlayer (built by PlayerFactory)              audio-only renderers in F1/F2
  ├─ MediaLibrarySession + LibraryCallback           browse tree for Auto/Wear later, queue commands
  ├─ StreamResolver                                  MediaItem -> playable URI + headers (asks PlaybackSource)
  ├─ AudioRouteMonitor                               output device: speaker, BT, USB DAC, HDMI/eARC, Cast
  ├─ AudioPolicy                                     decides offload / bit-perfect / EQ / normalization (pure Kotlin)
  ├─ ProgressReporter                                server reporting (jellyfin skill, section 6)
  └─ QueuePersistence                                saves/restores queue + position in Room
PlaybackConnection (UI side)                         async MediaController -> StateFlow<PlayerUiModel> + commands
```

- `AudioPolicy`, `StreamResolver` decisions and `DeviceProfileBuilder` are **pure Kotlin with injected inputs** (capabilities, network type, settings, route) -> fully unit tested without a device.
- Only the service touches `ExoPlayer`, `AudioManager`, `MediaCodecList`. Everything else gets snapshots of those as data.
- Session threading (1.11): call session/player methods on the application looper only; never block it.
- Implement `MediaSession.Callback.onConnectAsync()` (1.11 API). Untrusted controllers get read-only access by default; allow full control only for our app, System UI, Android Auto, Wear (later).

## 2. Building the player

- `ExoPlayer.Builder` with:
  - `setAudioAttributes(AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC), handleAudioFocus = true)`
  - `setHandleAudioBecomingNoisy(true)` (pause when headphones unplug)
  - `setWakeMode(C.WAKE_MODE_NETWORK)` for streaming
  - `setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))` (section 9)
  - renderers: `DefaultRenderersFactory` with `setEnableDecoderFallback(true)`; no video renderers until F3
  - audio sink: default `DefaultAudioSink` (passthrough + offload support built in). Only customize via `DefaultAudioSink.Builder` if a test proves it is needed; custom sinks break on media3 bumps.
- One player for the whole app process. Rebuild only when the output policy requires it (section 7).

## 3. Stream selection (with Jellyfin `PlaybackInfo`)

Input to `StreamResolver`: track, network type (Wi-Fi / Ethernet / cellular / metered), user settings, current output route, device capabilities.

| Situation | Request to server | Result |
|---|---|---|
| Wi-Fi / Ethernet, codec decodable or passthrough-able | direct play, no bitrate cap | original file |
| Cellular / metered | max 256 kbps, transcoding target **Opus** | server transcodes lossless and high-bitrate files |
| Codec not supported on this device/route | let the server transcode | Opus (phone) / lossless-safe target per DeviceProfile |
| Downloaded track | none, play local file | original (or the downloaded quality) |

- Transcoding container for Opus: one that Media3 extracts reliably (Ogg or WebM/Matroska). Pick one in an ADR after a device test; do not mix.
- The decision is a pure function `selectStream(track, context): StreamDecision` with exhaustive tests (one per row above plus Atmos rows in section 4).

## 4. Dolby Atmos (music)

Our Atmos music is (at least partly) **M4A with E-AC-3 JOC**; some may be **TrueHD Atmos** (MKA).
Jellyfin marks it as `AudioSpatialFormat = DolbyAtmos` (see `jellyfin` skill). Map it to `SpatialFormat.DolbyAtmos` in the domain model.

Two ways to play it:

- **Passthrough (TV, F2):** the compressed bitstream goes untouched over HDMI/eARC to the soundbar/AVR (Streamer -> TV -> eARC -> LG DSN11RG; later Denon AVR). This is the only way to get real Atmos. Requires the output route to report the encoding as supported (`AudioCapabilities` for the current route: E-AC-3 JOC, TrueHD).
- **Decode (phone):** the device decodes to PCM. Media3 1.11 removed the E-AC-3 JOC -> E-AC-3 fallback on Pixel because Pixel's standard E-AC-3 decoders **cannot decode E-AC-3 JOC**. So on the Pixel 9, E-AC-3 JOC tracks must **not** be sent for direct play unless a JOC-capable decoder is detected on the device.

Rules:

1. DeviceProfile is built from **real capabilities of the current route** (decoders from `MediaCodecList`, passthrough encodings from `AudioCapabilities` of the active output). Never hard-code "phone supports X".
2. "Prefer Dolby Atmos" (setting, default **Automatic**):
   - Route supports passthrough of the track's encoding -> direct play, passthrough, no transcoding, no EQ, no normalization (bitstream cannot be modified).
   - Route cannot passthrough and device cannot decode JOC (Pixel phone) -> if the album has a **stereo version** (stereo/Atmos as one album, later feature) play stereo; otherwise let the server transcode to a stereo/multichannel PCM-friendly format. Show "Atmos not available on this output".
   - Never silently downgrade on a route that could do Atmos: if passthrough was expected and failed, surface an error state with a reason.
3. Route changes (plug HDMI, connect BT, USB DAC) re-run stream selection **for the next track**; the current track continues unless playback fails.
4. The Now Playing "Dolby Atmos" badge shows only when the **actual** output is Atmos (passthrough active), read from the player's current audio format + route, not from metadata alone.
5. Atmos passthrough cannot be proven on JVM. Unit-test the decision; prove the output manually (`/test`: soundbar display shows Atmos).

## 5. Audio offload (battery)

- Enable via track selection parameters:
  ```kotlin
  TrackSelectionParameters.AudioOffloadPreferences.Builder()
      .setAudioOffloadMode(AUDIO_OFFLOAD_MODE_PREFERENCE_ENABLED)
      .setIsGaplessSupportRequired(true)     // we promise gapless albums
      .setIsSpeedChangeSupportRequired(false)
      .build()
  ```
  Verify constant names in the pinned version.
- `ENABLED` (not `REQUIRED`): fall back to normal playback when offload is unavailable.
- Offload is **off** when: EQ is active, bit-perfect USB is active, passthrough is active, or a feature needs PCM access (visualizer, crossfade, waveform). `AudioPolicy` decides; one place only.
- Volume-based normalization (ReplayGain via player volume) works with offload.
- Manual test: screen off, long album, battery and gapless transitions (1.11.1 fixed offload stalls at gapless transitions - keep media3 current).

## 6. USB DAC bit-perfect (phone)

- Android API: `AudioManager.getSupportedMixerAttributes(usbDevice)`, `setPreferredMixerAttributes(attrs, usbDevice, mixerAttributes)` with mixer behavior **BIT_PERFECT**, `clearPreferredMixerAttributes(...)`, `addOnPreferredMixerAttributesChangedListener(...)`. Usage must be `USAGE_MEDIA`.
- Behavior: no mixing, no system volume, no effects; data goes as-is to the DAC. Playback attaches to that output only when **encoding, channel mask and sample rate match** the preferred mixer attributes.
- Implementation:
  1. `AudioRouteMonitor` detects a USB audio output and the setting "Bit-perfect USB" is on.
  2. For each track, pick the supported mixer attribute matching the track's PCM format (sample rate, bit depth, channels); set it before the track starts; clear it when playback stops or the DAC disconnects.
  3. Force: no offload, no EQ, no normalization (volume is controlled on the DAC). Show a "Bit-perfect" indicator and a note that phone volume does not apply.
  4. If no matching mixer attribute exists -> normal playback, indicator off, no error.
- Check the API level requirement in the reference before implementing and guard with `Build.VERSION.SDK_INT`.
- Manual test with the owner's DAC: indicator on, sample rate on the DAC display matches the file.

## 7. EQ, normalization, crossfade

- **EQ:** `android.media.audiofx.DynamicsProcessing` attached to the player's audio session ID (`ExoPlayer.audioSessionId`). Bands and presets stored in DataStore. EQ on -> offload off (section 5). EQ is disabled in bit-perfect and passthrough modes, and the UI says why.
- **Normalization (default: Album):** ReplayGain album gain (fallback track gain) from server metadata, applied with `player.volume` per item (clamped, no boost above 0 dB unless the setting allows it with a limiter). Off in bit-perfect and passthrough modes.
- **Crossfade:** off by default and not implemented in F1. If added later it needs PCM processing (disables offload).
- `AudioPolicy.decide(route, settings, trackFormat): AudioMode` returns one of `Passthrough`, `BitPerfect`, `Offload`, `Processed(eq, normalization)` - exhaustively unit tested, including every conflict above.

## 8. Gapless

- Media3 handles gapless for MP3 (LAME/Xing), AAC (iTunSMPB) and FLAC when the files carry the metadata and the stream is direct-played.
- Transcoded streams are not reliably gapless. Prefer direct play for albums marked gapless; on cellular accept the trade-off.
- Queue the next items in the player playlist ahead of time (not "load on end") so the player can prepare the transition.

## 9. Prefetch and cache (streaming)

- One `SimpleCache` for streaming in `cacheDir/media`, `LeastRecentlyUsedCacheEvictor(2 GB)` (size from settings), database provider shared with downloads.
- `CacheDataSource.Factory` wrapping the OkHttp data source (`OkHttpDataSource.Factory` with the shared client, so the Jellyfin auth header is applied).
- Cache key = stable track ID + quality (not the URL with session params), via a custom `CacheKeyFactory`, so the same track at the same quality is reused across sessions.
- Prefetch: the next 1-2 queue items on Wi-Fi (whole track for music), only the next item on cellular. Use `DefaultPreloadManager` (`TargetPreloadStatusControl` returning `PreloadStatus.specifiedRangeCached(...)`) or a `CacheWriter` job; choose one in an ADR and stick to it.
- Never prefetch on metered networks when "Save data" is on.

## 10. Downloads (:core:download)

- Media3 `DownloadManager` + `DownloadService` (foreground, with notification), separate `SimpleCache` in `filesDir/downloads` with **`NoOpCacheEvictor`** (downloads are never evicted).
- Download quality setting: Original (default) or a transcoded quality; the chosen stream URL comes from `StreamResolver` with a "download" context.
- Download state goes into Room via `DownloadIndex` -> repository -> UI (Downloads tab).
- Playback prefers the download cache automatically (same cache key scheme).
- Requirements: Wi-Fi only by default; resumes after reboot; removing a download never touches the server (see `jellyfin` R5).

## 11. Queue, session and system integration

- Queue = Media3 playlist of `MediaItem`s with `mediaId = MediaId.toString()` and full `MediaMetadata` (title, artist, album, artwork URI sized for notification).
- Persist queue + index + position in Room on every change (debounced) and on service destroy; restore on cold start (paused).
- Notification via the default `MediaNotification.Provider`; custom buttons (favorite, shuffle) through session custom commands.
- Audio focus: handled by ExoPlayer (`handleAudioFocus = true`); ducking allowed.
- Sleep timer: lives in the service, survives UI death.
- Android Auto browse tree (`LibraryCallback.onGetChildren`) is designed now, implemented in F5.

## 12. Progress reporting and user data

- `ProgressReporter` follows the `jellyfin` skill section 6 (start, progress every ~10 s and on pause/seek, stopped).
- Uses `PlaySessionId` / `MediaSourceId` from `StreamResolver`.
- Ratings/favorites while playing: obey `jellyfin` R2.

## 13. Testing (see `testing` skill)

- Unit (JVM): `selectStream`, `DeviceProfileBuilder`, `AudioPolicy`, cache key factory, queue persistence mapping. One test per table row and per conflict rule.
- Robolectric + `media3-test-utils` (`TestExoPlayerBuilder`, `FakeClock`, `TestPlayerRunHelper`): queue behavior, shuffle/repeat, next/previous, restore after restart, progress reporting cadence with a fake reporter.
- Device (`androidTest`): service starts, notification shows, `MediaController` connects.
- Manual (`/test`): offload + gapless on Pixel 9; cellular 256 Opus; USB DAC bit-perfect; Atmos passthrough on Streamer + LG DSN11RG; Atmos-not-available message on Pixel 9 for E-AC-3 JOC.

## 14. Don'ts

- No `ExoPlayer` outside the service. No second player instance.
- No custom `AudioSink` subclass without an ADR.
- No direct USB access APIs for audio (use mixer attributes).
- No hard-coded device capabilities or codec lists.
- No transcoding of Atmos tracks on a route that can passthrough them.
- No EQ/normalization silently applied in bit-perfect or passthrough modes.
- No streaming URL with `ApiKey` in logs or cache keys.

## 15. Done checklist

- [ ] Decision logic is pure Kotlin and unit tested (every row/rule touched).
- [ ] Playback still works with offload on and off; gapless verified for affected paths.
- [ ] Route changes handled (speaker, BT, USB DAC, HDMI).
- [ ] No regression in cache/download key scheme (cached tracks still hit).
- [ ] Manual test items queued for anything hardware-only (Atmos, bit-perfect, offload battery).
- [ ] media3 version and any 1.11+ API usage checked against release notes.
