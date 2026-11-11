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
  ├─ AudioOutputPort                                 output device (phone, BT, USB DAC; HDMI/eARC, Cast later) + USB mixer
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
  - `setAudioAttributes(AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC), handleAudioFocus = false)`: `AudioFocusController` owns focus (ADR 0031)
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

- Quality per network kind comes from Settings -> Playback -> Decoding and transcoding (#25, #138): `StreamingQuality` Original / 320 / 256 / 192 / 128 kbps, defaults Original on unmetered and 256 on metered. `DeviceProfileBuilder.build(decoders, quality)`: Original = no cap (formats the device cannot decode still convert to Opus 256), otherwise the cap and Opus at that bitrate. `StreamResolver` keeps one stream per track and quality, so a new setting applies at the next load. Downloads ask for the original whenever the device plays it, else one converted file (section 10). A fully cached original still plays at any quality, and prefetch only fetches originals.
- Force instant transcoding (#138, off by default): when the network turns metered (Wi-Fi lost, `NetworkMonitor.meteredChanges`), `InstantTranscoder` replaces the playing entry with an equal item. Our `LazyStreamMediaSource` cannot update in place, so the entry gets a new source, which chooses its stream again at the metered quality. It keeps its place and the shuffle order. This happens only for a track streaming in another quality, never a download or a fully cached original (`wantsNewStream`). The new stream is a new play session, reported as such. Off: the new quality applies at the next load.

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
5. **Phone (#24):** no passthrough routes yet (they come with TV 1). `DeviceProfileBuilder` declares `eac3` only with an `audio/eac3-joc` decoder and never `truehd`, so on the Pixel 9 the server converts Atmos to the usual stereo Opus 256 (also on Wi-Fi). The track carries its spatial format in the media item extras; `AudioPolicy.atmosConverted(isAtmos, playMethod, convertedDownload)` (unknown stream = no note; a converted download counts although it reports a direct play, #89) feeds `AudioOutputState.atmosConverted` -> `AudioOutputInfo` -> Now Playing's note "Dolby Atmos not available · Playing in stereo on this output". A "stereo version" of an album is a later feature; a download of such a track is the server's conversion (#68, section 10).
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
  1. `AudioOutputPort` (platform side, API 34+, `NoAudioOutputPort` below; was `UsbAudioPort`) reports the device media plays on (#140) and finds a `TYPE_USB_DEVICE`/`TYPE_USB_HEADSET` output, maps its mixer attributes to plain `MixerCapability` data and watches device changes. Needs `MODIFY_AUDIO_SETTINGS` (normal permission, declared in `:core:playback`).
  2. `AudioOutputController` (pure, tested with a fake port) runs on every track change, playback stop, device change and settings change: `MixerAttributeSelector` picks the bit-perfect capability whose encoding, sample rate and channel mask equal the track's output format; it is set once per format and cleared when playback stops, the setting goes off or the DAC disconnects.
  3. The output format comes from the file's metadata in the media item extras (`MediaItem.outputFormat()`), as **16-bit PCM**: Media3's default sink outputs 16-bit, so a 24-bit file keeps its sample rate but not its bit depth (float/24-bit output would need its own issue). Only the original file is tried (`AudioPolicy.playsOriginal`: a direct play that is not a converted download, #113): a transcode or a converted download has another format and plays normally. The DAC is the USB device media is routed to (`getAudioDevicesForAttributes`), not any plugged-in USB output. EQ off = effect released, so nothing stays on a bit-perfect session.
  4. Offload is off whenever DAC + setting (decided per route, not per track). EQ off and volume 1 only while a mixer attribute is really set for the track (`AudioMode.BitPerfect`). The state reaches screens through the session extras (`AudioOutputInfo` in `PlayerUiModel`): Now Playing shows "Bit-perfect · 96 kHz" and that phone volume does not apply.
  5. If no matching mixer attribute exists (or the platform refuses it) -> normal playback with EQ and normalization, indicator off, no error.
- Device facts (#65, Pixel 9 + MOMENTUM 4, Android 17): an `AudioTrack` reaches the `BIT_PERFECT` output only when it is opened after the preference with no other `AudioTrack` of ours open. A rate change works on the transition (the old track plays out and is released before the new rate opens its own). One already open when the setting is turned on stays on the mixer, and a seek does not move it (open issue); `AudioOutputState.bitPerfectConfirmed` (from `AnalyticsListener.onAudioTrackInitialized` / `onAudioTrackReleased`) keeps the indicator honest. While a new track's stream is unresolved after a transition its format is assumed to be the file's: dropping the preference then put the next track on the mixer. An `AudioSink` init/write error while preferred (start `-32` while the USB device re-enumerated) drops the preference until the device or the setting changes, and playback goes on normally (`onBitPerfectFailed`, no error shown). A preference the platform drops is set again (`addOnPreferredMixerAttributesChangedListener`). Decisions are logged under `MqAudioOutput`; check with `dumpsys media.audio_flinger` (thread type and the track's `BitPerfect` column).
- Manual test with the owner's DAC: indicator on, sample rate on the DAC display matches the file.

## 7. EQ, normalization, crossfade

- **Per output (#140, ADR 0032):** normalization, equalizer, Bypass processing and bit-perfect live in each output's `OutputProfile` (`AudioOutputRepository`, audio DataStore), not globally. `AudioOutputController` publishes the device media plays on (`AudioOutputState.output`) with every decision; `OutputProfileFollower` remembers it (a new one copies the phone's profile) and hands its settings (`toAudioSettings`) and EQ gains to the controller, so a route change applies that output's settings at once. Ids: `phone`, `bluetooth:<hash of address + name>`, `usb:<hash of product name>`. The old global keys move into the phone's profile once (DataStore migration).
- **Bypass processing:** no EQ and no normalization on that output (`AudioPolicy.normalization` gives Off), so it may offload; bit-perfect is unaffected.
- **EQ:** `android.media.audiofx.DynamicsProcessing` attached to the player's audio session ID (`ExoPlayer.audioSessionId`). Bands and presets stored per output. EQ on -> offload off (section 5). EQ is disabled in bit-perfect and passthrough modes, and the UI says why.
- **Normalization (default: Album):** ReplayGain album gain (fallback track gain) from server metadata (Jellyfin `NormalizationGain`, the album's via a Room relation on track rows), carried in the media item extras and applied with `player.volume` on each item transition (`NormalizationListener`; clamped, no boost above 0 dB unless the setting allows it with a limiter). Controllers cannot set the player volume (`COMMAND_SET_VOLUME` removed), it carries the gain. Off in bit-perfect and passthrough modes, and with Bypass processing.
- **Fades (#142, `VolumeFader`):** fade in (play, resume, seek) and fade out (pause, stop), 0-1000 ms, linear, as a factor multiplied into `player.volume` next to the normalization gain and the focus duck, so they work with offload. Fade in: the factor drops to 0 when play is asked for or a seek lands and rises once `isPlaying`. Fade out: `QueuePlayer.pause()` / `stop()` / `setPlayWhenReady(false)` run after the ramp (`fadeOutThen`); a play cancels it, a second pause cuts at once, task removal and service destroy end it. Pauses on the ExoPlayer itself (audio focus, automatic actions) are immediate. No fades in bit-perfect (fixed volume).
- **Crossfade:** not implemented (#219). It needs a second, short-lived player or PCM mixing (ADR with that issue), disables offload, and never applies on a bit-perfect output.
- `AudioPolicy` (pure, exhaustively unit tested, including every conflict above; `AudioSettings` carries `bypass`): `decide(settings, route, bitPerfectFormat): AudioMode` (`Offload` | `Processed` | `BitPerfect`; F2 adds `Passthrough`), `atmosConverted(isAtmos, playMethod, convertedDownload)`, `playsOriginal(playMethod, convertedDownload)`, `offload(settings, route)` (per route, so it does not flip per track), `wantsBitPerfect(settings, route)`, `volume(gains, normalization, mode)` (1 in bit-perfect).
- **EQ (#22):** five bands (centers 60/230/910/3600/14000 Hz, +-12 dB, presets Flat, Bass boost, Treble boost, Vocal, Custom) in each `OutputProfile` (#140). `DynamicsProcessing` pre-EQ bands are set by **upper edge**: `EqualizerCurve` puts the edges at the geometric midpoints between centers (last at 20 kHz) and lowers the input gain by the largest boost so nothing clips. `EqualizerEffect` is re-created when the audio session id changes and enabled only in `Processed`.

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
- Playback: the player's data source is `ResolvingDataSource` over a **read-only** `CacheDataSource` on the download cache. A track that `DownloadRepository.isDownloaded` gets the download key and plays with no network at all; any other track resolves its stream (its cache key never hits). Covers (#154): `DownloadedCovers` (`:core:data`) keeps one 1024 px cover per downloaded album in `filesDir/download_covers` (`@DownloadCoverDir`, next to the downloads), fetched when a download completes and deleted with the album's last download; `ArtworkUrlResolver` hands out its `file:` URI first, so the app, the notification (`DataSourceBitmapLoader` over `DefaultDataSource`) and the resumption card (`localCoverBytes`) show it offline. Downloaded plays still report to the server with a direct-play session of our own (`StreamResolver.playLocal`); the resolver also keeps whether that download is converted (`isConvertedDownload`, from `CONVERTED_DOWNLOAD`) for the Atmos note.
- Offline (`NetworkMonitor.isOnline`), tracks that are not downloaded fail at once and the load error policy never retries, so the queue does not stall. The service sees the network through `OfflineModeNetwork`: while offline mode is forced (#63, #144: the switch, or Automatic offline mode Not on Wi-Fi on a metered network, `OfflineFilter.forced`) it reports no network, so only downloads and cached tracks play (also with a network) and prefetch stops.
- A track that cannot load (#61): `SkipFailedTrackListener` in the service skips it when only that track is at fault (`OfflineException`; `StreamUnavailableException` with `Forbidden`, `NotFound` or `Client`) and a next item exists; other errors (a dropped connection, a server down, signed out) stop where they are. A queue where nothing loads stops after one lap (repeat all); a new queue starts a new lap. Every error becomes a `PlaybackError` sent with `MediaSession.sendError` to the app's own controller only (not the media notification or cars); `PlaybackConnection.error` -> one snackbar in `:app`.
- State: `DefaultDownloadRepository` (in `:core:download`) keeps every download from the index plus the manager's events; progress is read once a second while someone watches. Actions go through `DownloadService.sendAddDownload/sendRemoveDownload`.
- Download quality (#144): Settings -> Offline, cache, and download -> Media. Original is the rule above; Opus 320 / 256 / 192 always download one converted Ogg file at that bitrate (`DeviceProfileBuilder.download(decoders, quality)`, no direct play). `CONVERTED_DOWNLOAD` holds the bitrate in kb/s (0 = original, 1 from before = 256); `AudioPolicy.convertedKbps` shows it. Max simultaneous downloads (1-4, default 2) is `manager.maxParallelDownloads`, set on the main thread like the requirements; the executor has 4 threads.
- Requirements: Wi-Fi only (`NETWORK_UNMETERED`) by default, or any network (`NETWORK`) from Settings; `DefaultDownloadRepository` sets `manager.requirements` on the main thread (never through the service: Android 12+ blocks starting it from the background). No scheduler: on API 31+ Media3 cannot restart the service from the background, so unfinished downloads resume when the app opens (`resumeDownloads` in `MainActivity`). Android 15+ limits `dataSync` to 6 h a day; a stopped batch resumes at the next app start. Removing a download never touches the server (see `jellyfin` R5).

## 10a. Waveforms (#124, ADR 0027)

- `WaveformAnalyzer` in the service: the current track and the next two (`upcomingTracks`), prefetched originals (`Prefetcher.onCached`) and downloads completing while it runs. No backlog. `LocalFiles` finds a whole file: a completed download (original or converted) or a complete `<id>:original` cache copy; never a converted stream.
- `MediaCodecPcmDecoder`: `MediaExtractorCompat` (`media3-inspector`) on a read-only `CacheDataSource` (no upstream, no writes) + platform `MediaCodec` to float PCM; `WaveformBuckets` folds it into 200 RMS levels. One dedicated background thread, one file at a time; never the player's PCM, so offload is unaffected.
- Stored in Room per file size (`WaveformRepository`); `null` levels = no decoder for the format (not retried); other failures store nothing. Decoding is proven on the phone only; tests use a fake decoder.

## 11. Queue, session and system integration

- Queue = Media3 playlist of `MediaItem`s with `mediaId = MediaId.toString()` and full `MediaMetadata` (title, artist, album, artwork URI sized for notification).
- Persist queue + index + position + shuffle order + repeat mode in Room on every change (debounced) and on service destroy; restore on cold start (paused).
- A queue item's source travels in its metadata extras and is saved with the entry: today the playlist it was played from (`PlaybackConnection.play(playlistId = ...)`, taps through `TrackTaps`; #135), which `ResumeListener` records into that playlist's Resume. Items queued with Play next / Add to queue carry none. A new source (album, genre) goes the same way: one extra, one nullable `queue_entry` column.
- Shuffle turned on mid-queue (`QueuePlayer.setShuffleModeEnabled`): the playing track goes on and every other entry follows once in a new order: weighted (`weightedShuffle`, by last played, favorite and rating) or uniform (`shuffleFrom`) per Settings -> Playback -> Playback (ADR 0030). ExoPlayer alone would reuse its old order, with the current track anywhere in it. Turning it off goes on in queue order.
- Settings -> Playback -> Playback (#138, ADR 0030) lives in `QueuePlayer`, the session's player, so every controller gets it:
  - **Rewind first:** Previous rewinds past 3 s.
  - **Circular:** next and previous wrap; they stay in the available commands at the ends, also in the commands listeners receive.
  - **Keep paused:** off, a manual skip while paused plays.
  - **Stop repeat one:** a manual skip switches it to repeat all; off, it plays the next track as Media3 does.
  - **Preserve Play next order:** items from `Player.playNext` carry a Play next mark and go after earlier marked ones, also shuffled.
  - **Retain player state:** off, a new queue resets repeat (and shuffle unless Shuffle asked for it). The system resuming the saved queue is exempt (`QueuePersistence.resuming`).
  - Automatic skips (`SkipFailedTrackListener`) use the ExoPlayer itself.
- System playback resumption (#43): `MediatheqMediaButtonReceiver` (a `MediaButtonReceiver`) starts the service only when `QueueRepository.hasSavedQueue` (a SharedPreferences flag read at once, ADR 0020; logout, an empty save and a restore that finds nothing clear it), since a service started in the foreground must play or the system kills the app. `onPlaybackResumption` -> `QueuePersistence.resumptionFuture`: waits for the restore `start()` runs, returns its queue, entry and position, and puts the saved shuffle order back after Media3 sets the items again; for System UI at boot (`isForPlayback = false`) only the current item, its cover as bytes from the server (2 s timeout, none offline). Logout forgets the saved queue; the restore brings nothing back while signed out. Force-stopped apps get no media buttons: test with the task swiped away.
- Notification via the default `MediaNotification.Provider`; buttons (#139, `SessionButtons.kt`) through `setMediaButtonPreferences`: previous / next can be hidden, Action 1-3 are favorite, repeat or shuffle as session custom commands with icons that follow their state. System UI and Android Auto use the same preferences.
- Headset buttons (#139, `HeadsetButtons.kt`): `onMediaButtonEvent` counts clicks of HEADSETHOOK / PLAY_PAUSE (PLAY, PAUSE and every key with nothing queued go to Media3, so resumption still works) (`ClickCounter`, 350 ms window, 650 ms in slow mode) into single / double / triple actions, and remaps NEXT / PREVIOUS (next, previous, seek 10 s, nothing). Everything goes through the session's player, so ADR 0030 rules apply.
- Audio focus (#139, ADR 0031): `AudioFocusController`, not ExoPlayer. Short interruption ducks / pauses / nothing (Duck pauses in bit-perfect), a long one resumes unless turned off, permanent loss pauses; refused focus does not play; `willPauseWhenDucked` is set so the system never ducks behind our back. Becoming noisy stays with ExoPlayer.
- Automatic actions (#141, ADR 0033, `AutoActions.kt`): only while the service lives. `autoAction(event, settings, state)` is the pure decision. `AutoActionsController` plays what is queued when an output is added (`AudioDeviceCallback`: wired and USB types = Headphone auto play, Bluetooth types = Bluetooth auto play; outputs already there at start do not count), pauses at media volume 0 and plays on when raised only what that paused (the platform's volume broadcast), and pauses on task removal. Ignore remote stop is `QueuePlayer.stop()` turning into a pause; a dismissed media card (a stop key with `NOTIFICATION_DISMISSED_EVENT_KEY`, seen in `onMediaButtonEvent`) stays a real stop. Android 17 blocks audio from a service started in the background and Media3 leaves the foreground 10 minutes after a pause, so no receiver starts the service for these: a play the platform refuses (no focus) does nothing.
- Bluetooth lyrics (#143, ADR 0034, `BluetoothLyrics.kt`): with the Playback page's switch on and synced lyrics, `QueuePlayer.setLyricsTitle` makes the session metadata's `title` the line sung now and `displayTitle` the real title (getter and the `onMediaMetadataChanged` value alike). Bluetooth reads the title, the system media card prefers the display title, the notification's content title is `displayTitle ?: title`, and the app's screens read the queue item's own metadata. While the switch is on the platform session has no queue (`notificationPlayerCommands(hideQueue)` drops `COMMAND_GET_TIMELINE`), because Bluetooth holds a title back for 2 s when it differs from the queue's active item. `BluetoothLyrics` loads the lyrics once per track, takes the line from the player's position whenever it fires, never changes the title twice within 500 ms, and gives `null` (the real title) on a track change, an empty line, no synced lyrics or a failed load.
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
