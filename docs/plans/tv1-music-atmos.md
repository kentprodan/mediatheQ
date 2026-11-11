# Plan: TV 1 · Music + Atmos

- Status: Proposed (owner approval pending)
- Date: 2026-11-11
- Milestone: `TV 1 · Music + Atmos` (ADR 0025, order 4)
- Issue drafts: [tv1-issues.md](tv1-issues.md). ADRs: 0037-0041 (Proposed).

## 1. Goal

A Compose for TV music app for the owner's Google TV Streamer that plays the Jellyfin music library and sends Dolby
Atmos music (E-AC-3 JOC in M4A, TrueHD in MKA) **untouched** over HDMI: Streamer -> LG TV -> eARC -> LG DSN11RG. The
soundbar's display shows "Dolby Atmos" while such a track plays. Everything else (stereo FLAC, MP3, AAC, Opus) plays as
it does on the phone.

Exit criterion (last issue): the Atmos chain is verified on hardware for both formats, and the owner has used the TV
app for a week of listening.

## 2. What exists and what is reused

| Module | Reused on TV | Notes |
|---|---|---|
| `:core:model`, `:core:common`, `:core:navigation` | as they are | `NavKey`s carry no UI; the TV app builds its own back stack from them (ADR 0041). `OutputKind` gains `Hdmi` (0039). |
| `:core:network`, `:core:server`, `:provider:jellyfin` | as they are | one new contract fixture: `PlaybackInfo` request with `eac3,truehd` direct play. |
| `:core:database`, `:core:datastore`, `:core:data` | as they are | accounts, sync, library, queue, resumes, audio profiles all work on TV unchanged. |
| `:core:playback` | reused, extended | HDMI route, passthrough capabilities, `AudioMode.Passthrough`, Atmos stream rules (issues 9-10). The phone keeps `AudioRoute.Other` and is unaffected. |
| `:core:download` | wired, no UI | `:core:playback` needs `@DownloadCache`; the TV app has no Downloads screen in TV 1. |
| `:core:designsystem`, `:core:ui` | **not** reused | they are Material 3 Expressive (phone). Google says not to mix the mobile `MaterialTheme` with `androidx.tv.material3` ([Compose for TV](https://developer.android.com/training/tv/playback/compose)). TV gets `:core:designsystem-tv` and `:core:ui-tv` (ADR 0037, 0038). |
| `:feature:*` | **not** reused | phone screens; TV screens live in `:feature-tv:*`. ViewModels are small and are written again against the same repositories (ADR 0040). |
| `:app` | not touched | the TV app is `:app-tv` (ADR 0038). |

Pure Kotlin helpers in `:core:ui` that TV needs too (`DurationFormat`, `NameOrder`, `QualityLabel` text) move to
`:core:common` in the skeleton issue, so both UI modules read them from one place. Nothing else moves.

## 3. Research summary (official sources, checked 2026-11-11)

### Compose for TV

- `androidx.tv:tv-material:1.1.0` is **stable** (2026-05-06); `androidx.tv:tv-foundation:1.0.0` is stable too (2026-05-06) but its lazy layouts (`TvLazyRow`, `TvLazyColumn`, grids, `pivotOffsets`) are deprecated and were removed in 1.0.0-alpha12; the lists guide says to use Compose Foundation `LazyRow` / `LazyColumn` / `LazyVerticalGrid` (1.7.0+) with `LocalBringIntoViewSpec` for a pivot. Sources: [androidx.tv releases](https://developer.android.com/jetpack/androidx/releases/tv), [Create scrollable layouts](https://developer.android.com/training/tv/playback/compose/lists). We do not depend on `tv-foundation`.
- tv-material 1.0 made stable: `MaterialTheme`, `ColorScheme`, `Typography`, `Shapes`, `Surface`, `Border`, `Glow`, `Button`s, `Card`s, `ListItem`, `NavigationDrawer`, `Tab`/`TabRow`, `Switch`, `Checkbox`, `RadioButton`, `Icon`, `Text` (release notes 1.0.0-beta01). `ImmersiveList` was removed; `Carousel` exists. No search bar, no dialogs of its own (use Compose `Dialog`).
- Google's own setup snippet uses the same `compose-bom:2026.09.00` and `activity-compose:1.13.0` the project already pins, so no BOM change is needed.
- Focus: Compose focus APIs (`focusRequester`, `focusRestorer()` on containers, `focusGroup()`, `focusProperties`), `LaunchedEffect(Unit) { focusRequester.requestFocus() }` for initial focus ([Change focus behavior](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior)). Lazy lists bring the focused item into view by themselves.
- Manifest ([Get started with TV apps](https://developer.android.com/training/tv/start/start)): the TV activity has `<category android:name="android.intent.category.LEANBACK_LAUNCHER" />`; `<uses-feature android:name="android.software.leanback" android:required="false" />`; `<uses-feature android:name="android.hardware.touchscreen" android:required="false" />`; `android:banner` = xhdpi 320 x 180 px with the app name in the image; `android:colorPrimary` tints the launch animation; the SplashScreen API is not supported on TV. Compose for TV needs API 21+; our minSdk 31 (ADR 0010) is fine.

### Media3 and passthrough

- Media3 stable is still `1.11.1` (2026-09-10); `1.12.0-alpha02` exists (2026-10-26). 1.11.0 removed the E-AC-3 JOC -> E-AC-3 decoder fallback on Pixel; 1.12.0-alpha01 adds `dec3` (E-AC-3 / E-AC-3 JOC) box writing to `Mp4Muxer` and a Fire TV AC-3 workaround. Nothing in 1.9-1.12 changes passthrough rules. Source: [media3 releases](https://developer.android.com/jetpack/androidx/releases/media3). We stay on 1.11.1.
- `DefaultAudioSink` does passthrough by itself when `AudioCapabilities` says the route supports the encoding; no custom sink is needed (media3 skill, section 2). `AudioCapabilities.getCapabilities(context, audioAttributes, routedDevice, spatializerChannelMasks)` is the current factory (the shorter overloads are deprecated); `isPassthroughPlaybackSupported(format, audioAttributes)`, `supportsEncoding(C.Encoding)`, `getEncodingAndChannelConfigForPassthrough(format, audioAttributes)`. All `@UnstableApi`. Source: [AudioCapabilities](https://developer.android.com/reference/androidx/media3/exoplayer/audio/AudioCapabilities).
- `AudioCapabilitiesReceiver(context, listener, audioAttributes, routedDevice)` with `register()` / `unregister()` / `setRoutedDevice()` notifies on capability changes (HDMI plug, routing). Source: [AudioCapabilitiesReceiver](https://developer.android.com/reference/androidx/media3/exoplayer/audio/AudioCapabilitiesReceiver).
- Whether passthrough **really** happened is visible in `AudioSink.AudioTrackConfig.encoding` reported through `AnalyticsListener.onAudioTrackInitialized` (fields `encoding`, `sampleRate`, `channelConfig`, `tunneling`, `offload`, `bufferSize`). The service already listens to it for bit-perfect. Source: [AudioSink.AudioTrackConfig](https://developer.android.com/reference/androidx/media3/exoplayer/audio/AudioSink.AudioTrackConfig).
- Platform constants: `AudioFormat.ENCODING_E_AC3_JOC` (API 28, value 18), `ENCODING_E_AC3` (21), `ENCODING_DOLBY_TRUEHD` (25, value 14), `ENCODING_DOLBY_MAT` (29, "used to transmit Dolby TrueHD ... over HDMI (e.g. Dolby Atmos content)"), `ENCODING_IEC61937` (24). "E-AC-3-JOC streams can be decoded by downstream devices supporting ENCODING_E_AC3." Source: [AudioFormat](https://developer.android.com/reference/android/media/AudioFormat). Device types: `AudioDeviceInfo.TYPE_HDMI` (API 23, 9), `TYPE_HDMI_ARC` (23, 10), `TYPE_HDMI_EARC` (31, 29); `getAudioProfiles()` (31), `getEncodings()` (23). Source: [AudioDeviceInfo](https://developer.android.com/reference/android/media/AudioDeviceInfo).
- `AudioManager.getDirectProfilesForAttributes(attributes)` and `getDirectPlaybackSupport(format, attributes)` (both API 33; return `DIRECT_PLAYBACK_BITSTREAM_SUPPORTED` = 4 for passthrough) report the **currently routed** device; the TV audio guide says to query again on every output change and warns that writing a no-longer-supported format gives "Error -32" before the routing callback arrives. Sources: [AudioManager](https://developer.android.com/reference/android/media/AudioManager), [Audio capabilities on TV](https://developer.android.com/training/tv/playback/audio-capabilities), [AudioProfile](https://developer.android.com/reference/android/media/AudioProfile).
- Now Playing card: shown by the launcher "whenever an active MediaSession is present"; selecting it starts the `PendingIntent` from `setSessionActivity()` ([Now Playing card](https://developer.android.com/training/tv/playback/now-playing)). Media3's `MediaSession.Builder.setSessionActivity(PendingIntent)` exists ([MediaSession.Builder](https://developer.android.com/reference/androidx/media3/session/MediaSession.Builder)); our service already sets it from `getLaunchIntentForPackage`, so the TV activity must carry the `LAUNCHER` category next to `LEANBACK_LAUNCHER` (Google's own snippet does this) or the service gets `null`.
- Google TV Streamer: 4 GB memory, 32 GB storage, HDMI 2.1, Ethernet, Dolby Digital / Digital Plus / Atmos ([specs](https://support.google.com/chromecast/answer/3046409), [store](https://store.google.com/us/product/google_tv_streamer_specs?hl=en-US)); Android TV 14 / API 34 per ADR 0010 (not re-verified online). Google states Atmos can be decoded **on the device**, so the Streamer most likely also has an `audio/eac3-joc` decoder: with no passthrough route the existing decoder rule gives direct play + PCM instead of a server transcode.

### Jellyfin 12 (OpenAPI 12.3.0 copy in `provider/jellyfin/api/`)

- Quick Connect: `POST /QuickConnect/Initiate` (no params) -> `QuickConnectResult { Authenticated, Secret, Code, DeviceId, DeviceName, AppName, AppVersion, DateAdded }`; poll `GET /QuickConnect/Connect?secret=`; `POST /Users/AuthenticateWithQuickConnect` with `QuickConnectDto { Secret }`; `GET /QuickConnect/Enabled`. Already implemented in `SessionRepository.startQuickConnect` / polling (`:feature:onboarding`); the TV flow reuses the repository unchanged.
- DeviceProfile: `DirectPlayProfile { Container, AudioCodec, VideoCodec, Type }`, `TranscodingProfile { ..., MaxAudioChannels, Protocol, ... }`, `CodecProfile { Type, Conditions, ... }`, `MusicStreamingTranscodingBitrate` (R11). `MediaStream.AudioSpatialFormat` is `None | DolbyAtmos | DTSX`. Passthrough device: direct play `eac3` and `truehd` in `m4a,mp4,mka`, no bitrate cap for such tracks (jellyfin skill, section 5; never transcode Atmos when passthrough is possible).

## 4. Module plan

```
:app-tv                       TV application: TvActivity (LEANBACK_LAUNCHER + LAUNCHER), Hilt graph, NavDisplay host,
                              drawer shell, banner; applicationId io.github.kentprodan.mediatheq.tv
:core:designsystem-tv         MediatheqTvTheme (tv-material ColorScheme from the Iris seed, same fonts), MqTv* components,
                              @MqTvPreviews + fixtures. No domain types.
:core:ui-tv                   domain-aware TV composables (MqTvAlbumCard, MqTvTrackRow, format badge) = designsystem-tv + model
:feature-tv:onboarding        server address, Quick Connect (first), password fallback
:feature-tv:home              rows: recently added, recently played, favorites, mixes
:feature-tv:library           albums / artists / playlists / genres tabs, A-Z grid
:feature-tv:album             album page and playlist page (same track-list layout)
:feature-tv:artist            artist page
:feature-tv:nowplaying        full-screen player, queue, lyrics
:feature-tv:search            on-screen keyboard search over FTS
:feature-tv:settings          Dolby Atmos per HDMI output, quality, account, licenses
```

- New convention plugin `mediatheq.android.feature.tv` in `build-logic`: `:core:model`, `:core:common`, `:core:navigation`, `:core:data`, `:core:playback` (controller API), `:core:designsystem-tv`, `:core:ui-tv`, lifecycle + hilt viewmodel, `:core:testing` for tests. Same forbidden edges as `:feature:*` plus: `:feature-tv:*` never depends on `:core:designsystem` / `:core:ui` / `:feature:*`, and `:feature:*` never on the TV modules.
- `:app-tv` applies `mediatheq.android.application`, `mediatheq.android.hilt`, `mediatheq.android.screenshot`, `aboutlibraries` (licenses, ADR 0022). No `:baselineprofile` for TV (owner rule: no performance work before beta).
- Core changes (all behind the existing pure functions, phone behavior unchanged):
  - `:core:model`: `OutputKind.Hdmi`; `OutputId` `hdmi:<hash of product name>`.
  - `:core:playback`: `AudioRoute.Hdmi`; `AudioMode.Passthrough`; `PassthroughCapabilities` (fun interface, snapshot of MIME types the sink can pass through, from Media3 `AudioCapabilities`); `AudioOutputState.passthroughEncoding`; `AudioOutputInfo.atmosPassthrough`; `DeviceProfileBuilder.build(decoders, passthrough, quality)`; `StreamResolver` asks for the original of an Atmos track whenever passthrough exists; `AudioCapabilitiesReceiver` registered in the service.
  - `:core:data`: `OutputProfile.atmosPassthrough` (default on), only shown for HDMI outputs.
  - `:core:common`: `DurationFormat`, `NameOrder`, quality-label text moved from `:core:ui`.
- CI: unchanged workflow. `assembleDebug`, `testDebugUnitTest`, `validateDebugScreenshotTest` and `lint` run without a module prefix, so the new modules are covered the day they exist.

## 5. Passthrough strategy (ADR 0039, short form)

1. **One source of truth for capabilities:** Media3's `AudioCapabilities` for the routed device and our media attributes. `DefaultAudioSink` decides passthrough from the same class, so what we tell Jellyfin and what the sink does cannot disagree. Snapshot it into plain data (`PassthroughCapabilities.mimeTypes()`), like `AudioDecoders`.
2. **DeviceProfile:** `eac3` is declared when the route passes E-AC-3 JOC through **or** the device has a JOC decoder (today's rule); `truehd` only with TrueHD passthrough. Containers add nothing new (`m4a`, `mp4`, `mka` already listed).
3. **Never transcode Atmos on a passthrough route:** `StreamResolver` asks with no bitrate cap for a `SpatialFormat.DolbyAtmos` track when passthrough covers its codec, whatever the network quality setting says; a pure function `atmosRequest(track, passthrough, quality)` decides it.
4. **AudioPolicy:** `Passthrough` when the route is HDMI, the output profile allows it and the playing track's codec is in the passthrough set: offload off (per route, as today, when any passthrough codec is available), EQ off, normalization off, volume 1, no fades. Rules already in the media3 skill (sections 5, 7).
5. **Truth on screen:** `onAudioTrackInitialized` with `encoding` E-AC-3 JOC or TrueHD -> `atmosPassthrough = true`; the badge shows only then (skill rule 4). A JOC track decoded to PCM (route without passthrough, JOC decoder present) shows "Dolby Atmos not available · Decoded on this output"; a server conversion keeps today's "Playing in stereo on this output".
6. **Route changes:** `AudioCapabilitiesReceiver` -> next track re-selects its stream (skill rule 3); `DefaultAudioSink` already recreates its `AudioTrack` for the current one. An `AudioSink` write error on a passthrough track drops to a re-prepare of the item (same path as the bit-perfect `-32` handling).
7. **Fallback when nothing passes through** (Bluetooth headphones on the Streamer, TV speakers without eARC): rule 2 falls back to the JOC decoder, else the server converts to stereo Opus 256 (today's phone path). Never silent: Now Playing states the reason.

## 6. Risks

| Risk | Why | Mitigation |
|---|---|---|
| The Streamer reports E-AC-3 JOC passthrough but the TV or eARC chain re-encodes or drops to DD+ 5.1 | the chain has three devices; Android's "Surround sound: Auto" decides from the TV's EDID, not the soundbar's | manual test with the soundbar display; test issue lists Streamer Settings -> Display & sound -> Audio -> Surround sound = Auto, TV eARC on, TV "Digital sound out: Pass-through"; if JOC shows as "Dolby Digital Plus" only, try `Manual` with Atmos ticked |
| TrueHD over eARC fails or stutters | TrueHD passthrough is rarer than E-AC-3 JOC; Media3 rechunks TrueHD samples for passthrough; the TV must pass MAT | E-AC-3 JOC is the acceptance criterion; TrueHD is a second manual item, failure becomes a bug, not a blocker |
| Media3 `AudioCapabilities` is `@UnstableApi` and `DefaultAudioSink` internals may change | 1.12 is in alpha | the snapshot interface keeps the API in one class; `MediatheqPlaybackService` is the only caller |
| Material-only components missing in tv-material (search bar, dialogs, sliders) | tv-material is small | Compose `Dialog`, `BasicTextField`; a seek bar is a focusable progress row with left/right D-pad seeking |
| Screenshot references for TV rendered on Windows differ from CI | same as phone | CI is canonical (ADR 0007) |
| Two apps from one repo doubles some work (settings, licenses, onboarding) | different toolkits | TV 1 keeps settings minimal; licenses list reuses the AboutLibraries JSON |
| `getLaunchIntentForPackage` returns `null` on TV | needs a `LAUNCHER` category | TV activity declares both categories (Google's snippet) and a device test asserts the session activity |

## 7. Decisions (owner answers 2026-11-11: every recommended option, plus lyrics in TV 1)

1. **TV UI toolkit:** `androidx.tv:tv-material:1.1.0` + Compose Foundation lazy lists (recommended) / Material 3 Expressive with our own focus handling / Leanback. ADR 0037.
2. **Separate application id** `io.github.kentprodan.mediatheq.tv` (recommended: a TV box and a phone never share an install, the phone manifest stays untouched, the Jellyfin device list shows it apart) / same id with a `LEANBACK_LAUNCHER` activity inside `:app`. ADR 0038.
3. **Own TV design system** `:core:designsystem-tv` from the same Iris seed (recommended) / reuse `:core:designsystem` tokens and wrap tv-material around them. ADR 0037.
4. **Passthrough capabilities from Media3 `AudioCapabilities`** (recommended) / platform `AudioManager.getDirectProfilesForAttributes` (API 33) with `AudioDeviceInfo.getEncodings()` below. ADR 0039.
5. **Dolby Atmos passthrough is a per-HDMI-output switch, default on** (recommended, fits ADR 0032) / a global "Prefer Dolby Atmos: Automatic / Off" setting as the media3 skill once sketched.
6. **TV onboarding is its own feature with its own ViewModels** (recommended) / extracting shared ViewModels into a module both apps depend on. ADR 0040.
7. **Now Playing is a destination on the TV back stack plus a strip in the drawer footer** (recommended) / an overlay like the phone's mini player. ADR 0041.
8. **No downloads, no offline mode, no equalizer, no lyrics settings, no video on TV 1** (recommended) / lyrics display is kept as the last, optional issue. **Owner: lyrics on TV Now Playing are in TV 1 (T13, not optional).**
9. **TV screenshot variants:** 1080p, dark theme, font scale 1.0 and 1.3 (recommended; TV apps are dark and 2.0 breaks every 10-foot layout) / the phone's light + dark, 1.0 + 2.0. Needs a testing-skill change.

## 8. Open questions for the owner

1. Answered: eARC (not ARC) is active between the TV and the DSN11RG and the TV offers "Pass-through".
2. Answered: no account switcher on TV in TV 1 (one account; the switcher comes later).
3. Answered: lyrics are in TV 1 (T13).
4. The Jellyfin `Client` name in the auth header: keep `mediatheQ` (device model distinguishes the box) or `mediatheQ TV`? Plan: keep `mediatheQ` (one interceptor, no change).
5. Should "Prefer original even on a capped quality" apply to **all** tracks on the TV (Ethernet is unmetered, so Original is the default anyway)? Plan: no, only the Atmos rule.

## 9. Out of scope (TV 1)

Video of any kind (TV 2 · Video), downloads and offline mode on TV, equalizer and bit-perfect on TV, Android Auto /
Cast / Wear, a second provider, a TV baseline profile or macrobenchmarks, voice search, Google TV "Continue watching"
channel integration, account switching UI, Bluetooth lyrics on TV, performance measurements.

## 10. Skills and ADRs that would need to change (not edited here)

- `architecture` skill: module map and dependency rules gain `:app-tv`, `:core:designsystem-tv`, `:core:ui-tv`, `:feature-tv:*` and the `mediatheq.android.feature.tv` plugin; section 6 gets the TV back stack; section 12 "where code goes" gets TV UI.
- `media3` skill: section 4 (passthrough rules become code: `PassthroughCapabilities`, `AudioMode.Passthrough`, `atmosRequest`), section 1 (`AudioCapabilitiesReceiver` in the service), section 7 (`AudioRoute.Hdmi`), section 13 manual tests.
- `jellyfin` skill section 5: `eac3` / `truehd` direct play on a passthrough route; new fixture `items_playbackinfo/passthrough_atmos.json`.
- `testing` skill: TV screenshot variants (`@MqTvPreviews`), device tests on the Streamer (`adb connect` over Tailscale, `:app-tv:connectedDebugAndroidTest`), the Atmos manual items.
- `expressive-design` skill: states that it applies to the phone; TV components follow tv-material and ADR 0037 (or a new `tv-design` skill with the component map of `:core:designsystem-tv`).
- ADR 0032 (per-output profiles): amendment for the `hdmi:` output id and the Atmos passthrough field.
- ADR 0010: unchanged (minSdk 31 covers the Streamer).
