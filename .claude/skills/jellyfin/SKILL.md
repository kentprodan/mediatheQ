---
name: jellyfin
description: Rules and known behaviors of the Jellyfin server API for mediatheQ's :provider:jellyfin module - auth, endpoints, paging, playback/stream URLs, device profile, Atmos, reporting, user data, playlists, lyrics, WebSocket, errors and server quirks. Use for any code that talks to Jellyfin or maps its data.
---

# mediatheQ - Jellyfin provider

Target: **Jellyfin server 12.x** (12.1 is current stable as of 2026-10). No support for 10.x.
All Jellyfin code lives in `:provider:jellyfin` and implements the interfaces in `:core:server` (see `architecture`).

## 0. Source of truth

1. The OpenAPI document of the **user's own server** (same version we target). Fetch it once per server upgrade and keep a copy in `provider/jellyfin/api/openapi-<version>.json` for reference and diffing. Jellyfin 12 regenerated its OpenAPI (Swashbuckle v10), so do not trust older docs or SDK snippets.
2. https://api.jellyfin.org for browsing.
3. Official SDKs (`jellyfin-sdk-kotlin`, `@jellyfin/sdk`) as **reference for request shapes only** - we do not depend on them and do not copy code.
4. The regression list in section 12 (behaviors learned the hard way).

Never invent an endpoint or parameter. If it is not in the OpenAPI document of the target version, it does not exist.

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
  - Invalid filter values now return **HTTP 400** -> treat as our bug (log request, fail tests), not as a server error.
  - By-name items (artists, genres) responses are restricted and people are deduplicated.
- Artists: album artists vs track artists are different lists - use the artists endpoints the OpenAPI lists for each and map both; the Library "Artists" tab shows **album artists** by default.
- Page size: 200-500 items; full sync of < 10k tracks must finish in under ~1 min on LAN. Measure, then tune.
- Incremental sync: re-query with a "changed since" filter if the target version supports one (check OpenAPI), otherwise driven by WebSocket `LibraryChanged` (section 9) + periodic full reconcile (e.g. daily, on Wi-Fi).

## 4. Images

- URL: `/Items/{id}/Images/{type}` with `tag` (cache key from the item's `ImageTags`), and size params (`maxWidth`/`fillWidth` + `quality`). Always request the display size (grid thumbnail vs Now Playing), never originals in lists.
- The `tag` makes URLs immutable -> Coil can cache aggressively; a changed tag = new URL.
- BlurHash: request `ImageBlurHashes` in `fields` and store it in Room for placeholders.
- Images go through the same OkHttp client (auth header), not anonymous.

## 5. Playback: stream selection

- Ask the server per item: `POST /Items/{id}/PlaybackInfo` with our **DeviceProfile** and `userId`, `maxStreamingBitrate`, `startTimeTicks`. The response's `MediaSources` say whether we can direct play / direct stream / must transcode, and give the URL/params.
- DeviceProfile is built in code from **real device capabilities** (MediaCodecList + AudioCapabilities for passthrough) plus user settings:
  - Wi-Fi: original quality -> declare all decodable codecs/containers as direct play.
  - Mobile data: max 256 kbps -> transcoding profile `opus` (container per Media3 support), so lossless/high-bitrate sources transcode.
  - Rare formats the device cannot decode -> server transcodes.
- **Atmos rule:** a track whose audio stream has `AudioSpatialFormat == DolbyAtmos` (enum values `None` / `DolbyAtmos` / `DTSX`) must **never** be transcoded when "Prefer Dolby Atmos" is on and the output supports passthrough. Declare `eac3` (and `truehd` where supported) as direct play for that path. If direct play is impossible, play the fallback and set a visible "Atmos unavailable" state - never silently downgrade.
- Ticks: Jellyfin uses 100-ns ticks. Convert only in mappers (`ticks / 10_000 = ms`).
- Stream URLs carry `PlaySessionId` and `MediaSourceId` from `PlaybackInfo`; keep them for reporting.

## 6. Playback reporting

- `POST /Sessions/Playing` on start, `POST /Sessions/Playing/Progress` periodically (every ~10 s and on pause/seek), `POST /Sessions/Playing/Stopped` on stop/skip, each with `ItemId`, `MediaSourceId`, `PlaySessionId`, `PositionTicks`, `IsPaused`, `PlayMethod`.
- Done in `:core:playback` service via `PlaybackSource`/`UserDataSink`, never from UI.
- Offline plays: queue reports locally and flush when online (later issue).

## 7. User data

- Favorites, played state, rating: use the per-item user-data endpoints from the 12.x OpenAPI (`/UserFavoriteItems/{itemId}`, `/UserPlayedItems/{itemId}`, rating endpoint). Verify routes; older `/Users/{userId}/...` variants may be removed.
- Optimistic update in Room, then server call; revert on failure.
- See regression R2 (rating overwritten during playback).

## 8. Playlists

- Create: `POST /Playlists`. Add: `POST /Playlists/{id}/Items?ids=...`. Remove: `DELETE /Playlists/{id}/Items?entryIds=...` (playlist **entry** IDs, not item IDs). Move: `POST /Playlists/{id}/Items/{itemId}/Move/{newIndex}`.
- Chunk any request carrying ID lists (R3).
- Playlist cover collage appears with delay (R6).

## 9. Live updates (WebSocket)

- Connect to `/socket` with `ApiKey` and `deviceId` query params (header not possible here).
- Handle: `ForceKeepAlive` / `KeepAlive` (reply on the interval the server sends), `LibraryChanged` (targeted re-sync of added/updated/removed IDs), `UserDataChanged` (update favorites/played/rating in Room). Ignore unknown message types.
- Reconnect with exponential backoff + jitter; stop on 401 and route to re-login.
- One connection per server, owned by an `@ApplicationScope` component; paused when app is in background and not playing.

## 10. Lyrics

- `GET /Audio/{id}/Lyrics` returns lyric lines with optional timestamps.
- See R4: `IsSynced` may be missing for `.lrc` -> decide "synced" from timestamps present on lines.
- LRCLIB fallback is optional and off by default (separate setting).

## 11. Errors and retries

| HTTP | Meaning for us | Action |
|---|---|---|
| 400 | our request is invalid (12 rejects bad filters) | `MqError.Client`, log request shape (no token), fix code |
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
- **R5 Delete:** `DELETE /Items` deletes the file **for all users from the server**. The app never calls it. "Remove" in the UI means remove from playlist / unfavorite / remove download.
- **R6 Playlist collage:** the generated playlist image appears ~1 s after creation; refresh images after a short delay instead of showing a broken image.
- **R7 GetItems semantics (12):** always send `includeItemTypes` + `recursive` explicitly; results differ from 10.11 otherwise.
- **R8 Invalid filters (12):** 400 instead of being ignored -> contract tests cover every filter we send.
- **R9 One session per DeviceId:** a new login (password or Quick Connect) with the same `DeviceId` revokes the previous access token (old token -> 401, found on 12.2.0). Keep exactly one stored token per server and replace it on every login; never keep using an older token. Android backup is off so a restored install cannot share a DeviceId with the original.

New quirk found -> add `R<n>` here + fixture + test in the same PR.

## 13. Done checklist

- [ ] Endpoint and params checked against the 12.x OpenAPI copy.
- [ ] DTO + mapper + mapper tests; contract test with a recorded fixture (no personal data).
- [ ] Auth only via `JellyfinAuthInterceptor`; no token in logs.
- [ ] Errors mapped to `MqError` per section 11.
- [ ] Any ID list chunked.
- [ ] Regression list updated if new behavior was discovered.
