# Mobile 3 · Video: issue drafts

Drafts for the milestone `Mobile 3 · Video`, in order. Each is one branch, one PR. Numbers `V1`-`V11` are
placeholders until the issues exist on GitHub; "Depends on" uses them. Labels proposed per issue. All follow the
format of #146 / #147. Plan: [mobile3-video.md](mobile3-video.md). Manual tests run on the Pixel 9 against the
owner's Jellyfin 12.3.0 over Wi-Fi and Tailscale; fixtures are recorded from that server with personal names and
paths stripped.

---

## V1. Video library: model, Room tables, Jellyfin sync, Movies and Series lists

Labels: `type:feature`, `area:data`, `area:jellyfin`, `area:ui`

## Goal
After a sync the Library hub shows Movies and Series tiles with counts; each opens a poster grid (sorted by name,
date added or year, with an Unwatched filter) that lists the owner's movies or series with their posters, year and
watched state. Nothing plays yet; tapping a poster opens a placeholder page.

## Scope (ADR 0048)
- `:core:model`: `Video` (`VideoKind.Movie | Episode`), `Series`, `Season`, `MediaStreamInfo`, `VideoFormat`, `UserData.playbackPosition` / `played` / `playedPercentage` / `unplayedCount`.
- `:core:server`: `LibrarySource.movies / series / seasons / episodes(server, startIndex, limit)`; `ServerCapabilities.video` (`true` for Jellyfin; the Subsonic provider of #249 answers `false` and `MqError.Client(0, "unsupported")`).
- `:provider:jellyfin`: `/Items?includeItemTypes=Movie|Series|Season|Episode&recursive=true&sortBy=SortName&sortOrder=Ascending&fields=MediaSources,Overview,Genres,Studios,DateCreated,SortName,Taglines&enableImageTypes=Primary,Backdrop,Thumb&enableUserData=true&startIndex&limit`; mappers for the episode / series fields, `MediaStreams` of type `Video` -> `VideoFormat`, `Audio` / `Subtitle` -> `MediaStreamInfo`, `UserData.PlaybackPositionTicks` -> `Duration`; fixtures `items_movie/page.json`, `items_series/page.json`, `items_season/page.json`, `items_episode/page.json`, `items_episode/with_external_subtitles.json`, `items_movie/hdr_dovi.json`.
- `:core:database`: `SeriesEntity`, `SeasonEntity`, `VideoEntity`, `VideoStreamEntity` + staging twins, `VideoDao` (movies / series lists with sort and `unwatched`, seasons of a series, episodes of a season, streams of a video, all list queries with the `all` / `downloaded` parameters of ADR 0021), FTS rows for the three kinds; schema 12, `AutoMigration(11 -> 12)`, exported `12.json`.
- `:core:data`: `VideoRepository` (lists, `movie(id)`, `series(id)`, `seasons`, `episodes`, `streams`), `DefaultLibrarySyncRepository` video steps after music (skipped when `capabilities.video` is false; progress shown by the Sync manager of #148 as "Movies", "Series", "Episodes"), `LibraryShortcut.Movies / Series`, `MainNavEntry.Movies / Series` (off by default), `LibraryPreferences` sort per list.
- `:feature:video`: `MoviesViewModel` / `MoviesScreen`, `SeriesListViewModel` / `SeriesListScreen` (`LazyVerticalGrid`, `MqPosterCard` 2:3 with a watched check and an unplayed-count badge, sort menu, Unwatched chip), placeholders for `MovieKey` / `SeriesKey`; `:feature:library` hub tiles Movies / Series (hidden when the capability is false or both counts are 0); `:app` entries and `TopLevelTab.Movies / Series`.
- `:core:ui`: `MqPosterCard`, poster size in `MediatheqSizes`, grid columns from the album grid setting + 1.

## Acceptance criteria
- [ ] A full sync on the owner's server fills movies, series, seasons and episodes; the Sync manager shows the three new steps; the music steps and their timing are unchanged
- [ ] Library hub shows Movies and Series tiles with the right counts; the grids list every movie / series with posters, sort and the Unwatched filter work
- [ ] A second sync changes nothing; a movie marked watched in the web UI shows the check after Sync now
- [ ] A Subsonic account (or a Jellyfin server without video libraries) shows no tiles and runs no video steps
- [ ] Offline mode lists no movies or series (nothing is downloaded yet)

## Required tests
- [ ] Mapper: every DTO -> domain function (movie, series, season, episode, streams, formats, user data with and without a position; missing fields)
- [ ] Contract: exact query of the four item requests; parse of each fixture; `R7` / `R8` style assertions
- [ ] Database: every `VideoDao` query including sort, `unwatched`, the offline parameters with 0 and 10,000 ids, FTS "Mosoi" finds "Moșoi" for a movie title; migration test 11 -> 12 on a database with music rows
- [ ] Repository: `DefaultLibrarySyncRepository` with `FakeLibrarySource` video pages upserts and swaps; `capabilities.video = false` skips the steps
- [ ] Unit: `MoviesViewModel`, `SeriesListViewModel`: loading, content, empty, error, sort, filter
- [ ] Screenshot: `MqPosterCard` states (plain, watched, unplayed count, loading placeholder); both grids in content and empty; the hub with the new tiles (`@MqPreviews`)

## Manual test
- [ ] Pixel 9: Sync now, open Movies and Series, sort by year, filter Unwatched, search a movie title; switch to the Navidrome account (if present): no tiles

## Notes
**Skills:** `architecture`, `jellyfin`, `testing`, `expressive-design`

**Depends on:** #249 (`ServerCapabilities`; without it the capability is a constant `true` on the Jellyfin provider and this issue adds the field)

ADR 0048 is accepted (owner, 2026-11-12). The architecture skill (module map, section 4) and the jellyfin skill (section 3, new fixtures and R lines) are updated in the same PR.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## V2. App mode: Music, Video or Both, chosen in onboarding, in Settings and on Home

Labels: `type:feature`, `area:ui`, `area:data`

## Goal
The app knows how it is used. Onboarding asks "What do you use mediatheQ for?" (Music, Video, Both) right after
the first sign-in; Settings -> Interface -> App mode and a segmented toggle at the top of Home change it later.
Music hides every video surface (today's app), Video hides every music surface, Both shows music with video placed
in Library tiles, Home sections, Search groups and optional navigation entries. Existing installs read as Music.
Sync runs only the steps of the mode.

## Scope (ADR 0051)
- `:core:model`: `AppMode { Music, Video, Both }` with `music` / `video` booleans.
- `:core:data`: `AppModeRepository` (`mode: Flow<AppMode>`, `setMode`) in the settings DataStore, key `app_mode`, missing = `Music` (upgrade default); in Backup (#150) like every preference; `DefaultLibrarySyncRepository` runs music steps when `mode.music` and video steps when `mode.video && capabilities.video`; `setMode` starts a sync when the newly enabled kind was never synced on this account (`sync_state` rows per kind); `HomeSections` lists music sections only with `music`, video sections only with `video`; `MainNavEntry.allowed(mode)`: music entries (Albums, Album artists, Genres, Record labels, Playlists, Tracks) only with `music`, Movies / Series only with `video`; the Navigation settings page drops disallowed entries and resets the chosen ones to the mode's defaults (Music / Both: today's defaults; Video: Home, Library, Search, Downloads).
- `:feature:onboarding`: `AppModeKey` step after login / Quick Connect (three large cards, Both preselected when the server has video libraries, Music preselected and the step skipped when `capabilities.video` is false); first-run only; the picker of #147 for a second account does not ask again (app-wide setting).
- `:feature:home`: `MqSegmentedButtons` Music / Video / Both at the top of Home (hidden for an account without video capability, which is always Music), `HomeViewModel` reads the mode for its sections; `:feature:library`: hub tiles per mode (Video: Movies and Series only; Years / Genres / Labels are music tiles); `:feature:search`: groups per mode; `:feature:downloads`: collections per mode (a download of the other kind is kept, not listed, and counted in the Settings storage line); `:feature:settings`: App mode page (`AppModeSettingsKey`, radio list with one sentence each), hub entry under Interface; music-only pages hidden in Video mode (Playback -> Decoding and transcoding, Transitions, Bluetooth lyrics, Now playing screens, Media lists' music options, Page settings for Album / Artist / Genre / Playlist; Output settings, Equalizer, Audio focus, Headset buttons stay: they apply to video audio).
- `:feature:nowplaying` / `:app`: in Video mode the Now Playing overlay is never opened and the mini player shows only while a video item plays (V3); the saved music queue is not restored on start in Video mode and stays saved for a switch back; `MainViewModel` exposes the mode to the shell.
- Video-mode placeholders: the Library hub shows Movies / Series tiles from V1; the rest of the video UI arrives with V3-V6.

## Acceptance criteria
- [ ] Fresh install: sign in -> mode step -> Home matches the choice; an upgrade from the current build lands in Music with nothing changed on screen
- [ ] Switching to Video on Home: music tiles, sections, entries, mini player and music settings pages disappear at once; Downloads lists only videos; switching back to Both brings everything back without a restart; Sync now runs only the mode's steps (Sync manager shows them)
- [ ] Switching from Music to Both on an account that never synced video starts a sync by itself; the video tiles fill
- [ ] A Subsonic account (no video capability) shows no toggle and behaves as Music whatever the stored mode
- [ ] Backup & restore keeps the mode

## Required tests
- [ ] DataStore: `app_mode` round trip, missing -> Music
- [ ] Unit: `MainNavEntry.allowed(mode)` and the default entries per mode; `HomeSections` filtered per mode; the "sync the newly enabled kind" rule (never synced -> sync; synced -> nothing; capability false -> nothing)
- [ ] Repository: `DefaultLibrarySyncRepository` runs only the mode's steps (three modes x capability)
- [ ] Unit: `AppModeViewModel` (onboarding) preselection per capability; `HomeViewModel` toggle action; `SettingsViewModel` hidden entries per mode
- [ ] Screenshot: the onboarding step, the Home toggle in the three states, the Settings page, the Library hub in Video mode, the Navigation settings page in Video mode (`@MqPreviews`)
- [ ] Robolectric UI: tapping Video on the Home toggle removes the music sections from the Home list (semantics)

## Manual test
- [ ] Pixel 9: update over the current build (Music, unchanged); toggle Video on Home, walk Library, Search, Downloads, Settings; toggle Both; sign out, fresh sign-in, pick Video in onboarding

## Notes
**Skills:** `architecture`, `expressive-design`, `testing`

**Depends on:** V1

ADR 0051 is accepted (owner, 2026-11-12). The architecture skill (section 4: sync per mode; section 5: screens gate on `AppMode` through their UiState) and the expressive-design skill (component map: the Home toggle, the onboarding step) are updated in the same PR.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## V3. Play a video: video stream selection, one-player hand-over, full-screen player with basic controls

Labels: `type:feature`, `area:playback`, `area:jellyfin`, `area:ui`

## Goal
Tapping Play on a movie (or an episode in a placeholder list) plays it full screen on the Pixel 9: the picture in
the right aspect ratio, landscape when the video is wider than tall, play / pause, seek bar with times, +-10 s, a
Back that pauses and returns to the page. The music queue is saved before the video and comes back paused when the
video ends or is stopped. No subtitles, no track choice, no PiP yet.

## Scope (ADR 0047, 0049, 0050)
- `:core:playback`: `VideoDecoders` (snapshot of `MediaCodecList` video MIME types and profile levels), `DeviceProfileBuilder.video(audioDecoders, videoDecoders, hdr = none, quality)` (containers, codecs, `MaxStreamingBitrate`, HLS fMP4 transcoding profile, codec profile conditions for `Width`, `VideoBitDepth`, `AudioChannels`, `VideoRangeType EqualsAny SDR`; HDR ranges come in V7), `StreamResolver.resolveVideo(itemId, choice)`, `VideoMediaItems` (extras: kind, series title, episode numbers, poster URI, stream choice), `PlaybackConnection.playVideo(video, choice = defaults, startPosition)`, `videoState: StateFlow<VideoPlayerUiModel>` (aspect ratio, playing, buffering, position via the existing sampled flow, duration, error); `QueuePersistence` ignores video items and restores the saved music queue paused when the video item ends or is cleared; `AudioPolicy.offload(settings, route, video)` is off while a video plays; `ReportingListener` reports video items like tracks (indexes in V5).
- `:provider:jellyfin`: `PlaybackInfoDto` gains `StartTimeTicks`, `AudioStreamIndex`, `SubtitleStreamIndex`, `EnableDirectPlay`, `EnableDirectStream`, `EnableTranscoding`, `AllowVideoStreamCopy`, `AllowAudioStreamCopy`; `DeviceProfileDto` gains `VideoCodec` on direct play and transcoding profiles, `CodecProfiles`, `SubtitleProfiles`, `MaxStaticBitrate`; the video direct-play URL `/Videos/{id}/stream?static=true&mediaSourceId&playSessionId`; fixtures `items_playbackinfo/video_direct_play.json`, `video_direct_stream.json`, `video_transcode_hls.json`.
- `:feature:video`: `VideoPlayerViewModel` / `VideoPlayerScreen` (`VideoPlayerKey(id, startPosition?)` pushed on the current tab's stack; `PlayerSurface(controller, SURFACE_TYPE_SURFACE_VIEW)` through `rememberPresentationState` with a black shutter until the first frame; controls overlay with auto-hide after 3 s, `MqVideoControls` in `:core:ui` (play / pause morphing button, seek bar, times, +-10 s, title line); orientation locked to landscape while a 16:9 video plays and back on leave; immersive system bars; keep-screen-on while playing).
- Movie page placeholder (V4 fills it) gets the Play button so the slice is user-visible; the mini player shows the video's poster and title and opens the player (in Video mode it is the only mini player state; in Music mode video never plays).
- Logging under `MqVideo`: play method, container, codec, decoder name, output size.

## Acceptance criteria
- [ ] An H.264 / AAC MP4 movie direct-plays on Wi-Fi (`PlayMethod = DirectPlay` in the server dashboard); an HEVC MKV with an audio codec the phone lacks direct-streams or transcodes (dashboard shows it) and plays
- [ ] With the 480p quality set in a debug setting (the Video settings page comes in V7) the same movie transcodes as HLS at 480p and seeks
- [ ] In Video mode (V2) the music queue is not restored after the video ends; in Music mode `playVideo` is never offered
- [ ] Play, pause, seek, +-10 s, Back work; the position after Back is reported to the server (web UI shows the resume point)
- [ ] Music playing before the video pauses; after the video ends the music queue is back, paused at the same track and position; the music notification returns
- [ ] Phone: every existing music test passes without edits other than new parameters with defaults

## Required tests
- [ ] Unit (`DeviceProfileBuilderTest`): `video(...)` per decoder set (h264 only; h264 + hevc; + av1), per quality (Original, 480p), the transcoding profile shape, conditions
- [ ] Unit (`StreamResolverTest`): `resolveVideo` direct play / direct stream / HLS transcode rows; `canSeek` per row; cache keys never written for video (`FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN` path unchanged)
- [ ] Contract (`PlaybackApiTest`): the video `PlaybackInfo` request body (profile, indexes, start ticks) and the three fixtures' parse (`SupportsDirectStream` row included); `ApiKey` stripped from the transcoding URL
- [ ] Playback (Robolectric, `media3-test-utils`): a video item replaces the playlist, the saved queue is untouched, and ending the item restores the queue paused at the saved position; `AudioPolicy.offload` off while a video plays
- [ ] Unit: `VideoPlayerViewModel` with `FakePlaybackConnection`: loading, playing, paused, buffering, error, each action, controls auto-hide timing (test scheduler)
- [ ] Screenshot: `VideoPlayerScreen` (surface slot = a dark `Box`) with controls shown / hidden, buffering, error; `MqVideoControls` states; the mini player in video state
- [ ] Device (androidTest): the first frame renders through the `MediaController` on the Pixel 9 (`EVENT_RENDERED_FIRST_FRAME` observed with a `PlayerSurface` in a test activity)

## Manual test
- [ ] Pixel 9: play a movie on Wi-Fi, seek, pause, Back, reopen (resume point in the web UI); then with music playing: start a video, let it end, see the music queue back

## Notes
**Skills:** `media3`, `jellyfin`, `architecture`, `expressive-design`, `testing`

**Depends on:** V1, V2

ADR 0047, 0049 and 0050 are accepted (owner, 2026-11-12). The media3 skill gets its Video section; `PlayerSurface` / `PresentationState` are `@UnstableApi` (checked at every Media3 bump, like `LazyStreamMediaSource`).

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## V4. Movie page, series page with seasons and episodes, Resume and Next up

Labels: `type:feature`, `area:ui`, `area:data`

## Goal
A movie page (backdrop header, poster, title, year, runtime, rating, tagline, overview, genres, studios, format
line "4K · HEVC · HDR10 · 5.1", Play / Resume, Favorite, Mark watched) and a series page (backdrop, overview, Next
up row, season tabs, episode rows with thumbnail, number, title, runtime, progress line and watched check; tapping
an episode plays it, Play from start in the long-press menu). Resume starts where the server left off.

## Scope
- `:feature:video`: `MovieViewModel` / `MovieScreen` (`LargeFlexibleTopAppBar` over the backdrop like the album page, `MqCoverTheme` optional per the artist page setting), `SeriesViewModel` / `SeriesScreen` (seasons as `PrimaryScrollableTabRow`, episodes as `MqEpisodeRow`), `SeasonKey` not needed (tabs); `resumeTarget` logic reused from the album page (`position > 0 && played == false` -> Resume + Play from start).
- `:core:data`: `VideoRepository.nextUp(seriesId)` from Room (first unplayed episode after the last played one, pure function) and `UserDataRepository.setPlayed(id, played)` (optimistic, `pending_user_data` when offline; `UserDataSink.setPlayed` -> `POST` / `DELETE /UserPlayedItems/{itemId}`; fixture `user_played_items/ok.json`).
- `:core:ui`: `MqEpisodeRow` (16:9 thumb with progress line, number, title, runtime, overflow), `LocalMediaListStyle` applied (swipe actions: Mark watched / Download later).
- Search (#136): results groups Movies, Series, Episodes open the pages; filter chip Video.

## Acceptance criteria
- [ ] Movie page shows the facts of the fixture movies (HDR line, 5.1, external subtitles count); Play plays from 0, Resume from the server position; Mark watched toggles and shows in the web UI; a change in the web UI shows after the socket event or Sync now
- [ ] Series page lists seasons and episodes in order with progress lines; Next up names the right episode; tapping an episode plays it and the row updates after Back
- [ ] Search finds a movie, a series and an episode by title
- [ ] Loading, empty (a series with no episodes), error states render

## Required tests
- [ ] Unit: `MovieViewModel`, `SeriesViewModel`: loading, content, empty, error, each action (play, resume, favorite, watched, episode tap, season switch); `nextUp` pure function rows (nothing played, mid-season, all played, a gap)
- [ ] Repository: `setPlayed` optimistic / revert / pending; contract `user_played_items` request shapes (`POST` with `datePlayed`, `DELETE`)
- [ ] Screenshot: movie page (SDR and HDR fixtures), series page (two seasons), `MqEpisodeRow` states (unplayed, in progress, watched, downloaded), search results with video groups
- [ ] Robolectric UI: tapping the third episode calls `playVideo` with that id; the Resume button sends the saved position

## Manual test
- [ ] Pixel 9: open a movie, Resume, Back; open a series, play Next up, Back, see the progress line; mark an episode watched, check the web UI

## Notes
**Skills:** `architecture`, `expressive-design`, `jellyfin`, `testing`

**Depends on:** V3

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## V5. Subtitles and audio tracks: delivery rules, side-loading, tracks sheet, per-item and per-season choice

Labels: `type:feature`, `area:playback`, `area:jellyfin`, `area:ui`, `area:data`

## Goal
The player shows subtitles (embedded, external and burned-in) and lets the user pick the audio track and the
subtitle track from a sheet; the same sheet opens from the movie page, an episode row and a season menu before
playing. A choice is remembered for that movie or episode, or for the whole season, and used every time it plays;
Settings keeps the default languages for everything else. Cues render with the system caption style and size.

## Scope (ADR 0050, plan section 5 rules 4-5)
- `DeviceProfileBuilder.video`: `SubtitleProfile`s `External` (`subrip`, `webvtt`, `ass`, `ssa`, `mov_text`), `Embed` (same + `pgssub`), `Hls` (`webvtt`), `Encode` (`pgssub`, `dvdsub`, and `ass` / `ssa` when "Burn in styled subtitles" is on); `StreamChoice(audioIndex, subtitleIndex)` sent as `AudioStreamIndex` / `SubtitleStreamIndex`; `PlaybackStream.subtitles: List<SubtitleDelivery>` from the answer's `MediaStreams` (`DeliveryMethod`, `DeliveryUrl` with `ApiKey` stripped, MIME from the format).
- `VideoMediaItems`: external deliveries as `MediaItem.SubtitleConfiguration` (URI through the app data source with the auth header; `startPositionTicks` equal to the transcode start when the stream is a transcode, else 0), language, label, `SELECTION_FLAG_DEFAULT` on the chosen one; embedded tracks selected by `TrackSelectionOverride` after `onTracksChanged` (matched by the server stream index carried in the extras -> Media3 track index by language + order; a pure `matchTrack` function with tests); a choice that needs a new server stream (burn-in, transcoded audio) re-resolves at the current position (the `InstantTranscoder` replace path, new play session).
- `PlaybackConnection.selectTracks(audioIndex, subtitleIndex?)`, `videoState.tracks` (audio and subtitle entries with labels, selected flags, "burned in" flag), `videoState.cues` (from `onCues`).
- `:core:ui`: `MqSubtitles` (an `AndroidView` over `androidx.media3.ui.SubtitleView`, user style from `CaptioningManager`, bottom padding above the controls when shown), `MqTracksSheet` (two lists, Off for subtitles).
- `ReportingListener`: `AudioStreamIndex` / `SubtitleStreamIndex` in every report (`PlaybackReportDto` fields).
- Defaults from a `VideoPreferences` (subtitle mode Off / Forced only / Language, preferred audio language) read by `playVideo`; the Settings page itself is V7 (defaults hard-wired until then: Forced only, phone locale).
- **Remembered choice** (ADR 0050): `:core:database` table `video_track_choice(server_id, item_id, audio_index, audio_language, subtitle_index, subtitle_language, subtitle_off)` where `item_id` is a movie, an episode or a **season** id (schema 13, auto migration, migration test; no foreign keys so a sync keeps it; cleared on sign-out like `album_resume`); `TrackChoiceRepository` (`choice(itemId)`, `seasonChoice(seasonId)`, `save`, `clear`); a pure `resolveChoice(streams, itemChoice, seasonChoice, preferences)`: the item's own indexes when they exist in its streams, else the season's choice matched by language (and the forced / hearing-impaired flags), else the Settings defaults; `playVideo` uses it, and a change in the player saves the item's choice (the sheet offers "Apply to the whole season" for episodes).
- Pages (V4): the movie page gets an "Audio & subtitles" row under the buttons showing the resolved choice and opening the sheet; an episode row's menu and the season tab's menu get the same entry (the sheet reads the episode's streams, or the first episode's for a season, and writes the season row).

## Acceptance criteria
- [ ] An MKV with embedded SRT and an external `.en.srt` direct-plays and both appear in the sheet; switching shows the right text within one line
- [ ] A PGS subtitle on a direct play renders as bitmap cues; on a transcode the sheet shows "Burned in" and the picture has the subtitle
- [ ] Changing the audio track on a direct play switches without a new stream; on a transcode it re-resolves and continues at the same position
- [ ] Subtitles on an HLS transcode started from a Resume point are in sync (the plan's risk row)
- [ ] The web UI's session shows the chosen stream indexes
- [ ] A choice made on the movie page is used when Play is tapped; a season choice (Romanian subtitles) applies to every episode of that season and not to the next season; an episode's own choice wins over its season's; clearing falls back to Settings

## Required tests
- [ ] Unit (`DeviceProfileBuilderTest`): subtitle profiles per switch; `matchTrack` rows (same language twice, no language, external vs embedded)
- [ ] Unit: `resolveChoice` rows (item indexes valid / stale, season by language with and without a match, forced flag, subtitle off, no choice -> defaults)
- [ ] Database: `video_track_choice` DAO and the 12 -> 13 migration test; repository save / clear / season lookup
- [ ] Contract: `PlaybackInfo` with indexes; fixtures `items_playbackinfo/video_external_subtitles.json` (External + DeliveryUrl), `video_burn_in.json` (Encode), `video_hls_subtitles.json` (Hls); the external subtitle request `Stream.vtt?startPositionTicks=`; reports with indexes
- [ ] Playback (Robolectric): a media item with two `SubtitleConfiguration`s exposes two text groups; `selectTracks` applies the override; the re-resolve path keeps the position
- [ ] Unit: `VideoPlayerViewModel` tracks state and actions; `videoState.cues` forwards `CueGroup`
- [ ] Screenshot: tracks sheet (audio + subtitles, burned-in row, Off selected, "Apply to the whole season"), the movie page's Audio & subtitles row, player with a cue stand-in (the `MqSubtitles` slot replaced by text in previews)
- [ ] Robolectric UI: choosing a subtitle in the movie page's sheet calls `save` with the item id; the season menu's sheet saves with the season id

## Manual test
- [ ] Pixel 9: the three fixture files (embedded + external SRT; PGS; a transcode with subtitles), switch tracks mid-play, resume at 40 min with subtitles on; change the system caption size and see it apply; pick subtitles for a season and play two episodes

## Notes
**Skills:** `media3`, `jellyfin`, `architecture`, `expressive-design`, `testing`

**Depends on:** V4

New R lines expected: subtitle start offset on transcodes, `DeliveryUrl` carrying `ApiKey`, `mov_text` delivery.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## V6. Home: Continue watching and Next up; auto-play next episode; live resume updates

Labels: `type:feature`, `area:ui`, `area:data`

## Goal
Home shows "Continue watching" (movies and episodes with a progress line) and "Next up" (the next episode of each
series in progress), refreshed from the server when Home opens; an episode ends with a 10 s "Next episode"
countdown in the player (cancel or play now); watched state and positions change live through the WebSocket.

## Scope
- `:core:data`: `VideoRepository.continueWatching()` / `nextUp()` read Room and refresh from `GET /UserItems/Resume?mediaTypes=Video&includeItemTypes=Movie,Episode&limit=20` and `GET /Shows/NextUp?limit=20&enableResumable=false` when Home opens or a video stops (`LibrarySource.resumeVideos`, `nextUp`; mappers write positions and played flags into the `video` rows); `HomeSection.ContinueWatching`, `NextUp`, `RecentlyAddedMovies`, `RecentlyAddedEpisodes` (`HomeKind.Videos`, display Row of `MqPosterCard` / 16:9 cards with a progress line; listed only when the app mode includes video, V2), gear settings as the other sections (#127); `LiveSync` applies `UserDataChanged` to video rows (position, played).
- `:core:playback`: when a video item ends and auto-play is on, the next episode (from `VideoRepository.nextEpisode(id)`, pure order) is appended before the end so the transition is gapless for the player; the countdown is UI: `videoState.endingIn` from the last 10 s, Cancel keeps the appended item from playing (`removeMediaItem`), "Play now" seeks to it.
- `:feature:home`: the sections; `:feature:video`: the countdown card in the player.

## Acceptance criteria
- [ ] After watching 20 min of a movie and leaving, Home shows it under Continue watching with the right progress; the web UI agrees
- [ ] Next up shows the episode after the last watched one per series; playing it removes it and the next appears after it ends
- [ ] The countdown appears 10 s before the end, Cancel stops at the credits, Play now jumps; with auto-play off nothing is appended
- [ ] Marking an episode watched in the web UI updates the row and Next up within seconds (socket), without Sync now

## Required tests
- [ ] Contract: exact query of `/UserItems/Resume` and `/Shows/NextUp`; fixtures `user_items_resume/page.json`, `shows_next_up/page.json`; parse into positions
- [ ] Repository: refresh writes positions and played into existing rows and ignores unknown ids; `nextEpisode` rows (last of season -> first of next; last of series -> none; specials skipped)
- [ ] Unit: `HomeViewModel` with the new sections (content, empty hidden, gear order); `VideoPlayerViewModel` countdown states with the test scheduler
- [ ] Playback (Robolectric): the next item is appended at T-10 s with auto-play on and not with it off; Cancel removes it
- [ ] Screenshot: Home with the two new sections; the countdown card

## Manual test
- [ ] Pixel 9: watch to the end of an episode, let it continue, cancel once; check Home and the web UI; mark watched in the web UI and watch the row change

## Notes
**Skills:** `architecture`, `jellyfin`, `media3`, `expressive-design`, `testing`

**Depends on:** V4

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## V7. Settings -> Video: quality per network, HDR and Dolby Vision from real capabilities, subtitle and audio defaults

Labels: `type:feature`, `area:playback`, `area:ui`

## Goal
Settings -> Playback -> Video exists: video quality on Wi-Fi and on mobile data (Original, 1080p, 720p, 480p,
360p; default Original on Wi-Fi and **480p** on mobile data), preferred audio language, subtitle mode and language,
burn in styled subtitles, auto-play next episode, picture-in-picture (used in V9), keep audio when leaving the
player. The device profile declares HDR10 / HLG / HDR10+ / Dolby Vision only when the Pixel 9's decoders and display
really support them, and the player's info line says what plays (range, codec, play method).

## Scope (ADR 0050)
- `:core:data`: `VideoPreferencesRepository` (DataStore `video.*` keys; defaults: Original on unmetered, 480p on metered, audio language = phone locale, subtitles Forced only, burn-in styled off, auto-play on, PiP on, keep audio off); `VideoQuality(maxHeight, bitrate)`: Original (no cap), 1080p / 10 Mbps, 720p / 4 Mbps, 480p / 1.5 Mbps, 360p / 0.7 Mbps (the plan's ladder; the issue may align it with Jellyfin web's quality menu after checking its source); included in Backup (#150).
- `:core:playback`: `DisplayHdr` (snapshot of `Display.getHdrCapabilities().supportedHdrTypes` for the activity's display, re-read on the next resolve), `VideoDecoders` with profile levels (`HEVCProfileMain10HDR10`, `HEVCProfileMain10HDR10Plus`, `VP9Profile2HDR`, `DolbyVisionProfile*`); `DeviceProfileBuilder.video` conditions `VideoRangeType EqualsAny <supported>` (SDR always; HDR10 / HLG with a Main10 decoder and the display type; HDR10Plus with the HDR10+ profile; `DOVI*` only with a `video/dolby-vision` decoder and `HDR_TYPE_DOLBY_VISION`; `DOVIWithHDR10` also when HDR10 is supported and the file's `DvBlSignalCompatibilityId` says HDR10-compatible, decided from the `VideoFormat` of the item, a pure rule); `VideoQuality` per network kind in `StreamResolver.resolveVideo` (`MaxStreamingBitrate` = the bitrate, a `CodecProfile` condition `Height LessThanEqual maxHeight`, so larger files transcode scaled down); `videoState.format` (what plays: codec, width x height, range, play method, decoder name from `onVideoDecoderInitialized`).
- `:feature:settings`: the Video page (`VideoSettingsKey`), choice dialogs like the Decoding page (#138); the Decoding page gets a link "Video settings".
- `:feature:video`: an info line in the player's controls (long-press the title: "HEVC 3840 x 2160 · HDR10 · Direct play · c2.exynos.hevc.decoder") and a note "Converted by the server" when the method is Transcode; a "Dolby Vision not available on this phone · playing HDR10 / SDR" note when the file is DV and the profile could not declare it.
- Record the Pixel 9's decoders, profile levels and display HDR types in the media3 skill (a debug log line at the first resolve prints them).

## Acceptance criteria
- [ ] On mobile data an SDR 4K HEVC movie transcodes to the 480p HLS stream; raising the setting to Original direct-plays it (Wi-Fi off, Tailscale over mobile)
- [ ] **HDR10 file**: direct play with an HDR picture when the phone reports `HDR_TYPE_HDR10` and a Main10 HEVC decoder (expected on the Pixel 9); otherwise a server transcode (tone-mapped) and the note says so
- [ ] **Dolby Vision profile 8 file** (HDR10-compatible base layer): direct play; with a `video/dolby-vision` decoder it plays as DV, without one Media3 picks the HEVC decoder for the base layer and it plays as HDR10 (the info line shows which decoder ran)
- [ ] **Dolby Vision profile 5 file** (no compatible base layer): direct play as DV only with a DV decoder and `HDR_TYPE_DOLBY_VISION`; otherwise the profile does not declare `DOVI`, the server transcodes (dashboard shows it, tone-mapped), and the note "Dolby Vision not available on this phone" shows; never a black or purple picture
- [ ] Subtitle default Language = Romanian picks the Romanian track when there is one, Forced only picks only forced tracks, Off picks none; audio default follows the setting
- [ ] Every setting survives Backup & restore

## Required tests
- [ ] Unit (`DeviceProfileBuilderTest`): the range table (display types x decoder profiles x file range -> declared ranges); quality per network rows; burn-in switch
- [ ] Unit: `VideoPreferencesRepository` DataStore round trip and defaults; `VideoSettingsViewModel` each setting
- [ ] Unit: `defaultChoice(streams, preferences)` rows (forced, language, off, no match -> server default)
- [ ] Contract: `PlaybackInfo` request with `CodecProfiles` conditions for two capability sets (SDR only; HDR10 + DV); fixture `items_playbackinfo/video_hdr_tonemap.json`
- [ ] Screenshot: the Video settings page, each dialog, the player info line and the two notes

## Manual test
- [ ] Pixel 9: read the logged decoders and display types; play an HDR10 file, a DV profile 8 file and a DV profile 5 file on Wi-Fi (expected results above) and one of them on mobile data at 480p; change the subtitle default and reopen an episode

## Notes
**Skills:** `media3`, `jellyfin`, `architecture`, `expressive-design`, `testing`

**Depends on:** V5

The media3 skill's Video section records the phone's capabilities and the range table. No performance measurements (owner rule).

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## V8. Chapters, trickplay seek previews, and Skip intro / Skip credits from Intro Skipper segments with their settings

Labels: `type:feature`, `area:ui`, `area:jellyfin`, `area:playback`

## Goal
The seek bar shows chapter marks and a chapter list opens from the controls; dragging the seek bar shows the
trickplay thumbnail of that position when the server has trickplay images; while an intro, recap, preview or
credits segment plays (written by the owner's Intro Skipper plugin into Jellyfin's media segments) a "Skip intro" /
"Skip recap" / "Skip credits" button appears, or the segment is skipped by itself, per Settings -> Playback -> Video
-> Skipping.

## Scope
- `:core:server` / `:provider:jellyfin`: `LibrarySource.playerExtras(server, id)` = one `GET /Items?ids=&fields=Chapters,Trickplay` -> `Chapter`s and `TrickplayInfo` (per media source, the width closest to 320 px chosen by a pure function), `segments(server, id)` = `GET /MediaSegments/{itemId}?includeSegmentTypes=Intro,Recap,Preview,Commercial,Outro` -> `MediaSegment`s (Jellyfin's native table; Intro Skipper mirrors its detections there, its "Credits" arriving as `Outro`, to confirm with the fixture and recorded as an R line; the plugin's own `GET /Episode/{itemId}/Segments` and the `/Intros/*` routes are not used, and no legacy `IntroTimestamps` route exists on Jellyfin 12), `trickplayTileUrl(baseUrl, id, width, index)`; the tile request goes through the **authenticated** client (the route has a security entry, unlike images) and the URL carries no token; fixtures `items/chapters_trickplay.json`, `media_segments/intro_credits.json` (recorded from an episode the plugin analyzed), `media_segments/none.json`.
- `:core:data`: `VideoRepository.playerExtras(id)` (memory cache per session; offline -> empty); `VideoPreferences.skipIntro` / `skipCredits` / `skipRecapPreview`: Off / Show button / Skip automatically (defaults: intro Show button, credits Show button, recap and preview Show button); the player's countdown to the next episode (V6) starts when an `Outro` segment is skipped.
- `:feature:video`: chapter marks on `MqVideoControls`' seek bar, a chapters sheet (name, time, chapter image from `/Items/{id}/Images/Chapter/{index}?tag=`), thumbnail preview above the thumb while dragging (tile crop: `index = thumbnail / (tileWidth * tileHeight)`, cell = remainder; Coil request with the auth client; a 320 px tile is about 100 KB, fetched on drag only); a pure `segmentAction(position, segments, preferences, alreadySkipped)` -> show button / seek to `EndTicks` / nothing; the button appears at `StartTicks`, hides 8 s later unless the controls are shown, and never shows twice for one segment; automatic skip seeks once at `StartTicks + 1 s` with a short "Skipped intro" toast; `:feature:settings`: the Skipping group on the Video page (three choices x three segment kinds).

## Acceptance criteria
- [ ] A movie with chapters shows marks and the sheet jumps to a chapter; a file without shows neither
- [ ] Dragging shows the matching thumbnail on a file with trickplay; no requests leave while not dragging
- [ ] An episode analyzed by Intro Skipper shows Skip intro at the intro's start and skipping lands at its end; with "Skip automatically" the intro is skipped once without a button; with Off nothing shows; the credits segment shows Skip credits and, when auto-play is on, skipping it opens the next-episode countdown
- [ ] An episode the plugin has not analyzed (no segments) shows no button and no error
- [ ] Offline (downloaded video, V10): no chapters, no previews, no skip, no error

## Required tests
- [ ] Contract: the `ids` + `fields` request, the segments request with `includeSegmentTypes`; parse of the fixtures (the `Trickplay` nested map; `Outro` for the plugin's credits); tile URL shape and the auth header on the tile request
- [ ] Unit: tile index / cell math rows; width choice; `segmentAction` rows (each kind x each setting, already skipped, seeking back into a skipped intro, controls shown); chapter-at-position rule
- [ ] Unit: `VideoPlayerViewModel` extras loading, skip action, automatic skip, drag preview state; `VideoSettingsViewModel` the three choices
- [ ] Screenshot: seek bar with marks, chapters sheet, preview bubble, Skip intro and Skip credits buttons, the Skipping settings group

## Manual test
- [ ] Pixel 9: a movie with chapters and trickplay (enable trickplay on the server for one library); an episode with an intro and credits from Intro Skipper with each of the three settings; an episode without segments

## Notes
**Skills:** `jellyfin`, `media3`, `expressive-design`, `architecture`, `testing`

**Depends on:** V5, V7 (the Video settings page)

The owner's server runs the Intro Skipper plugin for Jellyfin 12 (its routes are in the OpenAPI 12.3.0 copy); the jellyfin skill's video section documents that clients read Jellyfin's `/MediaSegments`, not plugin routes.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## V9. Picture-in-picture and leaving the player: auto-enter on the home gesture, PiP controls, background rules

Labels: `type:feature`, `area:ui`, `area:playback`

## Goal
Swiping home while a video plays puts it in a PiP window that keeps playing with subtitles and play / pause and
next-episode actions; tapping the window returns to the player. Back pauses and leaves (or keeps the audio when the
setting says so). Rotating, PiP resize and the single activity never restart playback.

## Scope (ADR 0049)
- `:app`: `MainActivity` manifest `android:supportsPictureInPicture="true"`, `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"` (check the existing value); `PipController` (platform API: `PictureInPictureParams.Builder().setAutoEnterEnabled(enabled).setSourceRectHint(rect).setAspectRatio(ratio).setSeamlessResizeEnabled(true).setActions(...)`, updated whenever the player screen reports its surface bounds, aspect ratio or play state; `RemoteAction`s play / pause and next episode sent to the session through `MediaButtonReceiver`-style pending intents on the session's media buttons); `rememberIsInPipMode()` from `addOnPictureInPictureModeChangedListener`; auto-enter is enabled only while the video player screen is shown and the setting is on.
- `:feature:video`: in PiP mode the screen composes only `PlayerSurface` + `MqSubtitles`; `LocalPipMode` for the shell (no bars); orientation lock released in PiP; "keep audio when leaving" (V7 setting) decides whether Back pauses or continues; the surface is cleared on leave in both cases (audio continues without a surface).
- `:core:playback`: nothing new beyond `PlaybackConnection.next()` already working for the appended episode.

## Acceptance criteria
- [ ] Home gesture during playback -> PiP window with the video and subtitles, no app chrome; tapping it opens the player screen at the same position
- [ ] PiP actions play / pause and Next work; the notification stays in step
- [ ] Back with the setting off pauses and shows the movie page; with it on the audio continues and the mini player shows the video
- [ ] Rotation, entering and leaving PiP, and a resize never re-prepare the stream (one play session in the dashboard)

## Required tests
- [ ] Unit: `PipController` params as a pure builder function (enabled, rect, ratio, actions per state); the leave rule (setting x playing -> pause / continue)
- [ ] Robolectric UI: `VideoPlayerScreen` in PiP mode shows only the surface slot and cues; `rememberIsInPipMode` toggles on the listener
- [ ] Screenshot: the PiP-mode composition at the PiP window size (a small preview size)
- [ ] Device (androidTest): `isInPictureInPictureMode` becomes true after `enterPictureInPictureMode` with the params on the Pixel 9 (smoke)

## Manual test
- [ ] Pixel 9: PiP from a playing movie, pause and resume from the window, resize, tap back in; Back with and without the keep-audio setting; rotate during playback

## Notes
**Skills:** `architecture`, `expressive-design`, `media3`, `testing`

**Depends on:** V5, V7

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## V10. Video downloads and offline: movies, episodes and seasons as originals, external subtitles beside, offline lists

Labels: `type:feature`, `area:download`, `area:ui`

## Goal
Download a movie, a single episode or a whole season (no "Download series"; Wi-Fi only by default, as music); downloaded videos play with no network with
their embedded and downloaded external text subtitles and resume positions; the Downloads tab lists Movies and
Seasons with sizes; offline mode lists only downloaded movies, series and episodes.

## Scope (ADR 0050, plan section 5 rule 8)
- `:core:playback`: `DeviceProfileBuilder.videoDownload(audioDecoders, videoDecoders, hdr)` = the Original direct-play lists, no transcoding profile; `StreamResolver.downloadVideoStream(id)` fails with `MqError.Client(0, "download: the server cannot send this video as it is")` when the answer is not a direct play (the Downloads tab shows "This video cannot be downloaded on this phone"); external text subtitle deliveries are extra `DownloadRequest`s with key `<MediaId>:sub:<index>` (small files); `VideoMediaItems` for a downloaded video uses `mediatheq://sub/<id>/<index>` URIs the resolving data source serves from the download cache; `playLocal` records the session as for music.
- `:core:download`: `DownloadOrigin.Movie(id)`, `Season(id)` (origins table kinds, no schema change), `TrackDownload` becomes `MediaDownload` with a `Video?` (or a parallel `VideoDownload` list; decide in the issue for the smallest diff), `completedIds` includes video ids; `DownloadedCovers` keeps a poster per movie / season like album covers; cleanup (#149) counts subtitle keys with their video.
- `:core:data`: `OfflineFilter` parameters on every `VideoDao` list; `VideoRepository.offline`.
- `:feature:video`: Download / Remove on the movie page, in the season menu (`DownloadOrigin.Season`) and in the episode row's menu and swipe action (`DownloadOrigin.Single`); no series-level download; `:feature:downloads`: Movies and Seasons collections (#168 grouping; single episodes under their season with the single origin, like single tracks under albums), episode rows with sizes, the failed reason text; in Video mode (V2) the tab lists only video collections.
- Settings -> Offline, cache, and download -> Media: a line "Videos download as originals".

## Acceptance criteria
- [ ] A movie with an external SRT downloads (two keys), plays in airplane mode with the subtitle, seeks, and reports its position when online again (the pending reports rule of music applies)
- [ ] A season downloads episode by episode (Wi-Fi only honored); the Downloads tab shows the season with its total size; removing the season removes every episode and its subtitles; a single episode downloaded on its own survives removing the season's origin (origin rules as for tracks)
- [ ] A video the phone cannot direct-play shows the "cannot be downloaded" message and no partial file
- [ ] Offline mode: Movies and Series lists show only downloaded items; Home's video sections show only downloaded ones; Continue watching positions come from Room

## Required tests
- [ ] Unit: `videoDownload` profile shape; the failure rule; subtitle key scheme parse / format
- [ ] Download (Robolectric): a video download with two subtitle keys completes and `completedIds` holds the video id; removing the origin removes the subtitle keys; a transcode answer leaves no cache entry
- [ ] Playback (Robolectric): a downloaded video item plays from the cache with `SubtitleConfiguration`s on the `mediatheq://sub/` URIs and no network
- [ ] Database: every `VideoDao` list with the offline parameters
- [ ] Unit: `DownloadsViewModel` grouping with Movies and Seasons; the movie / series ViewModels' download actions
- [ ] Screenshot: Downloads tab with video collections; episode row downloaded state; the cannot-download message

## Manual test
- [ ] Pixel 9: download a movie, a single episode and a season on Wi-Fi, airplane mode, play them with subtitles, remove the season (the single episode stays); try a DV profile 5 file (expected: "cannot be downloaded" unless the phone has a DV decoder)

## Notes
**Skills:** `media3`, `architecture`, `expressive-design`, `testing`

**Depends on:** V5, V6

ADR 0024 gets its "no converted downloads for video" amendment; ADR 0021 a note on the video lists.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## V11. Mobile 3 exit: a movie and a season watched, skills and ADRs final, a week of watching

Labels: `type:chore`, `area:playback`, `area:jellyfin`

## Goal
Close the milestone the way Mobile 1 closed (#72): the manual tests of V3-V10 passed on the Pixel 9, a full movie and
a full season were watched with the app (one of them downloaded, one in PiP, one on mobile data), resume points
agree with the web UI, the skills and ADRs describe the final state, and the owner used the app for a week of
watching. Everything in the way is filed as a bug here or moved to TV 2 · Video / a Polish milestone.

## Scope
- Skills: the `media3` Video section complete (capabilities of the Pixel 9 recorded), `jellyfin` video section and R lines (Intro Skipper segments included), `architecture`, `expressive-design`, `testing` edits listed in the plan's section 12 that earlier issues did not already make; ADRs 0047-0051 already Accepted; ADR 0021 / 0024 amendments.
- Fixture refresh: every new `jellyfin/` fixture re-recorded against the owner's server and the diff committed (expected: none).
- A note in the plan's sections 9-10 with the owner's answers; plan status Accepted.
- Candidate list for TV 2 · Video and for the Media3 1.12 bump (`rememberCuesState` replacing the cue listener, `videoAspectRatio`, the DV profile 7 fallback), filed as issues in their milestones.

## Acceptance criteria
- [ ] Test issues of V2-V10 closed; the music regression checklist of the Tests milestone that touches playback, queue restore, downloads and Home passes once more in Music mode and in Both
- [ ] A week of watching logged in a comment: what worked, what did not, what was missed (candidates for TV 2 or Polish)
- [ ] No open `type:bug` in the milestone, or each is triaged to a later milestone with a reason
- [ ] Every open `R<n>` of the jellyfin skill's video section has a fixture and a test

## Required tests
- [ ] All provider contract suites green after the fixture refresh
- [ ] None new otherwise (tracking issue)

## Manual test
- [ ] Daily watching on the Pixel 9 for seven days in Both mode: at least one episode a day, one movie downloaded, one PiP session, one mobile-data session at 480p, one HDR10 and one DV file, Skip intro used; resume points checked in the web UI at the end

## Notes
**Skills:** `architecture`, `media3`, `jellyfin`, `testing`, `expressive-design`

**Depends on:** V6, V7, V8, V9, V10

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)
