---
name: media3
description: Media3 playback rules for mediatheQ - MediaLibraryService and session, PlaybackConnection, stream selection, DeviceProfile, audio quality per network, Opus transcoding, Dolby Atmos (E-AC-3 JOC / TrueHD) decode vs passthrough, audio offload, USB DAC bit-perfect, EQ, ReplayGain, gapless, prefetch and cache, downloads, progress reporting. Use for any code in :core:playback or :core:download, and for anything that decides how audio is played.
---

# mediatheQ - Media3 playback

Scope now: **music** (milestones Mobile 1 phone, TV 1 TV; ADR 0025). Video rules are added with Mobile 3 / TV 2 Video (new section or new skill).
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
  ├─ ExoPlayer (built by PlayerFactory)              audio-only renderers until Video
  ├─ MediaLibrarySession + LibraryCallback           browse tree for Auto/Wear later, queue commands
  ├─ StreamResolver                                  MediaItem -> playable URI + headers (asks PlaybackSource)
  ├─ StreamMediaSourceFactory                        per item: HLS (converted) or the file data source (ADR 0019)
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
  - renderers: `DefaultRenderersFactory` with `setEnableDecoderFallback(true)`; no video renderers until Video
  - audio sink: default `DefaultAudioSink` (passthrough + offload support built in). Only customize via `DefaultAudioSink.Builder` if a test proves it is needed; custom sinks break on media3 bumps.
- One player for the whole app process. Rebuild only when the output policy requires it (section 7).

## 3. Stream selection (with Jellyfin `PlaybackInfo`)

Input to `StreamResolver`: track, network type (Wi-Fi / Ethernet / cellular / metered), user settings, current output route, device capabilities.

| Situation | Request to server | Result |
|---|---|---|
| Wi-Fi / Ethernet, codec decodable or passthrough-able | direct play, no bitrate cap | original file |
| Cellular / metered | the user's quality, default 256 kbps, transcoding target **Opus** at that bitrate | server transcodes lossless and high-bitrate files |
| Codec not supported on this device/route | let the server transcode | Opus (phone) / lossless-safe target per DeviceProfile |
| Downloaded track | none, play local file | original (or the downloaded quality) |

- Quality per network kind comes from Settings (#25): `StreamingQuality` Original / 256 / 128 kbps, defaults Original on unmetered and 256 on metered. `DeviceProfileBuilder.build(decoders, quality)`: Original = no cap (formats the device cannot decode still convert to Opus 256), otherwise the cap and Opus at that bitrate. `StreamResolver` keeps one stream per track and quality, so a new setting applies at the next load. Downloads ask for the original whenever the device plays it, else one converted file (section 10). A fully cached original still plays at any quality, and prefetch only fetches originals.

- Converted streams are **HLS** with Opus in fMP4 segments (ADR 0019, supersedes the Ogg stream of ADR 0016), so they seek. `StreamMediaSourceFactory` gives every `mediatheq://` item a `LazyStreamMediaSource`: when the player prepares the item it resolves the stream (`chooseStreamSource`, off the playback thread) and prepares either an `HlsMediaSource` on the playlist (segmented stream; app OkHttp client with the auth header, no cache) or the progressive source on the `mediatheq://` URI (download, cached original, offline, lasting failure, file, progressive fallback). Network/server errors are retried in the choice (1, 2, 4 s); HLS requests read the auth header at each request. The timeline keeps the queue item. The file data source reuses the stream the item started with and refuses a playlist.
- The decision is a pure function `selectStream(track, context): StreamDecision` with exhaustive tests (one per row above plus Atmos rows in section 4).

## 4. Dolby Atmos (music)

Our Atmos music is (at least partly) **M4A with E-AC-3 JOC**; some may be **TrueHD Atmos** (MKA).
Jellyfin marks it as `AudioSpatialFormat = DolbyAtmos` (see `jellyfin` skill). Map it to `SpatialFormat.DolbyAtmos` in the domain model.

Two ways to play it:

- **Passthrough (TV, TV 1):** the compressed bitstream goes untouched over HDMI/eARC to the soundbar/AVR (Streamer -> TV -> eARC -> LG DSN11RG; later Denon AVR). This is the only way to get real Atmos. Requires the output route to report the encoding as supported (`AudioCapabilities` for the current route: E-AC-3 JOC, TrueHD).
- **Decode (phone):** the device decodes to PCM. Media3 1.11 removed the E-AC-3 JOC -> E-AC-3 fallback on Pixel because Pixel's standard E-AC-3 decoders **cannot decode E-AC-3 JOC**. So on the Pixel 9, E-AC-3 JOC tracks must **not** be sent for direct play unless a JOC-capable decoder is detected on the device.

Rules:

1. DeviceProfile is built from **real capabilities of the current route** (decoders from `MediaCodecList`, passthrough encodings from `AudioCapabilities` of the active output). Never hard-code "phone supports X".
2. "Prefer Dolby Atmos" (setting, default **Automatic**):
   - Route supports passthrough of the track's encoding -> direct play, passthrough, no transcoding, no EQ, no normalization (bitstream cannot be modified).
   - Route cannot passthrough and device cannot decode JOC (Pixel phone) -> if the album has a **stereo version** (stereo/Atmos as one album, later feature) play stereo; otherwise let the server transcode to a stereo/multichannel PCM-friendly format. Show "Atmos not available on this output".
   - Never silently downgrade on a route that could do Atmos: if passthrough was expected and failed, surface an error state with a reason.
3. Route changes (plug HDMI, connect BT, USB DAC) re-run stream selection **for the next track**; the current track continues unless playback fails.
4. The Now Playing "Dolby Atmos" badge shows only when the **actual** output is Atmos (passthrough active), read from the player's current audio format + route, not from metadata alone.
5. **Phone (#24):** no passthrough routes yet (they come with TV 1). `DeviceProfileBuilder` declares `eac3` only with an `audio/eac3-joc` decoder and never `truehd`, so on the Pixel 9 the server converts Atmos to the usual stereo Opus 256 (also on Wi-Fi). The track carries its spatial format in the media item extras; `AudioPolicy.atmosConverted(isAtmos, playMethod)` (unknown stream = no note) feeds `AudioOutputState.atmosConverted` -> `AudioOutputInfo` -> Now Playing's note "Dolby Atmos not available · Playing in stereo on this output". A "stereo version" of an album is a later feature; a download of such a track is the server's conversion (#68, section 10).
6. Atmos passthrough cannot be proven on JVM. Unit-test the decision; prove the output manually (`/test`: soundbar display shows Atmos).

## 5. Audio offload (battery)

- Enable via track selection parameters:
  ```kotlin
  TrackSelectionParameters.AudioOffloadPreferences.Builder()
      .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
      .setIsGaplessSupportRequired(true)     // we promise gapless albums
      .setIsSpeedChangeSupportRequired(false)
      .build()
  ```
  (`AUDIO_OFFLOAD_MODE_DISABLED` / `ENABLED` / `REQUIRED` in 1.11.) Set it once at start and again only when settings change (`Player.applyAudioMode`): changing track selection parameters re-selects tracks and drops the next item buffered for gapless. With gapless required, formats with encoder delay (MP3/AAC) offload only where the device reports gapless offload; FLAC usually offloads.
- `ENABLED` (not `REQUIRED`): fall back to normal playback when offload is unavailable.
- Offload is **off** when: EQ is active, bit-perfect USB is active, passthrough is active, or a feature needs PCM access (visualizer, crossfade, waveform). `AudioPolicy` decides; one place only.
- Volume-based normalization (ReplayGain via player volume) works with offload.
- Manual test: screen off, long album, battery and gapless transitions (1.11.1 fixed offload stalls at gapless transitions - keep media3 current).

## 6. USB DAC bit-perfect (phone)

- Android API: `AudioManager.getSupportedMixerAttributes(usbDevice)`, `setPreferredMixerAttributes(attrs, usbDevice, mixerAttributes)` with mixer behavior **BIT_PERFECT**, `clearPreferredMixerAttributes(...)`, `addOnPreferredMixerAttributesChangedListener(...)`. Usage must be `USAGE_MEDIA`.
- Behavior: no mixing, no system volume, no effects; data goes as-is to the DAC. Playback attaches to that output only when **encoding, channel mask and sample rate match** the preferred mixer attributes.
- Implementation (#22):
  1. `UsbAudioPort` (platform side, API 34+, `NoUsbAudioPort` below) finds a `TYPE_USB_DEVICE`/`TYPE_USB_HEADSET` output, maps its mixer attributes to plain `MixerCapability` data and watches device changes. Needs `MODIFY_AUDIO_SETTINGS` (normal permission, declared in `:core:playback`).
  2. `AudioOutputController` (pure, tested with a fake port) runs on every track change, playback stop, device change and settings change: `MixerAttributeSelector` picks the bit-perfect capability whose encoding, sample rate and channel mask equal the track's output format; it is set once per format and cleared when playback stops, the setting goes off or the DAC disconnects.
  3. The output format comes from the file's metadata in the media item extras (`MediaItem.outputFormat()`), as **16-bit PCM**: Media3's default sink outputs 16-bit, so a 24-bit file keeps its sample rate but not its bit depth (float/24-bit output would need its own issue). Only a direct play is tried (`StreamResolver.current(...).playMethod`): a transcode has another format and plays normally. The DAC is the USB device media is routed to (`getAudioDevicesForAttributes`), not any plugged-in USB output. EQ off = effect released, so nothing stays on a bit-perfect session.
  4. Offload is off whenever DAC + setting (decided per route, not per track). EQ off and volume 1 only while a mixer attribute is really set for the track (`AudioMode.BitPerfect`). The state reaches screens through the session extras (`AudioOutputInfo` in `PlayerUiModel`): Now Playing shows "Bit-perfect · 96 kHz" and that phone volume does not apply.
  5. If no matching mixer attribute exists (or the platform refuses it) -> normal playback with EQ and normalization, indicator off, no error.
- Known risk: the preference is set on the media item transition, which may be after the player opened the next track's `AudioTrack` (gapless 44.1 -> 96 kHz). Verify on a real DAC; if it is late, fixing it needs a `ForwardingAudioSink` and an ADR (section 14).
- Manual test with the owner's DAC: indicator on, sample rate on the DAC display matches the file.

## 7. EQ, normalization, crossfade

- **EQ:** `android.media.audiofx.DynamicsProcessing` attached to the player's audio session ID (`ExoPlayer.audioSessionId`). Bands and presets stored in DataStore. EQ on -> offload off (section 5). EQ is disabled in bit-perfect and passthrough modes, and the UI says why.
- **Normalization (default: Album):** ReplayGain album gain (fallback track gain) from server metadata (Jellyfin `NormalizationGain`, the album's via a Room relation on track rows), carried in the media item extras and applied with `player.volume` on each item transition (`NormalizationListener`; clamped, no boost above 0 dB unless the setting allows it with a limiter). Controllers cannot set the player volume (`COMMAND_SET_VOLUME` removed), it carries the gain. Off in bit-perfect and passthrough modes.
- **Crossfade:** off by default and not implemented in Mobile 1. If added later it needs PCM processing (disables offload).
- `AudioPolicy` (pure, exhaustively unit tested, including every conflict above): `decide(settings, route, bitPerfectFormat): AudioMode` (`Offload` | `Processed` | `BitPerfect`; F2 adds `Passthrough`), `atmosConverted(isAtmos, playMethod)`, `offload(settings, route)` (per route, so it does not flip per track), `wantsBitPerfect(settings, route)`, `volume(gains, normalization, mode)` (1 in bit-perfect).
- **EQ (#22):** five bands (centers 60/230/910/3600/14000 Hz, +-12 dB, presets Flat, Bass boost, Treble boost, Vocal, Custom) in `AudioPreferences`. `DynamicsProcessing` pre-EQ bands are set by **upper edge**: `EqualizerCurve` puts the edges at the geometric midpoints between centers (last at 20 kHz) and lowers the input gain by the largest boost so nothing clips. `EqualizerEffect` is re-created when the audio session id changes and enabled only in `Processed`.

## 8. Gapless

- Media3 handles gapless for MP3 (LAME/Xing), AAC (iTunSMPB) and FLAC when the files carry the metadata and the stream is direct-played.
- Transcoded streams are not reliably gapless. Prefer direct play for albums marked gapless; on cellular accept the trade-off.
- Queue the next items in the player playlist ahead of time (not "load on end") so the player can prepare the transition.

## 9. Prefetch and cache (streaming)

- One `SimpleCache` for streaming in `cacheDir/media`, with `ResizableLruCacheEvictor` (least recently used, 1/2/4/8 GB from Settings, default 2; the service sets the size, a smaller one applies at the next write), on the database provider `:core:download` provides (ADR 0018).
- Data source: `ResolvingDataSource` -> read-only `CacheDataSource` (downloads) -> `CacheDataSource` (streaming) -> `OkHttpDataSource` with the shared client. The resolver picks, in order: download, complete streaming copy (both with no network), offline failure, server stream.
- Cache keys (ADR 0018): `<MediaId>:original` for a direct play; `transcode:<playSessionId>` for anything else (never written: no length). HLS playlists and segments never pass through a cache (ADR 0019). Never a URL or token. A partial copy whose length differs from the server's `MediaSource.Size` is dropped.
- Prefetch (ADR 0018): `Prefetcher` with `CacheWriter` - next 2 items in play order on unmetered networks, 1 on metered; original files only; skips downloaded/cached tracks before any server request; only while playing and online; never replaces the stream the player knows (`StreamResolver.prefetchStream`); cancelled when the player opens the same track.
- Never prefetch on metered networks when "Save data" is on.

## 10. Downloads (:core:download)

- Media3 `DownloadManager` + `MediatheqDownloadService` (foreground `dataSync`, progress notification), separate `SimpleCache` in `filesDir/downloads` with **`NoOpCacheEvictor`** (downloads are never evicted). Media3's own `StandaloneDatabaseProvider` (SQLite, not Room) holds the download index; #21 shares it for the streaming cache.
- Download id = custom cache key = `MediaId.toString()`. The request URI is a placeholder; the `@DownloadSource` data source finds the track by the key.
- Quality (#68, ADR 0024): the original file when the device plays it as it is, else the server's conversion as one file. `StreamResolver.downloadStream` asks once with `DeviceProfileBuilder.download` (the Original direct-play lists, transcoding target Opus 256 in Ogg, progressive, never HLS); a playlist answer fails the download. The download source records which one in the download cache's content metadata (`CONVERTED_DOWNLOAD`); the Downloads tab shows "Converted copy". A conversion cannot continue mid-file (no length, no ranges, other bytes each time, jellyfin R12): the source refuses to continue it, and "Download again" drops its partial copy first. A complete Ogg file in the cache has its length, so it seeks offline. Downloads never share the player's streams or play sessions.
- Wiring without a module edge: `:core:download` provides `@DownloadCache Cache`, `:core:playback` provides `@DownloadSource DataSource.Factory` (it knows the decoders); both qualifiers live in `:core:common`.
- Playback: the player's data source is `ResolvingDataSource` over a **read-only** `CacheDataSource` on the download cache. A track that `DownloadRepository.isDownloaded` gets the download key and plays with no network at all; any other track resolves its stream (its cache key never hits). Downloaded plays still report to the server with a direct-play session of our own (`StreamResolver.playLocal`).
- Offline (`NetworkMonitor.isOnline`), tracks that are not downloaded fail at once and the load error policy never retries, so the queue does not stall. The service sees the network through `OfflineModeNetwork`: with the offline mode setting on (#63) it reports no network, so only downloads and cached tracks play (also with a network) and prefetch stops.
- A track that cannot load (#61): `SkipFailedTrackListener` in the service skips it when only that track is at fault (`OfflineException`; `StreamUnavailableException` with `Forbidden`, `NotFound` or `Client`) and a next item exists; other errors (a dropped connection, a server down, signed out) stop where they are. A queue where nothing loads stops after one lap (repeat all); a new queue starts a new lap. Every error becomes a `PlaybackError` sent with `MediaSession.sendError` to the app's own controller only (not the media notification or cars); `PlaybackConnection.error` -> one snackbar in `:app`.
- State: `DefaultDownloadRepository` (in `:core:download`) keeps every download from the index plus the manager's events; progress is read once a second while someone watches. Actions go through `DownloadService.sendAddDownload/sendRemoveDownload`.
- Requirements: Wi-Fi only (`NETWORK_UNMETERED`) by default, or any network (`NETWORK`) from Settings; `DefaultDownloadRepository` sets `manager.requirements` on the main thread (never through the service: Android 12+ blocks starting it from the background). No scheduler: on API 31+ Media3 cannot restart the service from the background, so unfinished downloads resume when the app opens (`resumeDownloads` in `MainActivity`). Android 15+ limits `dataSync` to 6 h a day; a stopped batch resumes at the next app start. Removing a download never touches the server (see `jellyfin` R5).

## 11. Queue, session and system integration

- Queue = Media3 playlist of `MediaItem`s with `mediaId = MediaId.toString()` and full `MediaMetadata` (title, artist, album, artwork URI sized for notification).
- Persist queue + index + position + shuffle order + repeat mode in Room on every change (debounced) and on service destroy; restore on cold start (paused).
- Shuffle turned on mid-queue (`QueuePlayer.setShuffleModeEnabled`): the playing track goes on and every other entry follows once in a new random order (`shuffleFrom`); ExoPlayer alone would reuse its old order, with the current track anywhere in it. Turning it off goes on in queue order. Next on repeat one plays the next track (Media3 default).
- System playback resumption (#43): `MediatheqMediaButtonReceiver` (a `MediaButtonReceiver`) starts the service only when `QueueRepository.hasSavedQueue` (a SharedPreferences flag read at once, ADR 0020; logout, an empty save and a restore that finds nothing clear it), since a service started in the foreground must play or the system kills the app. `onPlaybackResumption` -> `QueuePersistence.resumptionFuture`: waits for the restore `start()` runs, returns its queue, entry and position, and puts the saved shuffle order back after Media3 sets the items again; for System UI at boot (`isForPlayback = false`) only the current item, its cover as bytes from the server (2 s timeout, none offline). Logout forgets the saved queue; the restore brings nothing back while signed out. Force-stopped apps get no media buttons: test with the task swiped away.
- Notification via the default `MediaNotification.Provider`; custom buttons (favorite, shuffle) through session custom commands.
- Audio focus: handled by ExoPlayer (`handleAudioFocus = true`); ducking allowed.
- Sleep timer: lives in the service, survives UI death.
- Android Auto browse tree (`LibraryCallback.onGetChildren`) is designed now, implemented in Auto & Wear 1.

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
