# 0027. Waveforms are computed on the device from whole local files

- Status: Accepted
- Date: 2026-11-03

## Context

Issue #124 wants a waveform seek bar in Now Playing. Jellyfin has no waveform data, so the app must compute it from the audio file, without slowing playback or the UI and without downloading anything just for this.

## Decision

- **Source:** only a file that is whole on the device: a completed download (the original, or the converted Opus copy of ADR 0024, which is what the user hears) or a complete original in the streaming cache (`<MediaId>:original`, ADR 0018). Converted streams (HLS, ADR 0019) are never analyzed: no file of them is on the device.
- **Decoder:** Media3's extractors (`MediaExtractorCompat` from the new artifact `androidx.media3:media3-inspector`, same version as the rest of Media3, only in `:core:playback`) read the file from its cache only (`CacheDataSource` with no upstream and no writes, so a missing span fails at once), and the platform `MediaCodec` decodes it to float PCM (16-bit when the decoder ignores the request). No player is involved, and the player's PCM is never touched, so offload stays as `AudioPolicy` decides.
- **Measure:** RMS per chunk of 2048 frames, folded at the end into **200 levels**, normalized to the loudest one (owner's choice: quiet and loud parts stay visible). Stored as one byte each.
- **Storage:** Room table `waveform` (server, item, file size, levels), schema 6. The file size is the download's stored bytes or the cached original's content length; another size means another file, analyzed again. `NULL` levels = no decoder on this device for the format (E-AC-3 JOC, TrueHD on a phone), never retried for that size. Any other failure stores nothing. Sign-out clears the table with the library.
- **When:** the current track and the next two in play order on player events (transition, timeline, loading stopped), a track the prefetcher has just cached whole, and downloads that complete while the playback service runs. No backlog of older downloads (owner's choice: only what is listened to). A track missed by process death is analyzed the next time it is queued.
- **Threading:** one dedicated background thread (`THREAD_PRIORITY_BACKGROUND`), one file at a time, what plays now ahead of the rest; cancellation is checked between buffers.

## Consequences

- A replay shows its waveform at once, even after the cache evicted the file.
- The first play of a streamed track shows the fallback until the player has read the whole file; on Wi-Fi the next tracks usually arrive analyzed thanks to prefetch.
- Decoding itself is proven only on the phone (manual test); everything around it is tested with a fake decoder.
- Styles (Sharp, Smooth, Bars, Bars with reflection) and the fallback (Basic slider, wavy progress) are in `MqWaveformProgress`; the choice becomes a setting in #126 (default Sharp + Basic).
