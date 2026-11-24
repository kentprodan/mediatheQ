# Audio and video engine: what twelve clients do, and what mediatheQ's engine should become

Research notes (2026-11-24, owner request). Scope: the playback core only, decoding, output, timing, buffering,
recovery. No UI. Dolby Vision gets its own pass later from Dolby's documentation (the owner will provide it); here DV
is covered only as far as the clients' code goes.

Method: a shallow clone of each repository's default branch (2026-11-24), read file by file where the player is
built and where audio or video decisions are made. Ideas are described in our own words. Code may only be copied from
GPL-3.0-compatible sources, with attribution in `THIRD_PARTY.md` (see section 1).

## 1. The twelve clients

| Client | Kind | Engine | License | May we copy code? |
|---|---|---|---|---|
| Moonfin (Moonfin-Core) | Flutter, every platform, video + music | Media3 with ~20k lines of own Kotlin (`moonfin_native_video`), mpv on desktop | GPL-3.0 | Yes, its own files (some come from jellyfin-androidtv, GPL-2.0: check each) |
| Streamyfin | React Native, video + music | Media3 module and an mpv module (`modules/`) | MPL-2.0 | Ideas only |
| Wholphin | Android TV, Kotlin, video + music | Media3 1.11 + optional mpv, own FFmpeg/AV1 builds | GPL-2.0 only | Ideas only |
| jellyfin-androidtv | Official TV client | Media3 (`playback/media3`), libass | GPL-2.0 | Ideas only |
| Findroid | Android phone + TV, Kotlin | Media3, or mpv as a `Player` | GPL-3.0 | Yes |
| Fintunes | React Native, music | react-native-track-player | MIT | Yes (nothing engine-level worth taking) |
| Finamp | Flutter, music | just_audio (ExoPlayer on Android), media_kit on desktop | MPL-2.0 | Ideas only |
| Jamfish | Android, music | ExoPlayer 2 (legacy) | GPL-3.0 | Nothing to take |
| Coppelia | Flutter desktop, music | media_kit (mpv) | Apache-2.0 | Nothing engine-level |
| Linthra | Flutter, music | just_audio with a patched libFLAC fallback; mpv on Linux | AGPL-3.0 | Ideas only |
| PixelPlayer OSS | Android, Kotlin, music | Media3, two players, own sinks and processors | GPL-3.0 | Yes |
| Rhythm | Android, Kotlin, music | Media3, two players, ViPER DSP in a processor | GPL-3.0-or-later | Yes |

The deep engines are Moonfin (video and passthrough), PixelPlayer and Rhythm (music), and Streamyfin and Wholphin
(device quirks). The rest confirm the baseline.

## 2. Where mediatheQ stands

One `ExoPlayer` in `MediatheqPlaybackService` plays music and video (ADR 0047). What it already does well:

- **Output policy in one pure place** (`AudioPolicy`): offload, bit-perfect USB (mixer attributes, `AudioTrackReopener`),
  EQ (`DynamicsProcessing`), normalization and fades, per output profile (ADR 0032).
- **Atmos passthrough** (ADR 0039): `PassthroughCapabilities`, the PCM-only HDMI check (#352), a real-encoding check
  from `onAudioTrackInitialized`.
- **Streaming**: our own `StreamDataSource`, `StreamResolver`, an LRU `StreamingCache` of original files, a `Prefetcher`
  for the next tracks, and an Opus/HLS conversion path.
- **Video**: HDR range types declared from decoders and display (`VideoHdr`), track choice per video / season / default,
  external subtitles, and a display mode chooser with Moonfin's rate rules (`DisplayModeChooser`, #384).

What it does not do (the gaps this research found):

| Area | Gap |
|---|---|
| Renderers | Stock `DefaultRenderersFactory`, no FFmpeg extension: DTS, DTS-HD, TrueHD, AC-4, WMA, APE and other formats the device cannot decode go to the server for transcoding instead of playing direct. |
| Buffering | Stock `DefaultLoadControl` for both music and 4K remuxes: no byte budget tied to the heap and no time-over-size priority. A 100 Mb/s remux can fill the heap (Streamyfin's crash), and a reached byte target can stall loading below the time minimum. |
| Passthrough robustness | No HDMI route-flap hold, no recovery for a bitstream track that went silent after a pause, no session-level fallback when a passthrough track cannot open. This is open as #377. |
| Recovery | No re-prepare when the screensaver took the decoder (`ERROR_CODE_DECODING_RESOURCES_RECLAIMED`), and no "resume stuck in buffering with data loaded" detection. |
| Normalization | Volume only, so a positive ReplayGain (a quiet track) is never applied. There is no peak guard either. |
| Crossfade | None (#219). |
| Hi-res safety | No cap for 352.8/384 kHz PCM. No check that the device really opens a `PCM_FLOAT` `AudioTrack`. |
| Video timing | The display mode switch does not wait for the HDMI renegotiation to settle before audio opens. Rate is taken from the container (3:2 pulldown files read as 23.976). No vsync pacing for bunched timestamps. The mode is handed back between two videos. |
| Dolby Vision / HDR | No profile 7 to 8.1 conversion. No HDR10+ masking for DV + HDR10+ files. No "video track left unselected" detection (audio over a black screen). |
| Containers | Blu-ray DTS in TS (HDMV) is skipped by Media3. zlib PGS in MKV makes the whole file fail. Fragmented MP4 transcodes are unseekable when downloaded. |
| Subtitles | No libass (ASS styling), no PGS, no subtitle delay (#456). |
| Diagnostics | No playback report of what the engine decided (decoder, output encoding, mode switch, buffer state). |

## 3. Audio engine: ideas by topic

### 3.1 Renderers and decoders

- **FFmpeg as the fallback, with the order chosen by output** (Streamyfin, jellyfin-androidtv, Wholphin, Rhythm):
  `EXTENSION_RENDERER_MODE_ON` (platform first) when the route can bitstream, so AC-3/E-AC-3/JOC still reach
  MediaCodec and passthrough and Atmos is never decoded by FFmpeg. `PREFER` only on PCM-only routes, where FFmpeg
  decodes DTS/TrueHD better than some platform decoders. The order only matters for formats both can decode.
- **DTS goes to FFmpeg when FFmpeg is present** (Streamyfin `DtsDecoderSelection`): some platform DTS decoders are
  wrong. DTS Express and DTS-UHD keep the platform decoder, and the DTS-core lookup Media3 adds for DTS-UHD is kept.
- **FLAC to float through FFmpeg** on devices where the platform FLAC decoder corrupts `PCM_FLOAT` (PixelPlayer
  `AudioDecoderPolicy`; Linthra bundles libFLAC for the same reason).
- **Silent-clock guard** (PixelPlayer): when an item has audio but no renderer can decode any of it, Media3 runs a silent
  clock. Detect it and skip the track or ask the server for a conversion, instead of "playing" silence.
- **Decoder fallback on** (`setEnableDecoderFallback(true)`, everyone): a failing hardware decoder retries the next one.

### 3.2 Passthrough (Atmos, DTS) robustness

This is Moonfin's strongest part, and their own Kotlin, so we may copy it.

- **Policy only narrows** (`AudioPassthroughPolicy`, `PassthroughPolicyAudioSink`): the user's switches can veto a
  format the sink supports, but never force bitstreaming onto a route that cannot carry it. The veto happens in the sink
  (which the renderer asks before any decoder) and in offload support too. Matches ADR 0039. Worth adopting the sink
  wrapper so a veto demotes cleanly to decoding.
- **DTS-HD to a DTS-only receiver as its core** (`DtsCoreExtractor`): the DTS-HD extension data is cut from each access
  unit, so the track stays within the core bitrate.
- **Route-flap hold** (`RouteFlapHold`, 5 s): at playback start HDMI often drops for a few seconds. Media3 then either
  throws on the dead track and reselects against a sink with no capabilities, or a paused player reselects on its own.
  Both end with Atmos decoded to PCM. Hold the dead track and defer the capabilities loss; if the route returns inside
  the window nothing is forwarded.
- **Silent bitstream recovery** (`PassthroughSilenceRecovery`: 700 ms frozen position with unplayed data, 3 s grace at
  start, 3 s between recoveries): some TV audio HALs never resume a paused passthrough track. Rebuild it, with a short
  hold so the new track does not open while the old one is still tearing down.
- **Own IEC 61937 output** (`Iec61937AudioOutputProvider` with AC-3, DTS and TrueHD packers, a carrier probe cached per
  route): for devices where platform passthrough fails, the app packs the bitstream into an `ENCODING_IEC61937` track.
  It is large; keep it as a later step, only if the Streamer or a target device needs it.
- **Effects off the main thread** (`ExoPlayerAudioPipeline`): every audio-effect call goes through the audio server,
  which can stall for seconds while a passthrough track is set up. Run EQ and loudness calls on one background thread.
- **Device quirk table** (`KnownDefects`, jellyfin-androidtv / Wholphin): models with known DV/HDR10+ or E-AC-3-in-TS
  bugs. Keep our own small table, fed by `/test` results.

### 3.3 Offload and bit-perfect

- **Offload stall memory** (PixelPlayer): if offload stalled once on this exact build fingerprint (playWhenReady but
  not playing after 1.5 s), skip offload at startup on that build. An OS update re-tests it.
- **Early buffering is an offload reset** (PixelPlayer): `STATE_BUFFERING` within ~500 ms of audio playing, not
  explained by a seek or a transition, means the HAL reset offload. Turn offload off for the session.
- **No offload over Bluetooth A2DP / LE Audio** across transitions: it crashes the Bluetooth HAL on several devices
  (PixelPlayer). Offload is already off with EQ in our policy.
- **Bit-perfect** (PixelPlayer `BitPerfectUsbAudioOutputProvider` + `PcmWideningAudioOutput`): we already set
  `MIXER_BEHAVIOR_BIT_PERFECT`. Their addition is a custom `AudioOutputProvider` that widens float or 16-bit samples
  into the DAC's 24/32-bit integer container without changing any value. `DefaultAudioSink` only writes 16-bit or float,
  while USB DACs take integers. This could replace part of `AudioTrackReopener` (an output provider sees each
  `AudioTrack` creation, so no track has to be dropped and reopened). Only the active player may clear the preferred
  mixer.
- **Float probe** (PixelPlayer, Rhythm `HiFiCapabilityChecker`): `getMinBufferSize` and then a real `PCM_FLOAT`
  `AudioTrack`, cached. Some devices pass the first check and fail the second.
- **Hi-res cap** (PixelPlayer `HiResSampleRateCapAudioProcessor`): above 192 kHz, halve the rate before the sink. Some
  device paths hang at 352.8/384 kHz. Not on a bit-perfect route that takes the rate.

### 3.4 Loudness and DSP

- **ReplayGain done right** (Linthra `ReplayGain`, Finamp, jellyfin-androidtv): track or album gain with a fallback to
  the other, and a **peak guard** (`gain <= 1 / peak`). Negative gain through the player volume (works with offload).
  Positive gain through `LoudnessEnhancer` on the audio session, the only way to raise a quiet track, which takes the
  track off offload. Today we lose every positive gain.
- **EQ as a sink in front of the real sink** (PixelPlayer `EqualizerAudioSink`): it processes each PCM encoding at its
  own precision and forwards the buffer untouched while the EQ is flat, so output stays bit-exact. Our
  `DynamicsProcessing` works at the session; this is the alternative if we need EQ that is exact when neutral.
- **Surround downmix** (PixelPlayer): 5.1/7.1 PCM to stereo with the standard coefficients when the route is stereo
  (headphones, phone), instead of relying on each device's downmix.
- Rhythm's ViPER DSP is a whole effects suite in an `AudioProcessor`. Not in scope for a fidelity-first engine.

### 3.5 Crossfade and transitions (#219)

- **Two players** (PixelPlayer `DualPlayerEngine`, Rhythm `RhythmPlayerEngine`): A is the master the session sees, and
  B pre-buffers the next track and fades in. After the fade, A takes B's state. One shared audio-focus request covers
  both. Each player has its own audio processors, so a crossfade has no shared state.
- **Queue window** (PixelPlayer `QueueWindowPolicy`): B gets only a slice of a large queue, spliced back into the full
  queue after the swap, so it is never lost or saved truncated. Pure and unit tested.
- **Smart crossfade** (PixelPlayer `SmartCrossfadePlanner`): with BPM tags, tempos within 4 % (or half/double) blend for
  whole bars on S-curves. Missing or clashing tempo falls back to the plain crossfade, so it only refines a transition.
- **Pause and play fades in a wrapper player** (PixelPlayer `FadingPlayer`): the pause is reported at once, not when the
  fade ends, so a second tap inside the fade resumes. The fade target is read from the engine (ReplayGain writes there).
  Our `VolumeFader` delays the pause instead, which is the case they fixed.
- Crossfade never runs on bit-perfect or passthrough, and never while a sleep timer ends at the end of a track.
- This matches the plan already kept on branch `spike-219` (crossfade deferred until the engine work).

### 3.6 Buffering and network

- **Heap-aware byte budget** (Moonfin): `targetBufferBytes = maxHeap / 3`, capped at 384 MB and floored at Media3's
  minimum. Low-RAM devices keep the stock time budgets. Otherwise, for streaming video, a max buffer of 120 s.
- **Time over size** (Streamyfin, Rhythm): `setPrioritizeTimeOverSizeThresholds(true)`, so a reached byte target never
  stops loading below `minBufferMs`. Streamyfin saw buffering stay near 0 s at 20+ Mb/s without it. Add a back buffer
  retained from a keyframe for quick rewinds.
- Separate profiles: **music** (small, Rhythm 15/30/1.5/2.5 s) and **video** (large, byte-bounded). One player, so the
  `LoadControl` must read the current item's kind, or the video item gets its own source settings.
- **Redirect handling** (Streamyfin `UpgradeRedirectDataSource`): follow http to https upgrades that
  `HttpURLConnection` refuses. Never send the auth header to another origin. Our OkHttp source follows redirects
  already, but the rule about not forwarding the token cross-origin is worth checking.
- **Transfer log** (Moonfin `Media3TransferLog`): log the player's own HTTP requests (the first, gaps, slow ones). A
  transcode the server stopped producing is then visible.
- **Cache size**: Rhythm caches 150 MB, and ours is configurable (`streamingCacheGb`) and keeps original files only.
  Ours is the better design.

### 3.7 Music gapless

- Gapless works for direct-played MP3/AAC/FLAC with the right metadata (media3 skill, section 8). Clients that need
  transcoding (Finamp) accept gaps. Crossfade's second player also gives a gapless path for transcoded streams
  (pre-buffered B), which no client here exploits deliberately.

## 4. Video engine: ideas by topic

### 4.1 Display mode (refresh rate and resolution)

- **Rules** (Moonfin `DisplayModeChooser`, `FrameCadence`; ours already has the rate rules from #384).
- **Settle before audio** (Moonfin `DisplayModeSettle`): after asking for a mode, wait until the display and the audio
  devices are quiet for 1.5 s, at most 8 s. If the HDMI audio route drops (some TVs drop it 1-2 s after the switch),
  wait for it to return. Without this the audio track opens during the renegotiation and either fails or decodes.
  Wholphin's simpler version waits for `onDisplayChanged` (5 s timeout) and then 2 s more for a switch that is not
  seamless, as the Android frame-rate guidance says.
- **Audio-gated start** (`DisplayRateStartup`): hold audio until the rate decision and the settle are done, so the first
  second of sound is not lost or decoded.
- **Rebuild audio after a settled switch** (`AudioOutputRebuildPolicy`): if the route dropped during the hold, the
  platform may have silently recreated the `AudioTrack`, and its clock no longer matches. A seek in place while paused
  flushes it.
- **Keep the mode between videos** (`DisplayModeSwitchPolicy`): a video after a video keeps the mode when the rate
  wants the same one. Each switch is an HDMI renegotiation, a black screen and lost audio.
- **Measure the real cadence** (`FrameCadenceObserver` in the extractor): read frame timestamps a buffer ahead. A 3:2
  pulldown file stated as 23.976 really wants 59.94. MPEG-2 sequence headers give trusted rate and scan type
  (`Mpeg2RateHeaderObserver`).
- **Vsync pacing** (`VsyncPacer`): files with bunched timestamps put two frames on one vsync, and about a quarter of
  frames are lost at 50 Hz. Hold a frame one vsync at most, 6 times in a row at most.

### 4.2 HDR and Dolby Vision (first pass, Dolby's docs to come)

- **Profile 7 to 8.1** (Moonfin `DoviCompatExtractorsFactory` + `DoviRpu`; Wholphin `DolbyVisionProfile7Filter`):
  rewrite the RPU NAL (62) to profile 8.1 and drop the enhancement layer (63). For a dual-layer MKV, the EL and RPU sit
  in `BlockAdditional` elements, which the stock Matroska extractor discards (`MoonfinMatroskaExtractor` keeps them).
  Nothing is re-encoded, and the TV enters DV instead of HDR10 or a server transcode. Wholphin does it as a bitstream
  filter in the codec adapter. Moonfin does it in the extractor's track output, which also fixes the advertised format.
  The extractor way is cleaner for track selection.
- **HDR10+ masking** (Wholphin `Hdr10PlusSeiFilter`): when a file carries both DV and HDR10+, some (MediaTek) decoders
  latch on to HDR10+. Overwrite the SEI payload type of the HDR10+ messages with an undefined value; size unchanged.
- **Unsupported video detection** (`UnsupportedVideoPolicy`): Media3 leaves a DV AV1 or full-range PQ track unselected
  without an error, and audio plays over black. The only signal is "video exists but none selected". Report it and ask
  the server for a transcode.
- **Startup failure classification** (`VideoDecoderStartupFailure`): a decoder init or decoding error before the first
  frame, not DRM, not reclaimed, means "this device cannot play this stream". Fall back to a transcode at once instead
  of retrying.
- **Decoder capability tests** (Wholphin `MediaCodecCapabilitiesTest`, jellyfin-androidtv `deviceProfile.kt`): HEVC
  levels, DV profiles (including EL), HDR10/HDR10+ per codec, AV1, Hi10P. Our `VideoHdr` covers the HDR part; the level
  checks are worth comparing when the device profile is redone.
- **VC-1 alias** (Moonfin `Vc1AliasMediaCodecSelector`): some MediaTek TVs list their VC-1 decoder as `video/vc1`,
  which Media3 never matches.

### 4.3 Recovery

- **Decoder reclaimed** (`DecoderReclaimPolicy`): after a long pause the screensaver takes the codec. Re-prepare the
  same source at most twice, only while the player is live.
- **Resume wedge** (`ResumeWedgePolicy`): a resume that is still buffering after 3 s with 10 s of data loaded is stuck
  (Fire TV seen). Re-prepare at the same position. Not for live sources.
- **Audio clock corruption** (Moonfin): a reported position more than an hour past the player's age is a corrupted
  audio clock. Recover at most once a minute.
- **Keep the screen on only while playing** (Wholphin, already ours).

### 4.4 Containers and subtitles

- **Blu-ray TS** (`HdmvTsExtractor`): read DTS stream types only when the PMT carries the HDMV registration (they clash
  with SCTE-35 in broadcast).
- **zlib PGS in MKV**: let those tracks through, because Media3's PGS parser inflates them itself. Otherwise the whole
  file fails.
- **Trick-play variants** (`TrickPlayFreeHlsPlaylistParserFactory`): drop I-frame-only variants from a multivariant
  playlist, or a playlist listing one first plays with no audio.
- **Seekable downloaded transcodes** (`Media3Transmux`): a server transcode arrives as fragmented MP4 without `sidx`,
  which is unseekable. Pass it through `Transformer` into a normal MP4, with no re-encode.
- **libass** (jellyfin-androidtv, Wholphin, Moonfin): `ass-media` overlay renderer, OpenGL on API 29+, canvas below.
  Styled ASS subtitles as the author made them.
- **PGS** (Moonfin `SupSubtitleParser`), **subtitle offset** as a media source wrapper (`TimeOffsetMediaSource`,
  `SubtitleOffsetPolicy`, which is #456), and **two subtitle tracks at once** (`DualSubtitleTrackSelector`).
- **Constant-bitrate seeking** in extractors (jellyfin-androidtv): `setConstantBitrateSeekingEnabled(true)` for files
  without a seek table. Smaller TS timestamp search on low-RAM devices.

### 4.5 mpv as a second engine

Wholphin, Findroid and Streamyfin ship mpv (libmpv) as an alternative engine: every codec, ASS natively, `gpu-next`.
The cost is high. It is a separate player (no Media3 session, so our ADR 0047 single-player model breaks), there is no
Android Atmos passthrough through AudioTrack the way Media3 does it, and the native library adds tens of MB. Findroid
wraps mpv as a Media3 `Player` (`MPVPlayer`), which keeps the session. Verdict: not now. Media3 + FFmpeg + libass
covers what mpv would add, and passthrough stays Media3's strength.

## 5. Diagnostics (both engines)

- **Playback report** (Moonfin events, Streamyfin `TechnicalInfoOverlay`, Wholphin `PlaybackDebugOverlay`): the decoder
  in use, output encoding (PCM / passthrough / offload / bit-perfect), rate decision and its evidence, mode switch
  timings, buffer seconds and bytes, dropped frames. We have `PlaybackLogListener` and `VideoLogListener`. A structured
  "what the engine decided" record, shown in a hidden overlay (#510's sibling) and kept in the log, makes every
  `/test` finding explainable.

## 6. Proposal: the two milestones

Order inside each engine: things that make playback **correct** first, then **robust**, then **better**.

**Audio engine**

1. Engine baseline: renderers factory with the FFmpeg extension, order by output (ON / PREFER), decoder fallback,
   DTS to FFmpeg, silent-clock guard. ADR for the FFmpeg build (prebuilt Media3 module vs own build, which formats).
2. LoadControl per kind: music profile and heap-aware video profile, time over size, back buffer.
3. Passthrough robustness (#377): policy sink, route-flap hold, silent-track recovery, effects off the main thread,
   DTS-HD as core.
4. ReplayGain v2: peak guard, positive gain via `LoudnessEnhancer`, the offload interplay in `AudioPolicy`.
5. Offload safety: stall memory per build, early-buffering reset detection, no offload over Bluetooth transitions.
6. Hi-res and bit-perfect: float probe, >192 kHz cap, widening output provider (maybe replaces the reopen).
7. Crossfade (#219): two-player engine with queue window and fading wrapper, after 1-6.
8. Surround downmix for stereo routes.
9. Later, only if a device needs it: own IEC 61937 output.

**Video engine**

1. Mode switch done right: settle before audio, audio-gated start, rebuild after a dropped route, keep the mode between
   videos.
2. Recovery: decoder reclaimed, resume wedge, startup failure to transcode, unsupported video detection, audio clock.
3. Real cadence: frame-timestamp observer, MPEG-2 headers, vsync pacer.
4. Dolby Vision: profile 7 to 8.1 (extractor way, MKV block additions), HDR10+ masking. Finalized after Dolby's docs.
5. Containers: HDMV TS DTS, zlib PGS, trick-play-free HLS, constant-bitrate seeking, seekable downloaded transcodes.
6. Subtitles: libass, PGS, offset (#456), two tracks.
7. Device profile v2: codec level tests and a quirk table, compared with the official client's.
8. Diagnostics: the engine decision record and overlay (shared with audio).

## 7. Owner decisions (2026-11-24)

1. **FFmpeg**: the prebuilt Media3 FFmpeg decoder module Jellyfin publishes (as jellyfin-androidtv and Wholphin use),
   not an own build.
2. **Moonfin's code**: copy their own GPL-3.0 Kotlin (passthrough robustness, Dolby Vision conversion, display mode
   settle and the recovery policies), adapted to our style and tests, with attribution in `THIRD_PARTY.md`. Check each
   file's origin first, because some come from jellyfin-androidtv (GPL-2.0).
3. **Crossfade**: two players inside, A and B, behind one session player (as PixelPlayer and Rhythm do). ADR 0047 is
   updated with that issue.
4. **ReplayGain**: raise quiet tracks too (`LoudnessEnhancer`) with the peak guard. A track that is raised leaves
   offload; the others keep it.
