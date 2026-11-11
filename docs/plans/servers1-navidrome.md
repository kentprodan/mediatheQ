# Plan: Servers 1 · Navidrome

- Status: Proposed (owner approval pending)
- Date: 2026-11-11
- Milestone: `Servers 1 · Navidrome` (ADR 0025, order 6)
- Issue drafts: [servers1-issues.md](servers1-issues.md). ADRs: 0042-0046 (Proposed).

## 1. Goal

The phone app can use a **Navidrome** server through the **OpenSubsonic API** as a second media provider, next to
Jellyfin: sign in from the provider picker (#147), sync the library, play (original or converted), favorite, rate,
edit playlists, read lyrics, download. One active server at a time, no merged library (owner decision, #147). The
Jellyfin provider and every Jellyfin-only feature keep working unchanged.

Exit criterion (last issue): the contract tests run green against a pinned Navidrome, the manual checklist passes on
the Pixel 9 against that server, and switching Jellyfin <-> Navidrome in Settings loses nothing on either side.

## 2. What exists and what is reused

| Module | Reused | Notes |
|---|---|---|
| `:core:model` | extended | `ProviderKind`, `ServerInfo.isSupported` decided by the provider (today: Jellyfin major >= 12 in the model). |
| `:core:server` | extended | `ServerCapabilities`, `MediaServer` (one object per provider), KDoc of what each field means on a server without the concept. No Jellyfin type leaves `:provider:jellyfin` today, so the interfaces are nearly neutral already (section 5). |
| `:core:network` | one small change | OkHttp factory, `CleartextPolicyInterceptor` (ADR 0013), `Json`. `NetworkModule` gains a `Set<Interceptor>` multibinding (empty today, `@Multibinds`) that the client factory adds; the Subsonic provider contributes its auth interceptor `@IntoSet`. No new module edge. |
| `:provider:jellyfin` | as it is | registers itself as `ProviderKind.Jellyfin`; `JellyfinCapabilities` says what it does. No fixture or test changes. |
| `:core:datastore` | extended | `StoredAccount.provider` (`provider@<id>` key, missing = Jellyfin, so no migration). |
| `:core:data` | extended | `SessionRepository` takes the provider kind at sign-in; the repositories keep their `LibrarySource` / `PlaybackSource` / ... constructor parameters: `:core:data` binds them to delegates that forward to the active account's provider (ADR 0043). `LiveSync` skips a provider without live events. |
| `:core:database` | as it is | ids already carry the server (`MediaId`), one database per account (ADR 0036). No schema change. |
| `:core:playback`, `:core:download` | as they are | `StreamResolver`, `DeviceProfileBuilder`, cache keys, converted downloads all work on `PlaybackStream` fields; the provider fills `playSessionId` / `mediaSourceId` itself. The `segmented` flag is never set on Subsonic (no HLS). |
| `:feature:onboarding` | extended | the server screen knows which provider it validates; Quick Connect only when `capabilities.quickConnect`. |
| `:feature:settings` | extended | the picker lists (Open)Subsonic / Navidrome; the account row shows the provider; ratings show 5 steps where `ratingSteps == 5`. |
| `:feature:nowplaying`, others | as they are | capability-driven differences arrive through `UiState` fields that exist (rating steps, lyrics present or not, Atmos note). |

## 3. Research summary (official sources, checked 2026-11-11)

### OpenSubsonic / Subsonic API

- OpenSubsonic extends the Subsonic API; every endpoint answers a `subsonic-response` envelope with `status`, `version`, and on OpenSubsonic servers `type`, `serverVersion`, `openSubsonic: true` ([ping](https://opensubsonic.netlify.app/docs/endpoints/ping/), [API reference](https://opensubsonic.netlify.app/docs/api-reference/)). Errors: `status: failed` + `error { code, message, helpUrl }`; codes 0, 10, 20, 30, 40 (wrong user/password), 41, 42, 43 (conflicting auth), 44 (invalid API key), 50 (not authorized), 60, 70 (not found) ([error](https://opensubsonic.netlify.app/docs/responses/error/)).
- **Auth** ([API reference](https://opensubsonic.netlify.app/docs/api-reference/)): every request carries `u`, `v`, `c`, `f=json` and either `p` ("testing purposes only") or `t` + `s` with `t = md5(password + s)` (lowercase hex, UTF-8, salt >= 6 chars). The [`apiKeyAuthentication`](https://opensubsonic.netlify.app/docs/extensions/apikeyauth/) extension replaces all of `u`/`p`/`t`/`s` with `apiKey`; sending `u` with it is error 43. There is **no header-based auth**; credentials are query (or form) parameters. The [`formPost`](https://opensubsonic.netlify.app/docs/extensions/) extension allows POST with a form body, which keeps credentials out of URLs.
- **Discovery:** [`getOpenSubsonicExtensions`](https://opensubsonic.netlify.app/docs/endpoints/getopensubsonicextensions/) is public (no credentials) and returns `openSubsonicExtensions[] { name, versions[] }`.
- **Library:** `getArtists` (ID3, all at once, no paging), `getArtist`, `getAlbum`, `getSong`, [`getAlbumList2`](https://opensubsonic.netlify.app/docs/endpoints/getalbumlist2/) (`type=alphabeticalByName`, `size` max 500, `offset`), [`search3`](https://opensubsonic.netlify.app/docs/endpoints/search3/) ("Servers must support an empty query and return all the data ... for offline sync", with `songCount`/`songOffset` paging), `getPlaylists`, `getPlaylist`, `getStarred2`, `getGenres`. OpenSubsonic fields the app maps: [Child](https://opensubsonic.netlify.app/docs/responses/child/) `bitDepth`, `samplingRate`, `channelCount`, `musicBrainzId`, `genres`, `artists`, `albumArtists`, `displayArtist`, `replayGain`, `played`, `userRating`, `starred`; [AlbumID3](https://opensubsonic.netlify.app/docs/responses/albumid3/) `recordLabels`, `genres`, `artists`, `releaseDate`, `originalReleaseDate`, `isCompilation`, `userRating`, `starred`; [ArtistID3](https://opensubsonic.netlify.app/docs/responses/artistid3/) `roles`, `sortName`, `coverArt`, `artistImageUrl`, `albumCount`.
- **Covers:** [`getCoverArt`](https://opensubsonic.netlify.app/docs/endpoints/getcoverart/) `id` (a `coverArt` id, not an item id on OpenSubsonic servers) + `size`; binary on success, XML on error; **authenticated** like every other endpoint.
- **Stream:** [`stream`](https://opensubsonic.netlify.app/docs/endpoints/stream/) `id`, `maxBitRate` (kbps, 0 = no limit), `format` (`raw` disables transcoding), `estimateContentLength`, `timeOffset` (music too with the [`transcodeOffset`](https://opensubsonic.netlify.app/docs/extensions/) extension). The [`transcoding`](https://opensubsonic.netlify.app/docs/extensions/transcoding/) extension adds `POST getTranscodeDecision` (body: `directPlayProfiles`, `transcodingProfiles`, `codecProfiles`, `maxAudioBitrate`; answer `canDirectPlay`, `canTranscode`, `transcodeParams`, `sourceStream`, `transcodeStream`) and `getTranscodeStream` ([getTranscodeDecision](https://opensubsonic.netlify.app/docs/endpoints/gettranscodedecision/)).
- **User data:** `star` / `unstar` (`id`, `albumId`, `artistId`), [`setRating`](https://opensubsonic.netlify.app/docs/endpoints/setrating/) `rating` 1-5, 0 clears; [`scrobble`](https://opensubsonic.netlify.app/docs/endpoints/scrobble/) `id`, `time`, `submission` (true = a play, false = now playing); [`playbackReport`](https://opensubsonic.netlify.app/docs/extensions/playbackreport/) extension: `reportPlayback` with `mediaId`, `mediaType`, `positionMs`, `state`, `playbackRate`, `ignoreScrobble`.
- **Playlists:** `createPlaylist` (`name`, `songId`...; with `playlistId` it replaces the playlist's songs), [`updatePlaylist`](https://opensubsonic.netlify.app/docs/endpoints/updateplaylist/) `playlistId`, `name`, `comment`, `public`, `songIdToAdd`*, `songIndexToRemove`*, `deletePlaylist`. No entry ids, no move.
- **Lyrics:** [`getLyricsBySongId`](https://opensubsonic.netlify.app/docs/endpoints/getlyricsbysongid/) (`songLyrics` extension): `lyricsList.structuredLyrics[] { lang, synced, offset, displayArtist, displayTitle, line[] { start (ms), value } }`. Without the extension only `getLyrics?artist&title` (plain text) exists.

### Navidrome

- Latest stable **v0.64.2** (2026-09-24; GitHub releases API, checked 2026-11-11). Docker image `navidrome/navidrome` with `/data` and `/music` volumes, port 4533 ([Docker install](https://www.navidrome.org/docs/installation/docker/)); the first user is created in the web UI at first start.
- Compatible with Subsonic API **1.16.1** "with some exceptions": no video, no browse-by-folder, `search3` only simple auto-complete queries, `getPlaylists` ignores `username`; "IDs in Navidrome are always strings, normally MD5 hashes or UUIDs" ([Subsonic API compatibility](https://www.navidrome.org/docs/developers/subsonic-api/)).
- OpenSubsonic extensions tracked in [navidrome#2695](https://github.com/navidrome/navidrome/issues/2695) (the list reflects `develop`). Merge dates from the pull requests: `songLyrics` (#2656, 2023-12-28), `indexBasedQueue` (#4244, 2025-11-09), `transcoding` (#4990, 2026-03-09), `playbackReport` (#5442, 2026-05-01) are in 0.64.2; **`apiKeyAuthentication` (#6219) merged 2026-09-28, after v0.64.2**, so no stable release has it yet. `formPost`, `transcodeOffset`, `topSongsByArtistId`, `songSimilarity` and the `Child` / `AlbumID3` / `ArtistID3` OpenSubsonic fields are all ticked.
- Transcoding ([configuration options](https://www.navidrome.org/docs/usage/configuration/options/)): `DefaultDownsamplingFormat` = `opus` (used when a client sends `maxBitRate` without `format`), `EnableTranscodingConfig` false by default (the admin enables it to edit profiles), `Transcoding.MaxConcurrent` / `MaxConcurrentPerUser` 0 = unlimited, `TranscodingCacheSize` 100 MB. Per-player settings exist in the web UI (Settings -> Players: a forced format and a Max Bitrate ceiling, [navidrome#5165](https://github.com/navidrome/navidrome/pull/5165), [#5611](https://github.com/navidrome/navidrome/pull/5611)): the server may cap what the app asks for.
- Converted streams: an mp3 transcode has no `Content-Length` and `Accept-Ranges: none` ([navidrome#6170](https://github.com/navidrome/navidrome/issues/6170)); the same R12 behavior as Jellyfin, so a conversion is `canSeek = false`.
- **Server identity:** neither the Subsonic API nor Navidrome exposes a server id (`ping` gives `type` and `serverVersion` only). ADR 0044 decides what `ServerId` is.
- Settings that change behavior: `EnableStarRating` (default true) and `SessionTimeout` 48 h (web UI only; the Subsonic API is stateless, every request authenticates).

## 4. Module plan

```
:provider:subsonic            OpenSubsonic implementation of :core:server (DTOs, mappers, auth interceptor, capabilities)
  SubsonicHttp                GET/POST with the standard parameters; one call site per endpoint family
  SubsonicAuthInterceptor     adds u, t, s, v, c, f (or apiKey) to /rest/* requests of the signed-in host (ADR 0046)
  SubsonicAuthSource          ping (validate + credentials), getOpenSubsonicExtensions (capabilities), no Quick Connect
  SubsonicLibrarySource       getArtists, getAlbumList2, search3 (empty query), getPlaylists, getPlaylist, getCoverArt
  SubsonicPlaybackSource      getSong + stream URL (raw / opus), scrobble (ADR 0045)
  SubsonicUserDataSink        star / unstar, setRating (1-5)
  SubsonicPlaylistEditor      createPlaylist, updatePlaylist, deletePlaylist (move = rewrite)
  SubsonicLyricsSource        getLyricsBySongId (songLyrics) or none
  SubsonicLiveEvents          none (flow never emits; capabilities.liveEvents = false)
  SubsonicModule              @IntoMap ProviderKind.Subsonic -> MediaServer; @IntoSet Interceptor
```

- `:core:model`: `enum class ProviderKind { Jellyfin, Subsonic }`; `ServerInfo.isSupported` becomes a constructor field the provider's mapper sets (Jellyfin: major >= 12; Subsonic: `openSubsonic == true`).
- `:core:server`: `MediaServer(kind, capabilities, auth, library, playback, userData, playlists, lyrics, live)`; `ServerCapabilities(quickConnect, liveEvents, syncedLyrics, ratingSteps, reportsProgress, spatialAudioInfo, seekableConversions, maxIdsPerRequest)`. The sub-interfaces keep their signatures (section 5).
- `:core:datastore`: `StoredAccount.provider: ProviderKind` (key `provider@<id>`, default Jellyfin).
- `:core:data`: `ProviderRegistry` (`Map<ProviderKind, MediaServer>` from Hilt), `ActiveProvider` (the active account's `MediaServer`, resolved once per process like the database, ADR 0036; during onboarding the kind the picker chose). `Active*` delegates bound as `LibrarySource`, `PlaybackSource`, `UserDataSink`, `PlaylistEditor`, `LyricsSource`, `LiveEvents`, so repositories do not change. `SessionRepository.checkServer(address, kind)`, `login(..., kind)`; `ServerCheck.OtherServer` text becomes provider-neutral.
- `:core:navigation`: `OnboardingServerKey(provider: ProviderKind = Jellyfin)`.
- `:app`: depends on `:provider:subsonic` (wiring only; architecture skill section 2 already allows `:app -> everything`).
- `build-logic`: the existing provider convention plugin (`:provider:* -> :core:model, :core:common, :core:server, :core:network`) is reused as it is.
- Test server: `tools/navidrome/compose.yaml` pins `navidrome/navidrome:0.64.2`, mounts `tools/navidrome/music` (a few generated test files: ffmpeg sine waves tagged with made-up artists, albums, genres, labels, embedded lyrics and a ReplayGain tag; CC0, no personal music) and an empty `/data`; `docs/dev/navidrome-test-server.md` describes start, first user (`mq` / a test password written in the doc), per-player transcoding, and how fixtures are recorded (`curl` with the test credentials, then `tools/navidrome/strip.py` removes `t`/`s` and hostnames). Fixtures live in `provider/subsonic/src/test/resources/subsonic/<endpoint>/<case>.json`, like Jellyfin's.

## 5. What `:core:server` needs to become provider-neutral

| Today (Jellyfin-shaped) | On Subsonic | Change |
|---|---|---|
| `AuthSource` Quick Connect methods | do not exist | keep the interface; `ServerCapabilities.quickConnect = false` hides the button (onboarding already hides it when `isQuickConnectEnabled` is false); the Subsonic methods answer `MqError.Client(0, "unsupported")` and are never called. |
| `AuthSession.accessToken` | the password (token auth) or later an API key | field kept, KDoc: "the provider's secret, stored encrypted (ADR 0013)". ADR 0046. |
| `ServerInfo.isSupported` = major >= 12 | `openSubsonic == true` | a field set by the provider. |
| `ServerId` from `/System/Info/Public` | none | generated per account at first login (ADR 0044). |
| `PlaybackStream.playSessionId`, `mediaSourceId` | none | the provider fills `mediaSourceId = itemId` and a fresh UUID as `playSessionId` per stream (cache key `transcode:<playSessionId>` stays unique, media3 skill 9). |
| `PlaybackSource.stream(server, itemId, profile)` asks the server | the client decides | `getSong` then a pure `chooseStream(song, profile)`: direct play (`format=raw`) when container and codec are in the profile's lists and the bitrate is under the cap, else `maxBitRate` + `format=<target codec>`, `canSeek = false`, `segmented = false`. ADR 0045. |
| `reportStart/Progress/Stopped` | `scrobble` | start -> `submission=false`; stopped -> `submission=true` when the Last.fm rule holds (>= 50 % or >= 4 min played); progress -> no request (`reportsProgress = false`); `reportPlayback` is a later option (open question 3). |
| 100-ns ticks | seconds / ms | each provider's mappers convert to `Duration`; the interfaces already use `Duration`. |
| `LiveEvents` WebSocket | nothing | `events()` = `emptyFlow()`; `LiveSync` does not connect when `capabilities.liveEvents` is false; a sync at app start when the last one is older than 15 min replaces it (open question 2). |
| `LyricsSource` always | only with `songLyrics` | `syncedLyrics` capability; without the extension the provider answers `null` (no `getLyrics` by artist/title: it is unsynced and needs tag text that may not match). |
| Rating 0-10 half stars | 1-5 | `ratingSteps = 5`: the provider maps app 2/4/6/8/10 <-> 1-5 (odd values round up); the UI shows whole stars for such an account. `setRating(null)` -> `rating=0`. |
| `setFavorite(itemId)` for any id | `id` / `albumId` / `artistId` | the Subsonic provider prefixes its item ids with the kind (`s/`, `al/`, `ar/`, `pl/`) and strips them in requests; no interface change. Ids stay opaque to the app. |
| `PlaylistEntry.entryId` from the server | none | `entryId = "<index>"` (position), `remove` -> `songIndexToRemove`, `move` -> `createPlaylist?playlistId=` with the full new order; the repository already refreshes the playlist after each edit (R13). |
| `maxIdsPerRequest` = 200 | query length | `updatePlaylist` with many `songIdToAdd` is chunked the same way; `formPost` lifts the URL limit when present. |
| `AudioFormat.spatialFormat` | not exposed | `spatialAudioInfo = false`: no Atmos note, no Atmos rule; `channelCount` > 2 is shown as the channel count only. |
| Images by `Artwork(itemId, tag, type)` | `coverArt` id, no tag | `Artwork.itemId = coverArt id`, `tag = ""`; Navidrome changes the id when the image changes (to be confirmed with the fixtures: R-list of the `subsonic` skill). |

## 6. Auth and secrets (ADR 0046)

1. **Token auth** (`t` + `s`, fresh random salt per request) is the only mechanism every stable Navidrome supports; the app never sends `p`. The password is needed for every token, so it is stored where the Jellyfin token is: `StoredAccount.accessToken`, AES-GCM in the Keystore (ADR 0013). `toString()` of every type already hides it.
2. **API keys** when `apiKeyAuthentication` appears in `getOpenSubsonicExtensions` of a stable server: a second sign-in field "API key" on the Subsonic login screen, stored in the same field, sent as `apiKey` without `u`. Planned as an optional issue (S10) because 0.64.2 lacks it.
3. **Clean URLs:** the app never puts credentials in a URL it stores, logs or hands to Coil / Media3. `SubsonicAuthInterceptor` (registered into the shared OkHttp client through a new `Set<Interceptor>` multibinding in `:core:network`'s `NetworkModule`; no module edge) adds the parameters at request time to `/rest/*` requests whose host is the signed-in Subsonic server, as a **POST form body** when `formPost` is supported, else as query parameters. Image URLs (`<base>/rest/getCoverArt?id=&size=`) and stream URLs therefore stay token-free in Coil's cache keys, Media3 cache keys and logs, which keeps ADR 0015 and the media3 don'ts. Logging keeps method + path + status; query strings are never logged on this provider.
4. **Cleartext** rules unchanged (ADR 0013): the owner's Navidrome will be reached over LAN / Tailscale; HTTPS otherwise.
5. Error 40 -> `MqError.Unauthorized` (sign out as today), 50 -> `Forbidden`, 70 -> `NotFound`, 10/20/30/0 -> `Client(code, message)`, HTTP 5xx -> `Server`. Subsonic errors arrive as HTTP 200 with `status: failed`, so the mapping lives in `SubsonicHttp`, not in the HTTP status handler.

## 7. Streaming, transcoding, downloads (ADR 0045)

| Case | Jellyfin today | Subsonic |
|---|---|---|
| Original (unmetered, decodable) | `PlaybackInfo` -> direct URL | `stream?id=&format=raw` (`DirectPlay`, `canSeek = true`, `sizeBytes = song.size`) |
| Metered / quality cap | HLS Opus in fMP4 (ADR 0019) | `stream?id=&maxBitRate=<kbps>&format=opus` progressive, `Transcode`, `canSeek = false` (navidrome#6170); seeking restarts the stream at `timeOffset` when `transcodeOffset` is present (later polish) |
| Codec the device cannot decode | server transcodes | same `maxBitRate` + `format=opus` request |
| Converted download (ADR 0024) | progressive Opus in Ogg | identical request, one file; `CONVERTED_DOWNLOAD` metadata as today |
| Prefetch, cache keys | `<MediaId>:original`, `transcode:<playSessionId>` | unchanged; the provider's UUID play session keeps keys unique |
| Atmos (E-AC-3 JOC) | Atmos rule, note | no spatial info: plays as an ordinary multichannel file; the device decodes or the server converts |
| Reporting | Playing / Progress / Stopped | `scrobble submission=false` at start, `submission=true` at stop when played enough; no progress reports |
| Server-side ceiling | `MusicStreamingTranscodingBitrate` | Navidrome's per-player Max Bitrate may lower what we asked; the app reads the actual `bitRate` of nothing (the stream has no answer object), so the quality label shows the requested quality (open question 4) |

`DeviceProfileBuilder` is reused as it is: the Subsonic provider reads `directPlayContainers`, `directPlayCodecs`,
`maxStreamingBitrate` and `transcoding.codec` / `bitrate` from the existing `DeviceProfile`; `segmented` is ignored
(no HLS on Subsonic). The OpenSubsonic `transcoding` extension (`getTranscodeDecision`) is **not** used in Servers 1:
the plain `stream` parameters cover the three cases above with one request less.

## 8. Risks

| Risk | Why | Mitigation |
|---|---|---|
| Navidrome's per-player transcoding settings override the app | the admin can force a format or a bitrate per player ([#5165](https://github.com/navidrome/navidrome/pull/5165)) | the `subsonic` skill documents it; the manual test checks the Players page shows "mediatheQ" with no forced format; the Settings page for a Subsonic account links to the explanation |
| Full track sync via `search3` with an empty query | Navidrome supports the OpenSubsonic clarification, other servers may not | capability-free: a contract test on the fixture; a fallback through `getArtists` -> `getArtist` -> `getAlbum` (N+1 requests) only if a target server fails, not in Servers 1 |
| Credentials in query strings when `formPost` is missing | the protocol has no header auth | Navidrome has `formPost`; the query fallback is for other servers; the interceptor is the only place that writes them and tests assert no URL leaves the provider with `t`/`s` |
| Converted streams cannot seek | no `Content-Length`, `Accept-Ranges: none` (navidrome#6170) | same as Jellyfin R12 before HLS: `canSeek = false`, shown in the player; `timeOffset` restart as polish |
| Album artist vs track artist | Subsonic ID3 has `getArtists` (all artists); OpenSubsonic adds `roles` | album artists = `roles` contains `albumartist` (Navidrome ticks `roles`); fallback: artists referenced by an album's `artists` |
| `ServerId` is generated, so the same server added twice is two servers | no server id in the API | duplicates detected by normalized host + user name at sign-in (ADR 0044); "Edit address" keeps the id |
| Rating granularity | 5 steps vs 10 | `ratingSteps` capability; the mapper's round-trip test |
| Ids with a kind prefix | a bug in prefixing breaks favorites | one `SubsonicId` helper with exhaustive tests; fixtures assert the outgoing `id` / `albumId` / `artistId` |
| Owner's machine has no Docker | fixtures need a Navidrome | the doc gives the plain binary alternative (same `--datafolder` / `--musicfolder` flags) |

## 9. Decisions (recommended option first)

1. **Second provider = `:provider:subsonic` speaking OpenSubsonic, Navidrome 0.64+ as the target, own thin client** (recommended; mirrors ADR 0008) / a generic "Subsonic 1.16.1 only" client without extensions / Navidrome's experimental Jellyfin API (`Jellyfin.Enabled`, off by default and experimental). ADR 0042.
2. **Provider kind stored with the account; providers registered in a Hilt map; `:core:data` forwards to the active account's provider through delegates** (recommended: repositories unchanged, one process = one provider, fits ADR 0036) / inject `Map<ProviderKind, MediaServer>` into every repository / one Hilt component per provider. ADR 0043.
3. **`ServerId` for Subsonic = `sub-<uuid>` generated at the first sign-in and kept with the account** (recommended) / a hash of the normalized host (breaks "Edit address" between LAN and Tailscale) / a hash of host + user. ADR 0044.
4. **Streaming with `stream` parameters: `format=raw` or `maxBitRate` + `format=opus`, progressive, no HLS, scrobble reporting** (recommended) / the OpenSubsonic `transcoding` extension (`getTranscodeDecision`, Jellyfin-like, more code, not needed for three cases) / `maxBitRate` only, letting `DefaultDownsamplingFormat` pick the codec. ADR 0045.
5. **Password stored encrypted in the account's secret field; credentials added by an interceptor, URLs stay clean; API keys later** (recommended) / `apiKey` only (no stable Navidrome has it) / credentials in URLs with Coil cache keys stripped. ADR 0046.
6. **No live events on Subsonic; a sync at app start when the last one is older than 15 minutes** (recommended, small, both providers benefit) / a periodic poll of `getScanStatus` / nothing beyond the daily sync.
7. **Kind-prefixed item ids inside the provider** (recommended, no interface change) / a `kind` parameter on `UserDataSink` and `PlaylistEditor` (touches `:core:data` callers and fakes).
8. **Playlist move = rewrite with `createPlaylist?playlistId=`** (recommended, the only API) / hide reordering for Subsonic accounts.
9. **Test server pinned to `navidrome/navidrome:0.64.2` with generated CC0 audio** (recommended) / `latest` (fixtures drift) / the owner's real library (personal data in fixtures).

## 10. Open questions for the owner

Owner answers (2026-11-11): every recommended decision in section 9 is accepted (ADRs 0042-0046 Accepted).

1. Answered: the owner has (or will have) a Navidrome of their own. Fixtures still come from the pinned test server (no personal data); the manual tests also run against the owner's server.
2. Answered: yes, sync at app start when stale for both providers.
3. Progress reporting through `reportPlayback` (`playbackReport` extension, in 0.64.2): wanted in Servers 1 for Navidrome's Now Playing view? Plan: no, scrobble only; `reportPlayback` is a one-issue follow-up.
4. Quality label when Navidrome caps the bitrate server-side: show the requested quality (plan) or fetch `getNowPlaying` to read the real one? Plan: requested quality; the skill documents the Players page.
5. Should the Subsonic login screen offer the plain "password in the request" fallback (`p`) for servers that refuse token auth (error 41, LDAP users)? Plan: no; show error 41's `helpUrl` text and stop.
6. API keys (S10, optional) in Servers 1 or when a stable Navidrome ships them? Plan: draft the issue, start it only after a release with `apiKeyAuthentication`.

## 11. Out of scope (Servers 1)

Plex (Servers 2), Emby and other Subsonic servers as tested targets (Gonic, Airsonic-Advanced, LMS may work but
are not verified), merged libraries or a server switcher that keeps two libraries on screen, Navidrome's Jellyfin
API, shares, podcasts, internet radio, jukebox, bookmarks, play queue sync (`savePlayQueue`), smart playlists,
the OpenSubsonic `transcoding` extension, Last.fm / ListenBrainz scrobbling settings, Subsonic on TV (the TV app
gets the provider for free through `:core:data` but is not tested here), performance measurements.

## 12. Skills and ADRs that would need to change (not edited here)

- New `subsonic` skill (`.claude/skills/subsonic/SKILL.md`, written in S1): source of truth (OpenSubsonic spec, Navidrome docs and version), client design, auth, library endpoints and paging, images, stream selection, reporting, user data, playlists, lyrics, errors, an `R<n>` regression list, done checklist, in the sections of the `jellyfin` skill.
- `architecture` skill: module map adds `:provider:subsonic`; section 4 "Provider interface" gains `MediaServer`, `ServerCapabilities`, `ProviderKind` and the delegate rule; section 10 notes that Subsonic credentials ride in an interceptor; the done checklist asks "capability-gated?".
- `jellyfin` skill: section 0 says the jellyfin skill applies to `:provider:jellyfin` only; `JellyfinCapabilities` listed.
- `testing` skill section 4: contract tests for Subsonic (`src/test/resources/subsonic/`), the pinned Navidrome test server and the fixture-stripping script.
- `media3` skill: section 3 notes progressive conversions on Subsonic (`canSeek = false`, no HLS), section 12 the scrobble rule.
- ADR 0008: unchanged (Jellyfin). ADR 0013: amended by 0046 (the secret may be a password). ADR 0015: amendment that the Subsonic interceptor authenticates covers at request time, URLs still carry no secret. ADR 0019 / 0024: a note that Subsonic conversions are progressive only.
