# 0019. HLS for converted streams, chosen by a lazy media source

- Status: Accepted
- Date: 2026-10-25
- Supersedes: 0016

## Context

ADR 0016 converted tracks to Opus in Ogg as one live progressive stream. Jellyfin answers those with `Accept-Ranges: none` and no length, so a converted track could not be seeked (R12). Since #24 and #25 conversions are common: mobile data (256 kbps), a lower Wi-Fi quality (256 or 128 kbps), Dolby Atmos on a phone without a JOC decoder.

On the owner's Jellyfin 12.3.0 (checked 2026-10-25, R15): with a transcoding profile `Protocol: hls`, `Container: mp4`, `PlaybackInfo` returns `TranscodingSubProtocol: hls` and a `master.m3u8` URL. The routes are missing from the OpenAPI, but the URL comes from the server. With the token stripped and the `Authorization` header sent, the playlists list every 3 s segment (VOD), no URL carries the token, and any segment can be asked for directly: the server converts from there in about 0.6 s. TS segments exist too; Opus is carried in fMP4.

The player builds each queue item's source when the item enters the playlist, but whether a track is converted is known only when its stream is resolved, at load time (one stream per track and quality, ADR 0018). A progressive source cannot play a playlist.

## Decision

- **Every conversion is HLS:** `DeviceProfileBuilder.opus(bitrate)` asks for Opus in fMP4 HLS segments (`TranscodingTarget.segmented`). Quality rules stay those of #25 (original, 256, 128 kbps, stereo).
- **A lazy media source chooses the kind of source:** `StreamMediaSourceFactory` (a `ForwardingMediaSourceFactory`) gives `mediatheq://` items a `LazyStreamMediaSource` (a `CompositeMediaSource`). When the player prepares the item (lazily, as for any source) it resolves the stream off the playback thread, then prepares one child: an `HlsMediaSource` on the playlist URL for a segmented stream, otherwise the existing progressive source on the `mediatheq://` URI. ExoPlayer's own masking keeps periods waiting until the child's timeline arrives. The timeline shows the queue item, never the resolved URL.
- **HLS bypasses both caches:** playlists and segments go straight to the server through the app OkHttp client with the auth header (cleartext rule, ADR 0013), with the same load error policy. Converted streams are never cached (ADR 0018).
- **Everything else is unchanged:** downloads, cached originals, offline and progressive transcodes go through `streamDataSourceFactory` as before. Network and server errors while choosing are retried by the choice itself (1, 2, 4 s while online), because the file source's own retry would get a playlist; only a lasting failure falls back to the file source, which then fails as before. That data source reuses the stream the item started with, so a replay after a network change never hands it a playlist.
- **Auth on every HLS request:** the header is read at each request (a `ResolvingDataSource` over OkHttp), so a new login (R9) reaches items prepared earlier.

## Consequences

- Converted tracks seek on any network; the seek bar follows the timeline. Reports keep the HLS play session.
- `LazyStreamMediaSource` subclasses Media3 unstable API (`CompositeMediaSource`, `ForwardingTimeline`): check it at every Media3 bump (tests in `HlsStreamTest` cover the lifecycle against recorded playlists).
- A replay keeps the stream chosen when the item was prepared, even if the network or the quality changed since; a new queue or a re-prepare chooses again.
- The HLS routes are undocumented (R15): a server update could remove them. The app then gets a progressive transcode (`TranscodingSubProtocol: http`) and plays it unseekable, as before.
- Gapless across converted tracks is still not guaranteed; a pause long enough for the server to end the transcode job is a manual test.
