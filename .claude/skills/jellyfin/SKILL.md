---
name: jellyfin
description: Rules and known behaviors of the Jellyfin server API for mediatheQ's :provider:jellyfin module - auth, endpoints, paging, playback/stream URLs, device profile, Atmos, reporting, user data, playlists, lyrics, WebSocket, errors and server quirks. Use for any code that talks to Jellyfin or maps its data.
---

# mediatheQ - Jellyfin provider

Target: **Jellyfin server 12.x** (the owner's server runs 12.3.0 as of 2026-10-25; OpenAPI copies for 12.2.0 and 12.3.0 in `provider/jellyfin/api/`). No support for 10.x.
All Jellyfin code lives in `:provider:jellyfin` and implements the interfaces in `:core:server` (see `architecture`).

## 0. Source of truth

1. The OpenAPI document of the **user's own server** (same version we target). Fetch it once per server upgrade and keep a copy in `provider/jellyfin/api/openapi-<version>.json` for reference and diffing. Jellyfin 12 regenerated its OpenAPI (Swashbuckle v10), so do not trust older docs or SDK snippets.
2. https://api.jellyfin.org for browsing.
3. Official SDKs (`jellyfin-sdk-kotlin`, `@jellyfin/sdk`) as **reference for request shapes only** - we do not depend on them and do not copy code.
4. The regression list in section 12 (behaviors learned the hard way).

Never invent an endpoint or parameter. If it is not in the OpenAPI document of the target version, it does not exist.
Exception: URLs the server returns (a `TranscodingUrl`, the URIs inside an HLS playlist) are used as given, never built by us, even when their routes are missing from the OpenAPI (R15).

## 1. Client design

- Own thin client: OkHttp + kotlinx.serialization. One `JellyfinApi` class per area (`AuthApi`, `ItemsApi`, `PlaybackApi`, `UserDataApi`, `PlaylistsApi`, `LyricsApi`, `SessionsApi`) with only the endpoints we use.
- DTOs: `@Serializable` classes named `*Dto` mirroring JSON (PascalCase names via `@SerialName`), all fields nullable/defaulted unless the spec guarantees them. `Json { ignoreUnknownKeys = true; explicitNulls = false }`.
- Mappers `*Dto.toDomain()` in `mapper/`, pure functions, 100% unit tested.
- DTOs never leave the module.

## 2. Authentication

Jellyfin 12 disables legacy authorization by default (also on upgraded servers). Only these work:

- **Header (default for everything):**
  ```
  Authorization: MediaBrowser Client="mediatheQ", Device="<Build.MODEL>", DeviceId="<stable id>", Version="<versionName>", Token="<access token>"
  ```
  - Before login: same header without `Token`.
  - Quote and escape values. `DeviceId` = random UUID created once per install, stored in DataStore; never the hardware ID.
- **`ApiKey` query parameter:** only where a header is impossible (WebSocket handshake; URLs handed to components that cannot set headers). Media3 can set headers - prefer headers for streams.
- **Never** both header and `ApiKey` on the same request.
- **Never** `api_key`, `X-Emby-Token`, `X-MediaBrowser-Token`, `X-Emby-Authorization` (removed/disabled). Never `/emby/` or `/mediabrowser/` path prefixes (removed in 12).
- Implemented in one `JellyfinAuthInterceptor`; tests assert the exact header.

### Login flows (`:feature:onboarding`)

- Validate server: `GET /System/Info/Public` (no auth) -> check `Version` major >= 12, show server name.
- Username + password: `POST /Users/AuthenticateByName` -> store `AccessToken` (encrypted) + `User.Id`.
- Quick Connect (preferred on TV later): initiate (POST in 12 - the GET route was removed), poll `GET /QuickConnect/Connect`, then `POST /Users/AuthenticateWithQuickConnect`. Verify exact routes in the OpenAPI.
- Logout: `POST /Sessions/Logout`, then delete token locally.
- 401 anywhere -> token invalid -> repository emits `MqError.Unauthorized` -> app routes to re-login. No silent retry loops.

## 3. Reading the library

- Main query: `GET /Items` with `userId`, `includeItemTypes` (`MusicAlbum`, `Audio`, `MusicArtist`, `Playlist`), `recursive=true`, `fields=...` (only what we map), `enableImageTypes`, `startIndex` + `limit` paging, explicit `sortBy`/`sortOrder` for stable paging.
- Jellyfin 12 changes to remember:
  - `GetItems` applies `recursive` when filters are requested together with `includeItemTypes`; the same query can return a different set than 10.11. Always send `includeItemTypes` and `recursive` explicitly.
  - Unknown values in `includeItemTypes`, `filters`, `sortBy` or `fields` are **silently ignored** on 12.2.0 (HTTP 200), so a typo returns a different set (e.g. the whole library) with no error (R8). Every query shape has a contract test asserting its exact parameters.
  - By-name items (artists, genres) responses are restricted and people are deduplicated.
- Artists: album artists vs track artists are different lists - use the artists endpoints the OpenAPI lists for each and map both; the Library "Artists" tab shows **album artists** by default.
- Page size: 200-500 items; full sync of < 10k tracks must finish in under ~1 min on LAN. Measure, then tune.
- Incremental sync: WebSocket `LibraryChanged` (section 9) starts a full sync (owner decision, #18), plus the daily full sync. A targeted re-sync by item ids can replace it if full syncs get too slow.

## 4. Images

- URL: `/Items/{id}/Images/{type}` with `tag` (cache key from the item's `ImageTags`), and size params (`maxWidth`/`fillWidth` + `quality`). Always request the display size (grid thumbnail vs Now Playing), never originals in lists.
- The `tag` makes URLs immutable -> Coil can cache aggressively; a changed tag = new URL.
- BlurHash: request `ImageBlurHashes` in `fields` and store it in Room for placeholders.
- Images go through the app OkHttp client (so the local-network cleartext rule applies), **without** the auth header: `/Items/{id}/Images/{type}` is public on 12.x (no security in the OpenAPI, checked 2026-10-19), and keeping the token out of image requests keeps it out of image caches (ADR 0015). Request widths are rounded up to 128/256/512/1024 so different layouts share cached images.

## 5. Playback: stream selection

- Ask the server per item: `POST /Items/{id}/PlaybackInfo` with our **DeviceProfile** and `userId`, `maxStreamingBitrate`, `startTimeTicks`. The response's `MediaSources` say whether we can direct play / direct stream / must transcode, and give the URL/params.
- DeviceProfile is built in code from **real device capabilities** (MediaCodecList + AudioCapabilities for passthrough) plus user settings:
  - Wi-Fi: original quality -> declare all decodable codecs/containers as direct play.
  - Mobile data: max 256 kbps -> transcoding profile `opus` (container per Media3 support), so lossless/high-bitrate sources transcode.
  - Rare formats the device cannot decode -> server transcodes.
- **Atmos rule:** a track whose audio stream has `AudioSpatialFormat == DolbyAtmos` (enum values `None` / `DolbyAtmos` / `DTSX`) must **never** be transcoded when "Prefer Dolby Atmos" is on and the output supports passthrough. Declare `eac3` (and `truehd` where supported) as direct play for that path. If direct play is impossible, play the fallback and set a visible "Atmos unavailable" state - never silently downgrade. On the phone (#24, no passthrough yet) `eac3` is declared only with a JOC decoder and `truehd` never, so the server converts Atmos (media3 skill, section 4).
- Ticks: Jellyfin uses 100-ns ticks. Convert only in mappers (`ticks / 10_000 = ms`).
- Stream URLs carry `PlaySessionId` and `MediaSourceId` from `PlaybackInfo`; keep them for reporting.

## 6. Playback reporting

- `POST /Sessions/Playing` on start, `POST /Sessions/Playing/Progress` periodically (every ~10 s and on pause/seek), `POST /Sessions/Playing/Stopped` on stop/skip, each with `ItemId`, `MediaSourceId`, `PlaySessionId`, `PositionTicks`, `IsPaused`, `PlayMethod`.
- Done in `:core:playback` service via `PlaybackSource`/`UserDataSink`, never from UI.
- Offline plays: queue reports locally and flush when online (later issue).

## 7. User data

- Favorite and rating: `POST /UserItems/{itemId}/UserData?userId=...` with only the changed field (`{"IsFavorite": true}` or `{"Rating": 8.0}`); fields left out keep their value. `Rating` is 0-10; the app shows it in half stars (1-10, one half star = 1; whole-star mode sends even values), and 0 means unrated (R14). `/UserItems/{itemId}/Rating` is the like/dislike flag (`Likes`), not the rating: never used. Clearing sends `Rating: 0` (R14).
- `/UserFavoriteItems/{itemId}` and `/UserPlayedItems/{itemId}` exist too; played state is not written yet.
- Optimistic update in Room, then server call; revert on failure (`DefaultUserDataRepository`). A change waiting for the server stays on top of socket echoes and sync swaps.
- Offline (#56): a change that fails with `MqError.Network` waits in `pending_user_data` (one merged row per item, last change wins) and is sent oldest first with the next change or when the WebSocket connects. Any HTTP answer settles it: 2xx deletes the row, 4xx/5xx undoes only the refused field and shows the message, 401 signs out.
- See regression R2 (rating overwritten during playback).

## 8. Playlists

- Create: `POST /Playlists` with JSON `{Name, UserId, MediaType: "Audio"}` and no `Ids`; tracks follow through Add, so there is one chunked path. Add: `POST /Playlists/{id}/Items?ids=...&userId=...`. Remove: `DELETE /Playlists/{id}/Items?entryIds=...` (`PlaylistItemId`s, never guessed from item IDs). Move: `POST /Playlists/{id}/Items/{entryId}/Move/{newIndex}` (index over all server entries). Rename: `POST /Playlists/{id}` with `{Name}`. Delete: `DELETE /Items/{id}`, the R5 exception.
- Chunk any request carrying ID lists (R3). Entries without `PlaylistItemId` are dropped by the mapper.
- Playlist cover collage appears with delay (R6). On 12.2.0 entry IDs equal item IDs (R13).

## 9. Live updates (WebSocket)

- Connect to `/socket` with `ApiKey` and `deviceId` query params (header not possible here).
- Connect with the plain OkHttp client (no `JellyfinAuthInterceptor`: never header and `ApiKey` together, R1). Never log the socket URL.
- Handle: `ForceKeepAlive` (`Data` = seconds; send `{"MessageType":"KeepAlive"}` at once, then every half of it), `LibraryChanged` (one full sync after 5 s of quiet, at most 30 s after the first change; a change during a running sync syncs again after it), `UserDataChanged` (only for our `UserId`; write favorite/played/rating in Room). Ids may come with dashes: compare without them. Ignore unknown message types and unreadable frames.
- Reconnect after 1, 2, 4 ... 60 s with +/- 20 % jitter; the wait resets only after a connection lasted 30 s. Stop on 401 and route to re-login.
- One connection per server, owned by `LiveSync` (`@Singleton`, app scope); open while signed in and the app is in the foreground or playing.

## 10. Lyrics

- `GET /Audio/{id}/Lyrics` returns lyric lines with optional timestamps.
- See R4: `IsSynced` may be missing for `.lrc` -> decide "synced" from timestamps present on lines.
- LRCLIB fallback is optional and off by default (separate setting).

## 11. Errors and retries

| HTTP | Meaning for us | Action |
|---|---|---|
| 400 | our request is invalid (malformed body or ids; bad enum values are ignored instead, R8) | `MqError.Client`, log request shape (no token), fix code |
| 401 | token invalid/expired or legacy auth used | `MqError.Unauthorized` -> re-login |
| 403 | parental/permission restriction | `MqError.Forbidden`, show message |
| 404 | item gone | remove locally, `MqError.NotFound` |
| 414 | too many IDs in URL | chunk (R3) - should never reach UI |
| 5xx / timeout | server or network | retry idempotent GETs 3x with backoff; writes: no blind retry |

Logging: method + path + status + duration. Never headers, never query strings containing `ApiKey`.

## 12. Regression list (each has a named test - see `testing` skill)

- **R1 Auth:** header `Authorization: MediaBrowser ...`; `ApiKey` only for WebSocket/headerless URLs; never `api_key` or legacy headers (Jellyfin 12 returns 401).
- **R2 Rating vs progress:** a rating/favorite set while the track is playing can be overwritten by Jellyfin 12 playback progress reporting. Re-apply user data after the next progress/stop report (or send it after stop), and verify via server state in the test.
- **R3 Long ID lists:** ~250+ IDs in a query string -> 414. Chunk to <= 200 IDs per request (`ServerCapabilities.maxIdsPerRequest`).
- **R4 Lyrics sync flag:** `.lrc` lyrics may arrive without `IsSynced`; infer from line timestamps.
- **R5 Delete:** `DELETE /Items` deletes the file **for all users from the server**. The app never calls it, with one exception: `PlaylistEditor.deletePlaylist` sends `DELETE /Items/{id}` for a single id that the repository has checked is a playlist (owner decision, #16; a non-admin user may delete their own playlist on 12.2.0). Never with `ids`, never for any other item type. "Remove" in the UI means remove from playlist / unfavorite / remove download.
- **R6 Playlist collage:** a new or empty playlist has no image; the generated collage appears ~1 s after tracks are added (12.2.0, checked 2026-10-22). Refresh the playlist again after a short delay instead of showing a broken image.
- **R7 GetItems semantics (12):** always send `includeItemTypes` + `recursive` explicitly; results differ from 10.11 otherwise.
- **R8 Invalid query values are ignored (12.2.0):** `includeItemTypes=Bogus`, `filters=Nope`, `sortBy=Nope`, `fields=Nope` all return 200 as if the value were absent (checked 2026-10-19; earlier notes said 400). Contract tests assert the exact query string of every request shape, because the server will not tell us about a typo.
- **R9 One session per DeviceId:** a new login (password or Quick Connect) with the same `DeviceId` revokes the previous access token (old token -> 401, found on 12.2.0). Keep exactly one stored token per server and replace it on every login; never keep using an older token. Android backup is off so a restored install cannot share a DeviceId with the original.

- **R10 Transcoding URL carries the token:** `PlaybackInfo`'s `TranscodingUrl` includes `ApiKey=<token>` (12.2.0). Strip `ApiKey`/`api_key` in any case and send the header (R1); never keep a URL with the token.
- **R11 Music transcodes default to 128 kbps:** without `DeviceProfile.MusicStreamingTranscodingBitrate` Jellyfin converts music at 128 kbps whatever `MaxStreamingBitrate` says (12.2.0). Always send it (256000 for Opus on metered networks).
- **R12 Live transcodes cannot be seeked:** progressive transcodes answer `Accept-Ranges: none` with no length (12.2.0); direct-play files return 206. Mark such streams `canSeek = false`. Since #38 the app streams conversions as HLS (R15); progressive transcodes remain for converted downloads (Opus in Ogg, #68, ADR 0024), which are never continued mid-file.
- **R13 Entry IDs are item IDs:** on 12.2.0 `PlaylistItemId` equals the track's item ID (checked 2026-10-22). Remove by entry ID removes **every** copy of that track in the playlist; Move by entry ID moves the **first** copy; Move past the last index answers 500. Mirror that locally and refresh the playlist after each edit.

- **R14 Clearing a rating:** the server applies `Rating` in `UpdateUserItemDataDto` only when it has a value, and `/UserItems/{itemId}/Rating` changes `Likes`, not `Rating`. So a cleared rating is sent as `Rating: 0`, and the mapper reads 0 as unrated (socket messages too). To be confirmed on 12.2.0.
- **R15 HLS audio is undocumented but works:** OpenAPI 12.3.0 lists no audio HLS routes, yet a `TranscodingProfile` with `Protocol: hls`, `Container: mp4` makes `PlaybackInfo` answer `TranscodingSubProtocol: hls` and `TranscodingUrl` `/audio/{id}/master.m3u8?...&SegmentContainer=mp4&PlaySessionId=...&ApiKey=...` (checked 2026-10-25 on 12.3.0). With `ApiKey` stripped and the `Authorization` header sent: the master playlist points to a relative `main.m3u8` (VOD, every 3 s segment listed, `EXT-X-MAP` init `hls1/main/-1.mp4`), no URL carries the token, segments answer 401 without the header, and any segment can be asked for directly (the server converts from there, ~0.6 s), so the stream seeks. TS segments exist too; the app uses fMP4 (Opus in fMP4). Fixtures: `items_playbackinfo/transcode_hls.json`, `core/playback` test resources `hls/`.
- **R16 Same-name artists in two libraries:** an artist with folders in two libraries (`/music` and `/atmosMusic`) is two `MusicArtist` items; `/Artists/AlbumArtists` lists one, while an album's `AlbumArtists` and `ArtistItems` may carry the other id. The server merges them by name (`albumArtistIds=` with either id returns the same albums; 12.3.0, checked 2026-10-27). The sync does the same: `SyncDao.mergeSameNameArtists` moves credits of a credit-only artist to its one album artist namesake (case-insensitive), drops doubled credits and marks still-linked credit-only artists as album artists (#112). Fixtures: `items_music_album/album_artist_other_id.json`, `artists_album_artists/same_name.json`.

New quirk found -> add `R<n>` here + fixture + test in the same PR.

## 13. Done checklist

- [ ] Endpoint and params checked against the 12.x OpenAPI copy.
- [ ] DTO + mapper + mapper tests; contract test with a recorded fixture (no personal data).
- [ ] Auth only via `JellyfinAuthInterceptor`; no token in logs.
- [ ] Errors mapped to `MqError` per section 11.
- [ ] Any ID list chunked.
- [ ] Regression list updated if new behavior was discovered.
