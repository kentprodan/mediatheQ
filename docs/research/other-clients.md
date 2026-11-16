# Other Jellyfin clients: what mediatheQ can learn

Research notes (2026-11-16). Ideas only: no code is copied (JellyBox is AGPL-3.0 and Dart; mediatheQ is GPL-3.0).

## 1. JellyBox (github.com/avdept/JellyBoxPlayer)

Flutter, music only, Jellyfin / Emby / Navidrome; desktop, iOS, Android, Android TV (early), CarPlay and Android Auto
(beta). ~74k lines of code, ~47k lines of tests, no UI screenshot tests. Part of it is closed: the private
`optional_features` package (genre mixes, UPnP/Sonos quirks, cross-device handoff, external artist catalogue); the
public repo ships inert stubs.

Already in mediatheQ, same or better: album/playlist resume, mixes, quality badge, offline mode, id batching (they fixed
long id lists on 2026-11-14), Quick Connect, video, Atmos, screenshot tests, layered architecture.

### Worth taking, by value

1. **Sleep timer** (we have none): after N tracks, after N minutes (1-60), or at a clock time; the last choice is
   remembered; "tracks" counts track changes.
2. **Start radio (Instant Mix)** from a track, album or artist: Jellyfin `GET /Items/{id}/InstantMix`, Subsonic
   `getSimilarSongs`.
3. **Similar albums** on the album page: `GET /Albums/{id}/Similar`.
4. **LRCLIB lyrics fallback** when the server has none: exact `api/get` (title, artist, album, duration) first, then
   `api/search` accepting only a duration within 5 s; synced preferred, instrumental skipped; 3 retries on transient
   errors only (timeouts, 429, 5xx), 404 = none. A setting, since title and artist leave for lrclib.net.
5. **Several addresses per server** (home LAN + remote/Tailscale + relay): all probed in parallel (4 s timeout), the
   first that answers wins; a 30 s heartbeat while fallbacks exist; a request error fails over at once; offline only
   after a second probe 2 s later also fails (no flapping).
6. **ListenBrainz and Last.fm scrobbling**: a listen tracker counts time actually heard (steps <= 10 s, so seeks do not
   count), threshold min(half, 4 min), a jump back under 5 s starts a new listen; a persistent queue per service (cap
   500), retry 1 -> 10 min backoff, flush when back online, 401 disconnects.
7. **Navidrome**: `reportPlayback` when the `playbackReport` extension exists, else `scrobble` (our plan's open
   question 3: they chose yes); the extension list is fetched once, concurrent callers share the request, a failure is
   retried only after an interval; rating sort emulated (rated albums first, then the rest).
8. **Android Auto**: root Home (grid) / Library (list) / Downloads (grid); a "Sign in on your phone" item without an
   account, an "offline, downloads available" item without a server; custom actions shuffle / repeat / favorite; covers
   through an own `ContentProvider` (Auto cannot fetch authenticated http). Voice search: honour `EXTRA_FOCUS`
   (artist / album / playlist), then an exact title match, then a song, then anything; an empty query resumes or
   plays a mix.
9. **Cast / UPnP**: each output target carries its own codec profile, the server converts for it (fits ADR 0032 and
   Media3 `CastPlayer`); per-device UPnP fixes are in their closed code.
10. **Google TV home row** "Recently added": a `PreviewChannel` of 10 albums, republished only when the list changes;
    a tap opens the album.

Smaller ideas: home screen widget (now playing, like / shuffle / repeat); "On repeat" (most played in 30 days from a
local play log); pins on Home that expire after 7 / 14 / 30 days; LAN discovery at onboarding (UDP 7359); Cover Art
Archive for missing covers; text-to-music search through the AudioMuse-AI server plugin.

Not worth it: Discord presence, Lidarr, telemetry (Sentry, Plausible), animated covers from a third-party server
(leaks library data), Studio Mode.

Code lessons: an injected clock everywhere; identical in-flight requests merged into one; transient vs permanent errors
told apart before any retry. Not to copy: almost no comments, global static state (`ScrobbleHandler`,
`TvHomeChannel`), no UI tests.

## 2. Moonfin (github.com/Moonfin-Client/Moonfin-Core)

Flutter, everything for everyone: Jellyfin, Emby, Plex, Silo; phone, desktop, Android TV, Fire TV, tvOS, web; video,
music, books, audiobooks, retro games, Live TV, Seerr, admin panel, themes store, watch parties, Kids Mode. ~475k lines
of Dart (without translations), plus a companion server plugin "Moonbase" (C#) for settings sync, smart search,
calendar and watch parties. **GPL-3.0**, the same license as mediatheQ: its own Kotlin can be reused with attribution
in `THIRD_PARTY.md`. Some files come from jellyfin-androidtv (GPL-2.0), e.g. `AudioAttributeState.kt`, so check where
each file came from before copying.

The valuable part is the native Android player, `packages/moonfin_native_video` (~29k lines of Kotlin on Media3, with
JVM tests). Every decision is a small class with an injected clock, tested on its own:

- **Display mode switching** (`DisplayModeChooser`, `FrameCadence`, `DisplayModeSwitchPolicy`):
  - 1000/1001 rates are told apart from whole ones (0.1 %), and loosely stated rates are snapped to the standard.
  - 25 and 29.97 content prefers twice its rate, so a mix of 29.97 and 59.94 never switches; film keeps its own rate.
  - 60 Hz is never used for 23.976, since the 2.5 ratio is 3:2 judder.
  - The real cadence is read from frame timestamps, so a 3:2 pulldown file stated as 23.976 goes to 59.94.
  - Between two videos that want the same mode, the mode is not handed back (each switch re-negotiates HDMI and drops
    audio).
- **Dolby Vision profile 7 -> 8.1** (`DoviCompatExtractorsFactory`, `DoviRpu`): on a device whose DV decoder lists
  profile 8 but not 7, the RPU is rewritten and the enhancement layer dropped. Nothing is re-encoded, and the display
  goes into DV instead of HDR10.
- **Passthrough robustness**, also useful for TV 1 Atmos music:
  - `RouteFlapHold`: when HDMI drops for a few seconds at playback start, the dead track is held instead of letting
    Media3 fall back to decoding.
  - `PassthroughSilenceRecovery`: some TV audio HALs never resume a paused bitstream track; this detects a frozen
    position while there is unplayed data and rebuilds the track.
  - `AudioPassthroughPolicy`: user toggles only narrow what the sink supports, never force bitstreaming; DTS-HD goes out
    as its DTS core when only DTS is allowed.
  - Own IEC 61937 packers (AC-3, DTS, TrueHD) for devices where platform passthrough fails.
- **Recovery after a long pause**:
  - `DecoderReclaimPolicy`: the screensaver took the decoder, so the source is prepared again, at most twice.
  - `ResumeWedgePolicy`: on Fire TV-like devices a resume that sits buffering with data loaded gets a re-prepare at the
    same position.
- `UnsupportedVideoPolicy`: Media3 leaves a DV AV1 track unselected with no error, so audio plays over a black screen;
  the track selection is the only signal, and it is checked.
- Subtitles: two subtitle tracks at once, ASS overlay, PGS (`SupSubtitleParser`), offsets.
- **Smart downloads** (`remove_watched_planner.dart`):
  - Keep the next episodes of followed series downloaded; delete watched ones automatically.
  - Only downloads made by hand are judged; a download is deleted only once the server says it was played, `deleteAfter`
    has passed, and the play happened after the download and after the setting was turned on.
  - Never the item now playing, never items or series on a keep list; music and books are never removed.
- TV: Watch Next and preview channels (`WatchNextPublisher`, `PreviewChannelPublisher`); client certificates (mTLS)
  sent from a phone to the TV; remote control of other Jellyfin sessions.

Not worth it: the kitchen sink (games, books, Seerr, admin panel, themes, watch parties, nine detail styles).

## 3. Wholphin (github.com/damontecres/Wholphin)

Android TV only, Jellyfin, video + music; Kotlin, Compose for TV, Media3 1.11, Hilt, Room: the same stack as our
`app-tv`. Optional MPV engine and own builds of the Media3 ffmpeg and AV1 decoders. ~107k lines, 59 test files.
**GPL-2.0** (only, per SPDX): ideas only, no code.

- **Hold-to-seek acceleration** (fixes our #353): the multiplier grows with `repeatCount / 3` (device repeat rates
  differ) and with the item's length: up to x2 under 30 min, x4 under 90 min, x6 under 150 min, x10 beyond. One shared
  function serves the seek bar and the key handler.
- **Screen kept on only while playing** (fixes our #355): `keepScreenOn(isPlaying)` from the player listener; pause
  releases `FLAG_KEEP_SCREEN_ON`, so the system screensaver runs. An optional in-app screensaver and dimming come on top.
- **Refresh rate and resolution switching** (`RefreshRateService`): pick a mode from `display.supportedModes`, call
  `preferredDisplayModeId`, wait for `onDisplayChanged` (5 s timeout), then 2 s more when the switch is not seamless
  (Android guidance); reset to mode 0 when playback ends. Simpler than Moonfin's.
- Dolby Vision profile 7 -> 8.1 (`DolbyVisionProfile7Filter` and `DolbyVisionProfile7ExtractorsFactory`, same idea as
  Moonfin); HDR10+ SEI filtering.
- Watch Next row from Next Up (`TvProviderWorker`); pass-out protection (stop auto-play after N hours without a key);
  skip back a few seconds on resume; theme music on detail pages; subtitle search and download through the server's
  OpenSubtitles plugin; per-profile PIN.

## 4. Official Jellyfin clients

**jellyfin-android** (phone, GPL-2.0): a WebView over jellyfin-web, with a native ExoPlayer for video and native
downloads. Little to learn for a native app, except the **Android Auto browser** (`sessionbrowser/`, ~700 lines on
`MediaLibraryService`): media ids are `@Serializable` routes (`Root`, `Suggested`, `Recent`, `Search`, `Library`,
`AlbumsAlpha`, `Albums(startLetter)`, ...), and large libraries are split by first letter so the car's list limits are
never hit.

**jellyfin-androidtv** (TV, GPL-2.0): mostly Leanback with some Compose, a separate `playback` module (core / jellyfin
/ media3).
- **System screensaver** (`LibraryDreamService`, a `DreamService`): shows what is playing, or a showcase of library
  backdrops and logos, or the logo. For music on TV, now playing as the system screensaver is a natural fit.
- **Global search** on Android TV (`MediaContentProvider` with `SearchManager` suggestions).
- **Remote control over the WebSocket** (`PlaySessionSocketService`): Playstate commands (pause, unpause, next,
  previous, seek, stop) and volume commands sent from another client.
- Leanback home channel (`LeanbackChannelWorker`), queue suppliers for album, instant mix and episodes, media segments.

## 5. Verdict

- None of the four is what mediatheQ is (a native Android phone + Google TV app, music and video, Material 3
  Expressive, Atmos chain): JellyBox is music in Flutter, Moonfin is everything in Flutter, Wholphin is TV video only,
  the official phone app is a WebView. The direction stays; nothing to fork or switch to.
- Code that may be copied: Moonfin's own Kotlin (GPL-3.0, check each file's origin). Wholphin and the official
  clients (GPL-2.0) and JellyBox (AGPL-3.0, Dart): ideas only.
- Order of what to take:
  1. Now (open TV bugs): #355 keep the screen on only while playing (Wholphin); #353 hold-to-seek acceleration
     (Wholphin).
  2. TV 1 hardening (the Atmos music app on the Streamer): HDMI route flap hold and passthrough silence recovery
     (Moonfin); decoder re-prepare after the screensaver (Moonfin).
  3. Mobile 2 · Polish: sleep timer, Start radio (Instant Mix), similar albums, LRCLIB lyrics (JellyBox).
  4. TV 2 · Video: display mode switching (Moonfin rules, Wholphin waiting), Dolby Vision 7 -> 8.1, unsupported video
     detection, Watch Next and a "Recently added" channel, now playing as the system screensaver, global search,
     pass-out protection.
  5. Video downloads follow-up: smart downloads with Moonfin's remove-watched rules.
  6. Network: several addresses per server with failover (JellyBox).
  7. Remote control: answer Playstate commands over the WebSocket, then let the phone control the TV.
  8. Servers 1: `reportPlayback` with `scrobble` fallback (JellyBox). Auto & Wear 1: the Auto tree (JellyBox + the
     official letter split), Cast with a profile per output. Later: ListenBrainz / Last.fm (JellyBox).
