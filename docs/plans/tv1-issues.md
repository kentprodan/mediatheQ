# TV 1 · Music + Atmos: issue drafts

Drafts for the milestone `TV 1 · Music + Atmos`, in order. Each is one branch, one PR. Numbers `T1`-`T14` are
placeholders until the issues exist on GitHub; "Depends on" uses them. Labels proposed per issue. All follow the
format of #146 / #147. Plan: [tv1-music-atmos.md](tv1-music-atmos.md).

---

## T1. TV app skeleton: `:app-tv`, `:core:designsystem-tv`, TV convention plugin, CI

Labels: `type:chore`, `area:ci`, `area:design`

## Goal
The TV app exists, installs on the Streamer, appears in the TV launcher with a banner, shows a themed placeholder screen
driven by the D-pad, and is built, tested, screenshot-validated and linted by CI like `:app`. Nothing else.

## Scope
- `:app-tv` (ADR 0038): `applicationId io.github.kentprodan.mediatheq.tv`, `TvActivity` with `MAIN` + `LEANBACK_LAUNCHER` + `LAUNCHER`, `android:banner` (xhdpi 320 x 180 with the name in the image), `android.software.leanback` and `android.hardware.touchscreen` not required, `android:colorPrimary`, `allowBackup=false`, cleartext rule of ADR 0013, Hilt `Application`, AboutLibraries plugin (ADR 0022).
- `:core:designsystem-tv` (ADR 0037): `MediatheqTvTheme` (tv-material `ColorScheme` from the Iris seed with material-color-utilities, dark; the fonts of ADR 0026), `MqTvButton`, `MqTvCard`, `MqTvListItem` wrappers, `@MqTvPreviews` (1080p, dark, font scale 1.0 and 1.3), preview fixtures.
- `build-logic`: `mediatheq.android.feature.tv` convention plugin (allowed dependencies per ADR 0038); `tvMaterial = "1.1.0"` in `libs.versions.toml`.
- `DurationFormat`, `NameOrder` and the quality-label text move from `:core:ui` to `:core:common` (phone callers updated, no behavior change).
- Placeholder: one screen with the app name and three focusable cards, to prove focus and theme.

## Acceptance criteria
- [ ] `.\gradlew.bat :app-tv:assembleDebug` builds; the APK installs on the Streamer and shows in the launcher's apps row with the banner
- [ ] The placeholder screen opens, focus is visible and moves with the D-pad; Back leaves the app
- [ ] `:app-tv` and `:core:designsystem-tv` have no dependency on `:core:designsystem`, `:core:ui`, `:feature:*` or `androidx.compose.material3` (a Gradle check or a dependency assertion test)
- [ ] CI is green with the new modules (no workflow change)
- [ ] The phone app builds and its tests pass after the helper move

## Required tests
- [ ] Unit: `tvColorScheme(seed)` is deterministic and dark; moved helpers keep their existing tests (now in `:core:common`)
- [ ] Screenshot: every `MqTv*` component in all its states (focused, unfocused, disabled), `@MqTvPreviews`
- [ ] Robolectric: `TvActivity` starts and the placeholder shows (smoke)
- [ ] Device (`:app-tv/src/androidTest`): app start smoke test; the session activity resolved from `getLaunchIntentForPackage` is `TvActivity`

## Manual test
- [ ] Streamer: install via adb over Tailscale, open from the launcher, move focus with the remote, press Back

## Notes
**Skills:** `architecture`, `testing`, `expressive-design` (for what does not apply on TV; ADR 0037)

**Depends on:** none

ADRs 0037 and 0038 are accepted in this branch (or amended). The architecture skill module map, the testing skill (TV previews, Streamer device tests) and the expressive-design skill (phone-only note) are updated in the same PR.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T2. TV onboarding: server address, Quick Connect first, password fallback

Labels: `type:feature`, `area:ui`, `area:jellyfin`

## Goal
Sign in to the owner's Jellyfin from the TV with the remote: enter the server address once, approve a Quick Connect
code from the phone, and land on the (empty, syncing) Home. Username + password stays available for servers without
Quick Connect.

## Screens (`:feature-tv:onboarding`, ADR 0040)
- **Server:** address field (TV keyboard), last address prefilled, "Connect" validates with `GET /System/Info/Public` (major >= 12) and shows the server name; errors in plain words (not reachable, too old, cleartext refused).
- **Quick Connect:** the code in large type, "Waiting for approval..." with the server name, a Restart button, a "Use password instead" button (shown when `GET /QuickConnect/Enabled` is false or on request).
- **Login:** username, password, Sign in.
- After sign-in the library sync starts and the app routes to Home (T3 wires the shell; here a placeholder Home).
- A 401 anywhere routes back to the Server screen as on the phone (ADR 0036). Sign-out is reachable from Settings later (T11).

## Acceptance criteria
- [ ] Quick Connect flow completes end to end on the Streamer with approval from the phone's Jellyfin web UI
- [ ] Password login works; a wrong password shows the message and keeps the fields
- [ ] Focus starts in the first field of each screen; the D-pad reaches every button; Back goes one screen back
- [ ] The TV install appears in the server's Devices list as its own device (model name of the Streamer)

## Required tests
- [ ] Unit: `TvServerViewModel`, `TvQuickConnectViewModel`, `TvLoginViewModel` with `FakeSessionRepository`: loading, success, each error, restart, fallback to password
- [ ] Contract: none new (the provider is unchanged); `R9` test still green
- [ ] Screenshot: the three screens in waiting, error and success states, `@MqTvPreviews`
- [ ] Robolectric UI: Quick Connect screen shows the code and the password button when enabled is false

## Manual test
- [ ] Streamer: fresh install -> Server -> Quick Connect -> approve on the phone -> Home; then sign out (adb clear) and sign in with a password

## Notes
**Skills:** `architecture`, `jellyfin`, `testing`

**Depends on:** T1

ADR 0040 is accepted in this branch.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T3. TV navigation shell: drawer, one back stack, focus restoration, now-playing strip

Labels: `type:feature`, `area:ui`

## Goal
The TV app has its frame: a left navigation drawer (Home, Library, Search, Now playing, Settings), one Navigation 3
back stack over the shared `NavKey`s, Back behavior that feels like a TV app, and a compact now-playing strip in the
drawer footer that opens the full player (T8). Screens behind the entries are placeholders until their issues land.

## Scope (ADR 0041)
- `:app-tv/navigation`: `TvShell` (tv-material `NavigationDrawer`, collapsed to icons until focused), `TvNavigator` (`navigateTo`, `back`; unknown keys logged, never thrown), `NavDisplay` host with the ViewModel entry decorator.
- Back: from a top-level entry other than Home -> Home; on Home -> finish; from a detail page -> pop.
- Focus: drawer entries and every content container use `focusRestorer()`; opening a screen requests initial focus once; the drawer remembers the selected entry.
- Strip: cover, title, artist, play/pause state from `PlaybackConnection.state`; D-pad select opens `NowPlayingKey`.
- The TV `PlaybackConnection` wiring (`MediaController` to `MediatheqPlaybackService`) and the service's session activity (`TvActivity`) are verified here.

## Acceptance criteria
- [ ] Every drawer entry opens its (placeholder) screen; the drawer collapses when focus leaves it
- [ ] Back behaves as specified from every level; the app never shows an empty screen
- [ ] After pushing a detail page and coming back, focus returns to the item that opened it
- [ ] The strip shows what the phone app queued on the same account (the service restores the saved queue) and opens the Now Playing placeholder

## Required tests
- [ ] Unit: `TvNavigator` / back stack rules (top-level, detail, unknown key) as pure functions
- [ ] Robolectric UI: drawer selection changes the shown entry; Back from Library lands on Home; focus restoration on a two-screen stack with `mainClock` control
- [ ] Screenshot: shell with the drawer collapsed and expanded, with and without the strip
- [ ] Device: `MediaController` connects to the service from `:app-tv` (androidTest)

## Manual test
- [ ] Streamer: walk every entry with the remote, Back from each, start playback on the phone with the same account, see the strip after the TV app restarts

## Notes
**Skills:** `architecture`, `testing`, `media3` (controller API only)

**Depends on:** T2

ADR 0041 is accepted in this branch; the architecture skill section 6 gets the TV back stack.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T4. TV Home: rows of recently added, recently played, favorites and mixes

Labels: `type:feature`, `area:ui`

## Goal
Home on TV shows what the phone's Home shows, as horizontal rows of covers: Recently added, Recently played, Favorite
albums, Mixes (and the other sections `HomeSections` already defines), each row scrolling under a fixed focus line.
Selecting a cover opens its album (T6) or plays a mix.

## Scope
- `:feature-tv:home`: `TvHomeViewModel` over `LibraryRepository`, `HomeSections`, `UserDataRepository` (same inputs as the phone's `HomeViewModel`, no download sections); `TvHomeScreen` = `LazyColumn` of `LazyRow`s with a `BringIntoViewSpec` pivot (focused item 30 % from the left); `:core:ui-tv` gets `MqTvAlbumCard` (cover, title, artist, focus scale) and `MqTvRowHeader`.
- Empty library while the first sync runs: a "Syncing your library..." state with the sync progress; error state with Retry (`Sync now`).
- Long-press (or the menu key) on a cover: Play, Shuffle, Add to queue (session commands through `PlaybackConnection`).

## Acceptance criteria
- [ ] Rows match the phone's Home sections for the same account (order and content)
- [ ] Focus moves between rows without losing the column position; the focused card is the one 30 % from the left
- [ ] Select opens the album; Play from the long-press menu starts playback and the drawer strip updates
- [ ] Loading, empty, syncing and error states render

## Required tests
- [ ] Unit: `TvHomeViewModel` with fakes: loading, content, empty, syncing, error, retry, each action
- [ ] Screenshot: `MqTvAlbumCard` states; Home content, syncing, empty, error at `@MqTvPreviews`
- [ ] Robolectric UI: D-pad right in a row moves focus to the next card; long-press opens the menu with its three items (semantics)

## Manual test
- [ ] Streamer: rows load after sync, cover scrolling is smooth to the eye, long-press menu works with the remote

## Notes
**Skills:** `architecture`, `testing`

**Depends on:** T3

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T5. TV Library: albums, artists, playlists, genres

Labels: `type:feature`, `area:ui`

## Goal
Browse the whole library on TV: tabs Albums / Artists / Playlists / Genres on top, a grid of covers (albums, playlists),
a grid of artist images, a list of genres; sort by name or date added; an A-Z jump strip for long grids.

## Scope
- `:feature-tv:library`: one `TvLibraryViewModel` with a fixed `LibraryFilter` per tab (same repository queries as the phone's `LibraryViewModel`, no offline parameter), `TvLibraryScreen` with tv-material `TabRow` and a `LazyVerticalGrid` (6 columns at 1080p, `focusRestorer` per tab), sort menu, A-Z strip (`focusProperties` jumps).
- Genre and year pages reuse the album grid with a header (`GenreKey`, `YearKey`).

## Acceptance criteria
- [ ] Each tab lists the same items as the phone's Library for the account; sorting changes the order
- [ ] Switching tabs keeps each tab's scroll position and focused item
- [ ] A-Z strip jumps to the first item of the letter; the grid keeps focus visible at the top and bottom edges
- [ ] Select opens album (T6), artist (T7), playlist (T6) or the genre's album grid

## Required tests
- [ ] Unit: `TvLibraryViewModel` per filter: loading, content, empty, error, sort action; A-Z index as a pure function
- [ ] Screenshot: each tab in content and empty state; the A-Z strip; `@MqTvPreviews`
- [ ] Robolectric UI: tab switch restores focus; sort menu applies

## Manual test
- [ ] Streamer: scroll the full album grid with the remote, use the A-Z strip, switch tabs and come back

## Notes
**Skills:** `architecture`, `testing`

**Depends on:** T3

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T6. TV album and playlist pages: header, track list, play and shuffle

Labels: `type:feature`, `area:ui`, `area:playback`

## Goal
An album (or playlist) page on TV: large cover, title, artist, year, format badge (FLAC 24/96, Dolby Atmos from
metadata), Play / Shuffle / Favorite buttons, the track list with durations and disc headers; selecting a track plays
the album from it; a Resume button where the phone would show one (`resumeTarget`).

## Scope
- `:feature-tv:album`: `TvAlbumViewModel` and `TvPlaylistViewModel` (same repositories as the phone: `LibraryRepository`, `PlaylistRepository`, `ResumeRepository`, `UserDataRepository`), one `TvTrackListScreen` layout shared by both; `:core:ui-tv` gets `MqTvTrackRow` and `MqTvFormatBadge`.
- Track long-press: Play next, Add to queue, Favorite.
- Header colors from the cover (`material-color-utilities`, ADR 0017) only if cheap; otherwise the theme's surface (decide in the issue).

## Acceptance criteria
- [ ] Album page shows the same tracks, order and badges as the phone for the same album; Play / Shuffle / track select start the right queue (`PlaybackConnection.play(queue, startIndex)`)
- [ ] Playlist page lists entries and plays them; no editing on TV 1
- [ ] Favorite toggles and syncs to the server (optimistic, as on the phone)
- [ ] Focus starts on Play; D-pad down enters the list; Back returns to the opener's item

## Required tests
- [ ] Unit: both ViewModels: loading, content, empty, error, each action (play, shuffle, track, resume, favorite, play next, add to queue)
- [ ] Screenshot: album and playlist pages in content, loading and error; `MqTvTrackRow` states (focused, playing, with Atmos badge)
- [ ] Robolectric UI: selecting the third track calls `play` with `startIndex = 2`

## Manual test
- [ ] Streamer: open an album, play, shuffle, favorite; open a playlist and play it; check the phone shows the same favorite

## Notes
**Skills:** `architecture`, `media3` (controller API), `testing`

**Depends on:** T4, T5

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T7. TV artist page

Labels: `type:feature`, `area:ui`

## Goal
The artist page on TV: backdrop or image (R21), name, biography (collapsed, expandable), rows for Albums, Appears on
and Top tracks; Play all / Shuffle all.

## Scope
- `:feature-tv:artist`: `TvArtistViewModel` over the same repositories as the phone's `ArtistViewModel`; `TvArtistScreen` = header + `LazyRow`s (reusing `MqTvAlbumCard`, `MqTvTrackRow`).

## Acceptance criteria
- [ ] Albums and appears-on rows match the phone's artist page (R16 merge included)
- [ ] Play all / Shuffle all queue the artist's tracks; a top track plays from the list
- [ ] Biography expands and collapses with Select; long text scrolls with the D-pad

## Required tests
- [ ] Unit: `TvArtistViewModel`: loading, content, empty (no albums), error, each action
- [ ] Screenshot: artist page with and without backdrop and biography
- [ ] Robolectric UI: biography toggle

## Manual test
- [ ] Streamer: open an artist with a backdrop and one without; play all

## Notes
**Skills:** `architecture`, `testing`

**Depends on:** T6

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T8. TV Now Playing and queue

Labels: `type:feature`, `area:ui`, `area:playback`

## Goal
The full-screen player on TV: big cover, title / artist / album, progress with D-pad seeking, Previous / Play-Pause /
Next / Shuffle / Repeat / Favorite buttons, the output line (quality label, and the Atmos badge and notes that T10 will
fill), and a queue panel (list with the playing item marked, select to jump, Remove). The launcher's Now Playing card
opens this screen.

## Scope
- `:feature-tv:nowplaying`: `TvNowPlayingViewModel` over `PlaybackConnection` (`state`, commands), `TvNowPlayingScreen`, `TvQueueScreen` (or a right-hand panel); seek: a focusable progress row where Left / Right seek 10 s and long-press seeks faster; the position comes from `PlaybackConnection`'s sampled updates.
- The remote's media keys (play/pause, next, previous) are not handled here: they reach the session through the platform media button path (media3 skill, section 11).
- Keep-screen-on while this screen shows and playback runs.

## Acceptance criteria
- [ ] Controls work and reflect the state (play/pause icon, shuffle, repeat, favorite); the phone app shows the same state for the same account
- [ ] Seeking with the D-pad moves the position; the time labels update
- [ ] The queue lists the queue in play order; Select jumps; Remove removes (not the playing item)
- [ ] Selecting the launcher's Now Playing card opens this screen; the card shows cover and title

## Required tests
- [ ] Unit: `TvNowPlayingViewModel` with a `FakePlaybackConnection`: each action, state mapping, seek step rules (pure)
- [ ] Screenshot: player (playing, paused, buffering, with quality label, with the Atmos badge placeholder), queue panel
- [ ] Robolectric UI: D-pad Right on the progress row calls `seekTo(position + 10 s)`; Select on the second queue item jumps

## Manual test
- [ ] Streamer: play an album, open Now Playing, seek, skip, open the queue, go Home and come back through the launcher's Now Playing card

## Notes
**Skills:** `architecture`, `media3` (controller API), `testing`

**Depends on:** T3, T6

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T9. Playback service: HDMI output, passthrough capabilities and `AudioMode.Passthrough`

Labels: `type:feature`, `area:playback`

## Goal
The playback service knows when media plays over HDMI, what the route can pass through, and runs the player in
passthrough mode for such tracks: no offload, no EQ, no normalization, volume 1, no fades. Whether the bitstream really
went out untouched is read back from the audio track the sink opened. No Jellyfin change yet (T10).

## Scope (ADR 0039)
- `:core:model`: `OutputKind.Hdmi`, `OutputId` `hdmi:<hash of product name>`; `:core:data`: `OutputProfile.atmosPassthrough` (default true; the Output settings page on the phone does not show it).
- `:core:playback`: `AudioRoute.Hdmi` (`TYPE_HDMI`, `TYPE_HDMI_ARC`, `TYPE_HDMI_EARC` in `outputKind` / `AudioOutputPort.currentOutput`); `PassthroughCapabilities` (fun interface; `Media3PassthroughCapabilities` built from `AudioCapabilities.getCapabilities(context, attributes, routedDevice, spatializerMasks)` and `supportsEncoding` for `C.ENCODING_E_AC3_JOC`, `ENCODING_E_AC3`, `ENCODING_AC3`, `ENCODING_DOLBY_TRUEHD`); `AudioMode.Passthrough`; `AudioPolicy.decide` / `offload` / `volume` / `normalization` / `wantsPassthrough` rules; `VolumeFader` skips passthrough; `EqualizerEffect` disabled in passthrough; `AudioCapabilitiesReceiver` registered in the service (unregistered on destroy), its callback re-running `AudioOutputController`; `AudioOutputState.passthroughEncoding` from `onAudioTrackInitialized` / cleared on `onAudioTrackReleased`; `AudioOutputInfo.atmosPassthrough`; the `AudioSink` write-error path re-prepares the item once for passthrough tracks (as for bit-perfect), then surfaces a `PlaybackError`.
- Logging under `MqAudioOutput`: route, passthrough set, decision, the opened track's encoding.

## Acceptance criteria
- [ ] On the Streamer over HDMI with Surround sound = Auto, the log shows the passthrough set containing E-AC-3 JOC (and TrueHD if the chain reports it) and `route = Hdmi`
- [ ] Playing a local E-AC-3 JOC file (sideloaded test file through the existing file path) opens an `AudioTrack` with encoding `ENCODING_E_AC3_JOC`; `atmosPassthrough` is true while it plays and false after a stereo track starts
- [ ] Phone: every existing behavior unchanged (`AudioRoute.Other`, empty passthrough set, all existing tests green without edits except new parameters with defaults)
- [ ] Turning the HDMI output's `atmosPassthrough` off makes the mode `Offload` / `Processed` again on the next track

## Required tests
- [ ] Unit (`AudioPolicyTest`): every new rule: HDMI + switch on + codec in set -> `Passthrough`; HDMI + switch off; HDMI + codec not in set; USB DAC unaffected; offload per route; volume 1; normalization off; fader skipped
- [ ] Unit: `outputKind` maps the three HDMI types; `OutputId` parses `hdmi:`; `OutputProfile` default and DataStore round trip
- [ ] Robolectric (`AudioOutputControllerTest`): capability change re-runs the decision; `passthroughEncoding` set and cleared from the two analytics callbacks; write error path
- [ ] Device (androidTest on the Streamer, optional): `Media3PassthroughCapabilities` is non-empty on HDMI

## Manual test
- [ ] Streamer -> LG TV -> eARC -> DSN11RG: `adb logcat -s MqAudioOutput` shows the passthrough set and `encoding=18` (E-AC-3 JOC) for a JOC file; soundbar display shows "Dolby Atmos"

## Notes
**Skills:** `media3`, `architecture`, `testing`

**Depends on:** T1 (the TV app to run it on; the code is in `:core:playback`)

ADR 0039 is accepted in this branch; ADR 0032 gets its `hdmi:` amendment; the media3 skill sections 1, 4, 7 are updated. `AudioCapabilities` / `AudioCapabilitiesReceiver` are `@UnstableApi`: used in one class, checked at every Media3 bump.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T10. Atmos stream selection: passthrough DeviceProfile, never transcode Atmos, honest badge and notes

Labels: `type:feature`, `area:playback`, `area:jellyfin`

## Goal
Jellyfin is asked for the original E-AC-3 JOC / TrueHD file whenever the route passes it through, whatever the quality
setting says; the server never transcodes such a track on that route. On a route that cannot, the track decodes on the
device (JOC decoder) or converts to stereo as today, and Now Playing says which. The TV Now Playing badge "Dolby Atmos"
appears only while passthrough is confirmed.

## Scope (ADR 0039, rules 3-5)
- `DeviceProfileBuilder.build(decoderMimeTypes, passthroughMimeTypes, quality)`: `eac3` with `audio/eac3-joc` in either set, `truehd` only with `audio/true-hd` in the passthrough set; `download(...)` unchanged (TV has no downloads).
- `StreamResolver`: pure `atmosRequest(track, passthrough, quality)`; an Atmos track whose codec passes through asks with `StreamingQuality.Original`; cache key rules unchanged (`<MediaId>:original`).
- `AudioPolicy.atmosDecoded(isAtmos, playMethod, passthroughConfirmed, decoderUsed)` -> `AudioOutputInfo.atmosDecoded`; existing `atmosConverted` unchanged.
- `:feature-tv:nowplaying`: badge "Dolby Atmos" from `atmosPassthrough`; notes "Dolby Atmos not available · Decoded on this output" / "Playing in stereo on this output"; the phone's Now Playing shows the decoded note too (same `AudioOutputInfo`).
- Contract fixture `items_playbackinfo/passthrough_atmos.json` recorded from the owner's server: request with `DirectPlayProfiles` containing `eac3,truehd` and `MaxStreamingBitrate` absent; response `PlayMethod = DirectPlay` for a JOC track.

## Acceptance criteria
- [ ] With a quality cap set (256 kbps) on the TV, an Atmos track still direct-plays over HDMI; a stereo FLAC in the same queue converts as the cap says
- [ ] With passthrough unavailable (Bluetooth headphones paired to the Streamer), the same track plays decoded or converted and the note says so; no silent downgrade
- [ ] The badge is on only while `encoding` is JOC or TrueHD; it goes off on the next stereo track
- [ ] Phone (`Pixel 9`): unchanged behavior for Atmos tracks (stereo Opus 256 with the note)

## Required tests
- [ ] Unit (`DeviceProfileBuilderTest`): the four combinations of decoder / passthrough for `eac3`; `truehd` only with passthrough; empty passthrough set equals today's profile byte for byte
- [ ] Unit (`StreamResolverTest`): `atmosRequest` per row (Atmos + passthrough + cap -> Original; Atmos without passthrough + cap -> cap; stereo + cap -> cap; Atmos TrueHD with only JOC passthrough -> cap); the resolved stream per case
- [ ] Unit (`AudioPolicyTest`): `atmosDecoded` / `atmosConverted` truth table
- [ ] Contract (`PlaybackApiTest`): `passthrough_atmos.json` request shape (profiles, no cap, `MusicStreamingTranscodingBitrate` still sent, R11) and parse
- [ ] Screenshot: TV Now Playing with the badge, with each note
- [ ] Unit: `TvNowPlayingViewModel` maps `AudioOutputInfo` to badge / note

## Manual test
- [ ] Streamer -> LG TV -> eARC -> DSN11RG (Surround sound = Auto, TV digital out = Pass-through, eARC on): an E-AC-3 JOC album -> soundbar shows "Dolby Atmos", the TV badge is on, the server's Dashboard shows Direct Play
- [ ] Same with a TrueHD (MKA) track -> soundbar shows "Dolby Atmos" / "TrueHD"; if not, record what it shows (becomes a bug, not a blocker)
- [ ] Set quality to 256 kbps in TV Settings (T11) -> Atmos still direct-plays, a FLAC converts
- [ ] Pair Bluetooth headphones to the Streamer -> the note appears, playback continues

## Notes
**Skills:** `media3`, `jellyfin`, `architecture`, `testing`

**Depends on:** T8, T9

The jellyfin skill section 5 and the media3 skill section 4 (phone rule 5 becomes "no passthrough route") are updated in this PR; no server-upgrade chores (owner rule).

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T11. TV Settings: Dolby Atmos passthrough, streaming quality, account, licenses

Labels: `type:feature`, `area:ui`

## Goal
A small Settings screen on TV: the HDMI output's Dolby Atmos passthrough switch (with the explanation that the TV's
Surround sound setting decides what the route offers), the unmetered streaming quality, Sync now, the account (server,
user) with Sign out, the app version and the open-source licenses list.

## Scope
- `:feature-tv:settings`: `TvSettingsViewModel` over `AudioOutputRepository` (the current HDMI output's profile), `AudioPreferencesRepository` (quality), `LibrarySyncRepository`, `AccountRepository` / `SessionRepository`; `TvSettingsScreen` as a tv-material `ListItem` list with `Switch`es and selection dialogs (Compose `Dialog`).
- Licenses: the AboutLibraries JSON of `:app-tv` parsed as the phone does (move `LicensesReport` parsing from `:app` to `:core:common` so both apps share it) and shown as a scrolling list with the license text on select.
- Sign out: `POST /Sessions/Logout`, token deleted, route to onboarding (ADR 0036 behavior for the last account).

## Acceptance criteria
- [ ] The passthrough switch shows for the HDMI output only and takes effect on the next track (T9)
- [ ] Quality change applies at the next load (as on the phone); Sync now runs a sync with visible progress
- [ ] Sign out returns to the Server screen and the server's Devices list shows the session gone
- [ ] Licenses list is complete (the same `LicensesReportTest` rule as the phone, on `:app-tv`'s report)

## Required tests
- [ ] Unit: `TvSettingsViewModel`: each setting read and written, sync action, sign-out action, error
- [ ] Unit: moved `LicensesReport` parsing keeps its tests; `:app-tv` `LicensesReportTest` on its release report
- [ ] Screenshot: settings list, each dialog, licenses list and one license text
- [ ] Robolectric UI: toggling the switch calls the repository

## Manual test
- [ ] Streamer: toggle passthrough off, play an Atmos track (decoded note), toggle on; change quality; Sync now; open licenses; sign out and back in

## Notes
**Skills:** `architecture`, `testing`

**Depends on:** T9, T10

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T12. TV Search

Labels: `type:feature`, `area:ui`

## Goal
Search the library from the TV with the on-screen keyboard: results in rows (Albums, Artists, Tracks, Playlists) as
you type, diacritic-insensitive (the FTS of #17), Select opens or plays.

## Scope
- `:feature-tv:search`: `TvSearchViewModel` over the same search repository the phone uses (`LibraryRepository.search` / `FtsQuery`), debounced; `TvSearchScreen` with a `BasicTextField` styled by `:core:designsystem-tv` (`MqTvSearchField`), results as `LazyRow`s under the field; recent searches (`RecentSearches`) when the field is empty.
- No voice search in TV 1.

## Acceptance criteria
- [ ] Typing "Mosoi" finds "Moșoi"; results update while typing; empty state and no-results state render
- [ ] D-pad Down from the field enters the first result row; Back from results returns to the field, a second Back leaves Search
- [ ] Recent searches are listed and selectable

## Required tests
- [ ] Unit: `TvSearchViewModel`: idle with recents, typing (debounce with the test scheduler), results, no results, error, select
- [ ] Screenshot: idle with recents, results, no results
- [ ] Robolectric UI: focus moves from the field to the first row with D-pad Down

## Manual test
- [ ] Streamer: search with the remote keyboard, open an album from the results, use a recent search

## Notes
**Skills:** `architecture`, `testing`

**Depends on:** T5, T6

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T13. Lyrics on TV Now Playing

Labels: `type:feature`, `area:ui`

## Goal
Synced lyrics on the big screen: a "Lyrics" button on Now Playing opens a panel with the current line large and
centered, the previous and next lines dimmed, scrolling with playback; plain lyrics scroll with the D-pad.

## Scope
- `:feature-tv:nowplaying`: `TvLyricsPanel` fed by `LyricsRepository` (R4 sync inference as on the phone) and the sampled position from `PlaybackConnection`.

## Acceptance criteria
- [ ] Synced lyrics follow the position within one line; plain lyrics show as text
- [ ] No lyrics: the button is disabled with a hint
- [ ] The panel does not steal focus from the transport controls (Back closes it)

## Required tests
- [ ] Unit: current-line selection from position (pure, shared with the phone if it already is a pure function; otherwise moved to `:core:common`)
- [ ] Screenshot: synced, plain, none
- [ ] Robolectric UI: Back closes the panel and focus returns to the Lyrics button

## Manual test
- [ ] Streamer: an album with synced lyrics, one with plain lyrics, one without

## Notes
**Skills:** `architecture`, `testing`

**Depends on:** T8

Owner may defer this issue to TV 2 without affecting the milestone exit.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## T14. TV 1 exit: Atmos chain verified, a week of listening on the Streamer

Labels: `type:chore`

## Goal
Close the milestone the way Mobile 1 closed (#72): the Atmos manual tests of T9 and T10 passed (E-AC-3 JOC required,
TrueHD recorded), and the owner used the TV app for a week of listening. Everything that got in the way is filed as a
bug in this milestone or moved to TV 2 · Video / a Polish milestone.

## Acceptance criteria
- [ ] Test issues of T9 and T10 closed with "Dolby Atmos" seen on the DSN11RG for E-AC-3 JOC
- [ ] TrueHD result recorded (pass, or a bug with what the soundbar showed)
- [ ] A week of use logged in a comment: what worked, what did not, what was missed (candidates for the next milestone)
- [ ] No open `type:bug` in the milestone, or each is triaged to a later milestone with a reason

## Required tests
- [ ] None (tracking issue)

## Manual test
- [ ] Daily listening on the Streamer for seven days, at least one Atmos album per day

## Notes
**Skills:** none

**Depends on:** T10, T11, T12

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)
