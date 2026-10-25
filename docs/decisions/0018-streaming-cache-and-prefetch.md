# 0018. Streaming cache keys and prefetch with CacheWriter

- Status: Accepted
- Date: 2026-10-25

## Context

Issue #21 asks for a 2 GB streaming cache with stable keys, replays without network, and a prefetch of the next tracks, choosing between Media3's `DefaultPreloadManager` and a `CacheWriter` job (media3 skill, section 9). The player's tracks are `mediatheq://track/<id>` URIs resolved at load time into server URLs that change per play session; Jellyfin's live transcodes have no length and are not byte-identical between conversions (ADR 0016).

## Decision

- **Cache:** one `SimpleCache` in `cacheDir/media` with `LeastRecentlyUsedCacheEvictor(2 GB)`, on the Media3 `DatabaseProvider` that `:core:download` provides (one index for both caches, wired through the Hilt graph, no module edge). Size fixed until Settings (#25).
- **Keys:** a direct play is cached under `<MediaId>:original`, independent of URL and session; any other stream (transcode, remux) under `transcode:<playSessionId>`, so its bytes never mix with another stream. Transcodes are never actually written: the player loads with `FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN` and they have no length. No URL or token is ever part of a key.
- **Replay:** before resolving a stream, a complete `<id>:original` copy plays with no server request at all (also offline), reported as a direct play with a session of our own. A partial copy whose content length differs from the server's file size (`MediaSource.Size`) is dropped before reading on.
- **Prefetch: `CacheWriter`**, not `DefaultPreloadManager`. A preload manager prepares media sources in memory and would still need this data source and cache; the player already prepares the next playlist item itself for gapless. The `Prefetcher` caches the next 2 tracks in play order (1 on metered networks), original files only, skipping downloaded or cached tracks before any server request, and only while playing and online. It never replaces the stream the player knows for a track, and stops when the player starts loading the same track.

## Consequences

- Replays of streamed tracks cost no data and work offline until evicted; transcoded (metered) plays are not cached.
- A complete cached copy of a file changed on the server keeps playing until it is evicted (accepted shortcut).
- The "Save data" rule (no prefetch on metered networks) and the cache size become settings in #25.
