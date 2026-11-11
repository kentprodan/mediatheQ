# Servers 1 · Navidrome: issue drafts

Drafts for the milestone `Servers 1 · Navidrome`, in order. Each is one branch, one PR. Numbers `S1`-`S10` are
placeholders until the issues exist on GitHub; "Depends on" uses them. Labels proposed per issue. All follow the
format of #146 / #147. Plan: [servers1-navidrome.md](servers1-navidrome.md). Manual tests run on the Pixel 9 against
the pinned Navidrome test server (`tools/navidrome`, S1) unless the item says otherwise, and again against the owner's
own Navidrome server.

---

## S1. `:provider:subsonic` skeleton, `subsonic` skill, Navidrome test server, auth and discovery

Labels: `type:feature`, `area:subsonic`, `area:ci`

## Goal
The Subsonic provider module exists with its HTTP client, authentication and server discovery, built and tested by
CI; a pinned Navidrome runs locally for recording fixtures and for manual tests; the `subsonic` skill tells every
later issue how the provider is written. Nothing is wired to the UI yet.

## Scope
- `:provider:subsonic` with the existing provider convention plugin; `SubsonicHttp` (GET, and POST form when `formPost` is listed; `f=json`; `subsonic-response` envelope; `status: failed` -> `MqError` per ADR 0046; GET retries like `JellyfinHttp`), `SubsonicAuthInterceptor` (ADR 0046: `u`, `t = md5(password + s)`, fresh 16-hex salt per request, `v=1.16.1`, `c=mediatheQ`, `f=json`; only `/rest/*` on the signed-in host), `SubsonicAuthSource` (`serverInfo` = `ping` without credentials for reachability and `openSubsonic` / `type` / `serverVersion`, then `ping` with credentials for `login`; `getOpenSubsonicExtensions` -> `SubsonicCapabilities`; Quick Connect methods answer `MqError.Client(0, "unsupported")`; `logout` is local only).
- `:core:network`: `Set<Interceptor>` multibinding (`@Multibinds`) added to the shared client; `:provider:subsonic` contributes `@IntoSet`.
- `tools/navidrome/compose.yaml` pinned to `navidrome/navidrome:0.64.2`, `tools/navidrome/music/` with 6-10 generated CC0 files (ffmpeg sine waves; FLAC 16/44, FLAC 24/96, MP3 320, Opus, one 5.1 FLAC; tags: two album artists, one credit-only artist, two genres, one record label, embedded synced lyrics in one file, ReplayGain tags), `tools/navidrome/strip.py` (removes `t`, `s`, host names from recorded responses), `docs/dev/navidrome-test-server.md` (start, first user `mq`, Players page, recording a fixture, the plain binary alternative).
- `.claude/skills/subsonic/SKILL.md`: sections as in `jellyfin` (source of truth with spec and Navidrome version and dates, client design, auth, library, images, stream, reporting, user data, playlists, lyrics, errors, R-list, done checklist). First R lines come from this issue (e.g. `R1 No header auth`, `R2 Errors are HTTP 200`).
- Fixtures: `subsonic/ping/open_subsonic.json`, `ping/plain_subsonic.json`, `ping/wrong_password.json` (error 40), `get_open_subsonic_extensions/navidrome_0_64_2.json`.

## Acceptance criteria
- [ ] `.\gradlew.bat :provider:subsonic:testDebugUnitTest` passes; CI is green with the module (no workflow change)
- [ ] `SubsonicAuthSource.serverInfo` on the test server returns `isSupported = true`, name `Navidrome`, version `0.64.2`; on a non-OpenSubsonic answer `isSupported = false`
- [ ] `login` with the test user succeeds and with a wrong password answers `MqError.Unauthorized`
- [ ] No URL built by the module contains `t`, `s`, `p`, `u` or `apiKey` (asserted by a test over every request shape)
- [ ] The `subsonic` skill exists and the architecture skill module map lists `:provider:subsonic`

## Required tests
- [ ] Unit: `md5(password + salt)` against the spec example; salt length and randomness; `SubsonicError` code -> `MqError` table (0, 10, 20, 30, 40, 41, 42, 43, 44, 50, 70)
- [ ] Contract (MockWebServer): exact form body of a `formPost` request and exact query of the fallback; `ping` with and without credentials; `getOpenSubsonicExtensions` sent without credentials; a `status: failed` body with HTTP 200 maps to the right `MqError`; interceptor leaves non-`/rest/` and other-host requests untouched
- [ ] Mapper: `PingDto.toServerInfo()`, `ExtensionsDto.toCapabilities()` (songLyrics, formPost, transcodeOffset, transcoding, playbackReport, apiKeyAuthentication recognized; unknown ignored)

## Manual test
- [ ] Windows: `docker compose -f tools/navidrome/compose.yaml up`, create user `mq` in the web UI, run the fixture-recording commands from the doc and diff the result with the committed fixtures

## Notes
**Skills:** `architecture`, `testing`, `jellyfin` (as the model), new `subsonic`

**Depends on:** none

ADR 0042 and 0046 are accepted in this branch. The architecture skill (module map, section 10) and the testing skill (section 4: Subsonic fixtures, test server) are updated in the same PR.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## S2. Provider-neutral core: `ProviderKind`, `ServerCapabilities`, `MediaServer` registry, provider stored with the account

Labels: `type:chore`, `area:data`, `area:jellyfin`

## Goal
The app knows which provider each account uses and gets every server source from the active account's provider.
Jellyfin behaves exactly as before; the Subsonic provider is registered but no screen can sign in to it yet.

## Scope (ADR 0043, 0044)
- `:core:model`: `ProviderKind`; `ServerInfo.isSupported` as a field set by the provider (Jellyfin mapper: major >= 12).
- `:core:server`: `ServerCapabilities`, `MediaServer`; `AuthSource.login` gains `existingServerId: ServerId? = null` (ADR 0044; Jellyfin ignores it).
- `:provider:jellyfin`: `JellyfinCapabilities` (quickConnect, liveEvents, syncedLyrics, ratingSteps 10, reportsProgress, spatialAudioInfo true, seekableConversions true, maxIdsPerRequest 200); `JellyfinModule` binds `@IntoMap ProviderKind.Jellyfin -> MediaServer` instead of the seven direct bindings. `:provider:subsonic` binds its `MediaServer` with the sources that exist (S1) and stubs that answer `MqError.Client(0, "not implemented")` for the rest, replaced in S4-S7.
- `:core:datastore`: `StoredAccount.provider` (`provider@<id>`, missing = Jellyfin); `AccountStore` tests for the default.
- `:core:data`: `ProviderRegistry`, `ActiveProvider` (active account's kind, or the kind being signed in), the seven `Active*` delegates bound as the source interfaces; `SessionRepository.checkServer(address, kind)` and `login(..., kind)`; `capabilities: Flow<ServerCapabilities?>` on `SessionRepository`; `LiveSync` returns early when `liveEvents` is false; Subsonic duplicate detection by kind + normalized host + user name; `ServerId("sub-<uuid>")` generated when none is saved.
- `:core:testing`: `FakeMediaServer(capabilities, ...)`.
- Picker (`ProviderPickerBody`) still lists Jellyfin only (S3 adds the entry) but takes a `ProviderKind` in `onPick`.

## Acceptance criteria
- [ ] The phone app signs in, syncs, plays, favorites and edits playlists on Jellyfin exactly as before (regression run of the existing tests, no fixture changes)
- [ ] An account saved before this change reads as Jellyfin after the update
- [ ] `ActiveProvider` resolves once per process and `LiveSync` never connects for a provider with `liveEvents = false`
- [ ] A Subsonic sign-in through `SessionRepository.login(kind = Subsonic)` (from a unit test) saves an account with a `sub-` server id and reuses it the second time

## Required tests
- [ ] Unit: `ActiveProvider` picks by the account's kind and by the onboarding kind; delegates forward every method (one test per interface with two fake servers); duplicate rule (same host and user -> same account; other address -> new account; "Edit address" keeps the id)
- [ ] DataStore: `provider@<id>` written and read; missing value -> Jellyfin
- [ ] Repository: `LiveSync` with `liveEvents = false` does nothing; `SessionRepository.capabilities` follows the active account
- [ ] Contract: Jellyfin tests unchanged and green

## Manual test
- [ ] Pixel 9: update over the current build with a signed-in Jellyfin account; the library, queue and downloads are still there; sign out and in again

## Notes
**Skills:** `architecture`, `jellyfin`, `testing`

**Depends on:** S1

ADR 0043 and 0044 are accepted in this branch; the architecture skill section 4 (provider interface, delegates) is updated.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## S3. Onboarding per provider: picker entry, Subsonic sign-in, capability-gated Quick Connect

Labels: `type:feature`, `area:ui`, `area:subsonic`

## Goal
From Settings -> Manage media providers -> Add media provider, the owner picks "(Open)Subsonic / Navidrome", enters
the address, user name and password, and lands on an empty, syncing library of that account (inactive beside the
Jellyfin one, or active when it is the first account). Error texts are provider-neutral.

## Screens (`:feature:onboarding`)
- `OnboardingServerKey(provider)`; the server screen title and hint name the provider ("Navidrome or another OpenSubsonic server", address example `http://navidrome.local:4533`); `ServerError.NotJellyfin` becomes `NotThisProvider` with a text per kind; `Unsupported` says "This server does not answer as an OpenSubsonic server" for Subsonic.
- Login screen: the same fields; the Quick Connect button only when `capabilities.quickConnect`; the error for code 40 is the existing wrong-password text; 41 / 42 show the server's `message` and `helpUrl` as plain text.
- `ProviderPickerBody` lists Jellyfin and (Open)Subsonic / Navidrome with the `MqIcons` logo placeholder of each; the account row shows the provider name under the address.
- First-run onboarding (no account) starts with the picker too (today it jumps to the Jellyfin server screen).

## Acceptance criteria
- [ ] Add a Navidrome account beside the active Jellyfin one; both listed with their provider; Make active restarts into the Navidrome account (empty library until S4)
- [ ] A wrong password keeps the fields and shows the message; an address of a plain web server shows "not an OpenSubsonic server"
- [ ] Quick Connect is not shown for a Subsonic server and still shown for Jellyfin
- [ ] Fresh install: picker -> Subsonic -> sign-in -> Home

## Required tests
- [ ] Unit: `ServerViewModel` / `LoginViewModel` with `FakeMediaServer` for both kinds: each error, the hidden Quick Connect, the kind passed to `SessionRepository`
- [ ] Screenshot: picker with two providers, server screen per kind, login without Quick Connect, accounts list with mixed providers (light/dark, 1.0/2.0)
- [ ] Robolectric UI: picking Subsonic opens the server screen with the Subsonic hint

## Manual test
- [ ] Pixel 9: add the test Navidrome over LAN (`http://<pc-ip>:4533`), switch to it, switch back to Jellyfin, remove the Navidrome account

## Notes
**Skills:** `architecture`, `expressive-design`, `subsonic`, `testing`

**Depends on:** S2

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## S4. Library sync on Subsonic: artists, albums, tracks, playlists, genres, labels, covers

Labels: `type:feature`, `area:subsonic`, `area:data`

## Goal
A Navidrome account's library appears in Home, Library (albums, album artists, tracks, playlists, genres, years,
record labels), Search and the album, artist, genre and playlist pages, with covers, exactly like a Jellyfin library.

## Scope (`SubsonicLibrarySource`)
- `albumArtists`: `getArtists` once (all indexes), album artists = entries whose `roles` contain `albumartist` (fallback when `roles` is missing: artists referenced by albums); sliced by `startIndex` / `limit` in memory, `totalCount` = list size. Credit-only artists come from tracks' `artists`, as on Jellyfin.
- `albums`: `getAlbumList2?type=alphabeticalByName&size=500&offset=`; map `genres`, `recordLabels`, `artists` / `displayArtist`, `year` / `originalReleaseDate`, `isCompilation`, `userRating`, `starred`, `playCount`, `played`, `duration`, `songCount`.
- `tracks`: `search3?query=&songCount=500&songOffset=` (OpenSubsonic empty query); map `track`, `discNumber`, `duration`, `size`, `contentType`, `suffix`, `bitRate`, `bitDepth`, `samplingRate`, `channelCount` -> `AudioFormat` (no spatial format), `replayGain`, `genres`, `artists`, `albumArtists`, `musicBrainzId`, `userRating`, `starred`, `playCount`, `played`.
- `playlists` / `playlist` / `playlistEntries`: `getPlaylists`, `getPlaylist?id=` (entries all at once, `entryId = "<index>"`), `readOnly` kept for S7.
- Ids: `SubsonicId` with kind prefixes `s/`, `al/`, `ar/`, `pl/` (plan section 5); covers: `Artwork(itemId = coverArt, tag = "")`, `imageUrl` = `<base>/rest/getCoverArt?id=&size=<rounded width>` (no credentials, ADR 0046), `image` through the client.
- Ratings mapped 1-5 -> 2, 4, 6, 8, 10.

## Acceptance criteria
- [ ] A full sync of the test library finishes and every list and page shows the right counts, names, covers, genres, labels and formats (24/96 badge on the hi-res file, 5.1 channel count)
- [ ] Library A-Z, year and label pages, Search (FTS) work on the Subsonic account without code changes outside the provider
- [ ] A second sync changes nothing (stable ids); a renamed album on the server shows after Sync now
- [ ] Covers load in lists and in Now Playing; no request without the interceptor's credentials reaches the server for covers (asserted in the contract test), no credentials in Coil's cache keys

## Required tests
- [ ] Mapper: every DTO -> domain function, including missing OpenSubsonic fields (plain Subsonic server), `roles` missing, `coverArt` missing (no artwork), rating round trip
- [ ] Contract: exact parameters of `getArtists`, `getAlbumList2` (type, size, offset), `search3` (empty query, counts, offsets), `getPlaylists`, `getPlaylist`, `getCoverArt` (id, size); fixtures `get_artists/page.json`, `get_album_list2/page.json`, `search3/songs_page.json`, `get_playlists/page.json`, `get_playlist/entries.json`, `get_cover_art/missing.xml` (error body with `text/xml`)
- [ ] Repository: `LibrarySyncRepository` with `FakeMediaServer(Subsonic)` upserts the same rows as with Jellyfin shapes (same sync code)

## Manual test
- [ ] Pixel 9: sync the test Navidrome, open each Library tile, Search for a track, open an artist and a genre; scroll covers in the album grid

## Notes
**Skills:** `architecture`, `subsonic`, `testing`

**Depends on:** S3

New R lines expected (cover id changes, `roles`, paging limits) go into the `subsonic` skill with their fixtures.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## S5. Playback on Subsonic: stream selection, Opus conversion, scrobble reporting

Labels: `type:feature`, `area:subsonic`, `area:playback`

## Goal
Tracks of a Navidrome account play: originals on Wi-Fi, Opus at the chosen quality on mobile data or for formats the
Pixel 9 cannot decode, with the queue, gapless, prefetch and cache behaving as on Jellyfin. Plays are scrobbled to the
server.

## Scope (ADR 0045, `SubsonicPlaybackSource`)
- `stream(server, itemId, profile)`: `getSong` then pure `chooseStream(song, profile)`: raw direct play (`DirectPlay`, `canSeek = true`, `sizeBytes`) or `maxBitRate` + `format=<profile.transcoding.codec>` (`Transcode`, `canSeek = false`, `segmented = false`); `mediaSourceId = itemId`, `playSessionId` = UUID.
- `streamHeaders` = empty (the interceptor authenticates); `PlaybackStream.url` without credentials.
- `reportStart` -> `scrobble?id=&submission=false`; `reportStopped` -> `scrobble?id=&submission=true&time=` when position >= min(half the duration, 4 min), else nothing; `reportProgress` -> `MqResult.Success(Unit)` without a request (`reportsProgress = false`).
- `:core:playback`: nothing new; verify `StreamResolver`, `InstantTranscoder` and `Prefetcher` with a `PlaybackStream` whose `segmented` is false and `canSeek` false (the progressive path of before ADR 0019).
- Now Playing: the Atmos note never shows on an account with `spatialAudioInfo = false`; the quality label shows the requested quality.

## Acceptance criteria
- [ ] On Wi-Fi the FLAC files play as originals (`DirectPlay`, seek works, cache key `<MediaId>:original` hits on replay)
- [ ] On mobile data (or with a 256 kbps cap) the FLAC plays as Opus; seeking is disabled and the player says so; the Navidrome Players page shows "mediatheQ" with the Opus transcode
- [ ] The 5.1 FLAC plays (decoded on the device) without any Atmos note
- [ ] A finished track raises its play count on the server and appears in Navidrome's Now Playing while playing

## Required tests
- [ ] Unit: `chooseStream` table (container and codec listed, bitrate over the cap, codec not listed, Original quality, converted-download profile)
- [ ] Contract: exact `stream` parameters for raw and for Opus 256; `getSong`; `scrobble` with `submission=false` / `true` and `time`; fixtures `get_song/flac.json`, `get_song/mp3.json`, `scrobble/ok.json`
- [ ] Playback (Robolectric, `media3-test-utils`): a `Transcode` stream with `canSeek = false` prepares the progressive source and the player reports `isSeekable = false`; a `DirectPlay` stream is written to the streaming cache
- [ ] Unit: the stop rule (half / 4 min) with durations of 1, 6 and 12 minutes

## Manual test
- [ ] Pixel 9: play an album from the test Navidrome on Wi-Fi, then with Wi-Fi off (mobile data) at 256 kbps; check the Players page and the play count on the server; prefetch the next track and replay it offline-cached

## Notes
**Skills:** `media3`, `subsonic`, `architecture`, `testing`

**Depends on:** S4

ADR 0045 is accepted in this branch; the media3 skill sections 3 and 12 are updated.

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## S6. User data on Subsonic: favorites, 5-step ratings, offline queue of changes, lyrics

Labels: `type:feature`, `area:subsonic`, `area:data`

## Goal
Favorites and ratings of tracks, albums and artists work on a Navidrome account, with whole stars; changes made
offline are sent later as on Jellyfin; synced lyrics show in Now Playing when the server has them.

## Scope
- `SubsonicUserDataSink`: `star` / `unstar` with `id`, `albumId` or `artistId` from the id's kind prefix; `setRating` with 1-5 from the app's 0-10 (2, 4, 6, 8, 10; odd values round up; `null` -> 0).
- `ratingSteps` reaches the rating UI: the dialog and the rows show whole stars for an account with 5 steps (the half-star mode setting is hidden for it).
- `pending_user_data` (#56) works unchanged; the sink maps `MqError.Network` the same way.
- `SubsonicLyricsSource`: with `songLyrics` -> `getLyricsBySongId?id=`; the first `structuredLyrics` entry (prefer `synced = true`, then the user's language, then the first), `line.start` ms -> `Duration`, `offset` applied; `synced = false` -> plain lines; no entry -> `null`. Without the extension -> `null` (`syncedLyrics = false`).

## Acceptance criteria
- [ ] Favorite a track, an album and an artist; the stars show in Navidrome's web UI; unfavorite from the web UI shows after Sync now
- [ ] Rate a track 4 stars: the server shows 4; the app shows 4 of 5 whole stars; clear removes it
- [ ] A favorite set in airplane mode is sent when the network returns
- [ ] The file with embedded synced lyrics shows synced lyrics in Now Playing; a file without shows "No lyrics"

## Required tests
- [ ] Contract: `star` / `unstar` with each of `id`, `albumId`, `artistId`; `setRating` 1-5 and 0; `getLyricsBySongId` synced, unsynced, empty; fixtures `star/ok.json`, `set_rating/ok.json`, `get_lyrics_by_song_id/synced.json`, `unsynced.json`, `none.json`
- [ ] Mapper: rating round trip 0-10 <-> 1-5; lyrics mapper (ms, offset, language choice)
- [ ] Unit: the rating dialog state with `ratingSteps = 5` (no half stars, the half-star setting hidden)
- [ ] Repository: `DefaultUserDataRepository` with the fake Subsonic server: optimistic write, revert on 50, pending on Network

## Manual test
- [ ] Pixel 9: favorite and rate on the test Navidrome, check the web UI, change a rating in the web UI and Sync now; open the lyrics file in Now Playing

## Notes
**Skills:** `subsonic`, `architecture`, `expressive-design`, `testing`

**Depends on:** S4

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## S7. Playlists on Subsonic: create, rename, delete, add, remove, reorder

Labels: `type:feature`, `area:subsonic`, `area:data`

## Goal
Playlist editing works on a Navidrome account with today's UI: create, rename, delete, add tracks, remove entries,
drag to reorder; read-only playlists (another user's, smart playlists) are shown but not editable.

## Scope (`SubsonicPlaylistEditor`)
- `create` -> `createPlaylist?name=` (no songs; tracks follow through `add`, one chunked path as on Jellyfin); `rename` -> `updatePlaylist?playlistId=&name=`; `deletePlaylist` -> `deletePlaylist?id=`; `add` -> `updatePlaylist` with `songIdToAdd` repeated, chunked at `maxIdsPerRequest` (or in one form POST when `formPost` is present); `remove(entryIds)` -> `songIndexToRemove` repeated, indexes sorted descending in one request; `move(entryId, newIndex)` -> `createPlaylist?playlistId=&songId=...` with the full new order (ADR 0045 plan, decision 8).
- Entries: `entryId = "<index>"`; the repository already refreshes the playlist after each edit (jellyfin R13), so the indexes are fresh.
- `Playlist.readOnly` from the OpenSubsonic field: the UI hides edit actions (a small `Playlist` field; Jellyfin sets false).

## Acceptance criteria
- [ ] Create a playlist, add 3 tracks, reorder, remove one, rename, delete: each step is visible in Navidrome's web UI at once
- [ ] A playlist owned by another Navidrome user (made public) is listed and plays, with no edit actions
- [ ] Adding 600 tracks works (chunked or form POST), the order matches

## Required tests
- [ ] Contract: exact parameters of each request; `songIndexToRemove` order; the full-order rewrite; chunking at 200 ids on the query path and a single form POST on the `formPost` path; fixtures `create_playlist/created.json`, `update_playlist/ok.json`, `get_playlist/read_only.json`
- [ ] Repository: `DefaultPlaylistRepository` with the fake Subsonic server: optimistic edits and refresh after each
- [ ] Screenshot: playlist page in read-only state

## Manual test
- [ ] Pixel 9: the create-add-reorder-remove-rename-delete flow on the test Navidrome; open a public playlist of a second test user

## Notes
**Skills:** `subsonic`, `architecture`, `testing`

**Depends on:** S4

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## S8. Downloads, offline mode and stale-sync on Subsonic

Labels: `type:feature`, `area:subsonic`, `area:download`

## Goal
Downloads (original or one converted Opus file), offline mode, automatic downloads of favorites and the downloaded
covers work on a Navidrome account; without live events the library refreshes at app start when the last sync is
older than 15 minutes.

## Scope
- `StreamResolver.downloadStream` with `DeviceProfileBuilder.download` on Subsonic: originals as `format=raw`, conversions as `maxBitRate` + `format=opus` progressive (one file, `CONVERTED_DOWNLOAD` bitrate, no continuation mid-file, like jellyfin R12). No provider change beyond S5; this issue verifies and fixes the download path, `DownloadedCovers` (through `LibrarySource.image`) and the Downloads tab labels.
- `LibrarySyncRepository`: sync at app start (`MainActivity` -> existing start hook) when `lastSyncAt` is older than 15 min, for every provider (plan decision 6).
- Settings -> Offline, cache, and download: no change; the Media page's "Converted copy" label shows the bitrate as today.

## Acceptance criteria
- [ ] Download an album as originals and another with Opus 256; both play with the network off; the converted one seeks offline (complete file)
- [ ] Downloaded album covers show offline; removing the last download of the album deletes its cover
- [ ] Automatic downloads of favorites run on the Subsonic account
- [ ] After adding an album on the server, reopening the app 15+ minutes after the last sync shows it without Sync now

## Required tests
- [ ] Unit: the stale rule (14 min no sync, 16 min sync, never synced -> sync, offline -> no sync)
- [ ] Download (Robolectric): a converted Subsonic download records `CONVERTED_DOWNLOAD` with the bitrate; a partial converted download is dropped on retry, an original continues
- [ ] Contract: the download `stream` request shapes (raw, Opus 256, Opus 192)

## Manual test
- [ ] Pixel 9: download two albums from the test Navidrome (one with Original, one with Opus 256), airplane mode, play both, check seeking and covers; favorite an album with automatic downloads on

## Notes
**Skills:** `media3`, `subsonic`, `testing`

**Depends on:** S5, S6

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## S9. Servers 1 exit: provider details in Settings, skills and ADR updates, full regression on both providers

Labels: `type:chore`, `area:subsonic`, `area:jellyfin`

## Goal
The milestone closes clean: Settings shows what each account's server is and can do, the skills and ADRs describe the
final state, fixtures are re-recorded against the pinned Navidrome, and the manual regression passes on the Pixel 9
for a Jellyfin and a Navidrome account side by side.

## Scope
- Settings -> Manage media providers: the account row shows provider and server version (`Navidrome 0.64.2` / `Jellyfin 12.3.0`); a Subsonic account's sheet lists what the server lacks (live updates, synced lyrics when `songLyrics` is missing, half-star ratings) in one sentence each, and links the Players page note of the `subsonic` skill in the help text.
- "Sign in again" on a Subsonic account opens the Subsonic login (S3) with the user name prefilled.
- Skills: `subsonic` R-list complete; `architecture`, `jellyfin`, `testing`, `media3` edits listed in the plan's section 12 that earlier issues did not already make; ADRs 0042-0046 Accepted, 0013 / 0015 amendments.
- Fixture refresh: re-record every `subsonic/` fixture against `navidrome/navidrome:0.64.2` with the doc's commands and commit the diff (expected: none).

## Acceptance criteria
- [ ] Both accounts listed with provider and version; switching back and forth keeps each library, queue, downloads and pending changes (ADR 0036 behavior on two providers)
- [ ] No Jellyfin regression: the full Jellyfin manual checklist of the Tests milestone that touches sign-in, sync, play, favorites, playlists, lyrics, downloads passes once more
- [ ] Every open `R<n>` of the `subsonic` skill has a fixture and a test
- [ ] Plan status set to Accepted; plan sections 9-10 updated with the owner's answers

## Required tests
- [ ] Screenshot: accounts list with provider and version, the Subsonic account sheet
- [ ] Unit: the capability sentences (each flag off -> one sentence; all on -> none)
- [ ] All provider contract suites green after the fixture refresh

## Manual test
- [ ] Pixel 9: Jellyfin and Navidrome accounts saved; for each: switch, sync, play on Wi-Fi and mobile data, favorite, rate, edit a playlist, lyrics, download, remove the account; finish on Jellyfin active

## Notes
**Skills:** `architecture`, `subsonic`, `jellyfin`, `media3`, `testing`, `expressive-design`

**Depends on:** S7, S8

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)

---

## S10 (optional, after a stable Navidrome ships `apiKeyAuthentication`). API key sign-in for Subsonic

Labels: `type:feature`, `area:subsonic`, `area:ui`

## Goal
When the server lists `apiKeyAuthentication`, the Subsonic login offers "API key" instead of a password: the key is
stored encrypted and sent as `apiKey` without `u`, `t`, `s`, so the password never reaches the phone.

## Scope (ADR 0046)
- Login screen: a segmented choice Password / API key shown only when `getOpenSubsonicExtensions` lists the extension; the key field with paste.
- `StoredAccount.secretKind` (`Password` / `ApiKey`, DataStore `secret_kind@<id>`, missing = Password); the interceptor sends `apiKey` and no `u` for such an account; `login` validates with `ping`.
- Error 44 -> `MqError.Unauthorized` with "The API key is not valid"; 43 is a programming error (test asserts it cannot be produced).

## Acceptance criteria
- [ ] Sign in with a key created in Navidrome's web UI; the library syncs and plays; revoking the key signs the account out at the next request
- [ ] A server without the extension shows the password form only

## Required tests
- [ ] Contract: `apiKey` request shape without `u`, `t`, `s`; error 44; fixtures `ping/invalid_api_key.json`
- [ ] Unit: `LoginViewModel` choice visibility by capability; `AccountStore` default `secretKind`
- [ ] Screenshot: login with the choice, in both modes

## Manual test
- [ ] Pixel 9: against a Navidrome release that lists `apiKeyAuthentication` (version to be filled in when it exists): create a key, sign in, revoke it, see the sign-out

## Notes
**Skills:** `subsonic`, `architecture`, `expressive-design`, `testing`

**Depends on:** S3; a Navidrome stable release newer than 0.64.2 with [navidrome#6219](https://github.com/navidrome/navidrome/pull/6219)

## Definition of Done
- [ ] All acceptance criteria met
- [ ] Done checklists of the listed skills pass
- [ ] CI green (build, unit tests, screenshot validation, lint)
- [ ] ADR written if a decision changed
- [ ] Manual test items queued with `/test` (if any)
