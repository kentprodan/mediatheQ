# 0024. Converted downloads are one progressive Opus file

- Status: Accepted
- Date: 2026-10-26

## Context

A track the device cannot play as it is (Dolby Atmos E-AC-3 JOC on the Pixel 9, which has no JOC decoder) failed to
download: downloads accepted only direct play (#68). ADR 0019 made every conversion HLS, so streams seek; a download,
though, is one file the player reads back from the download cache by its key, sniffing the container.

## Decision

- A download asks once, with `DeviceProfileBuilder.download`: the same direct-play lists as Original quality (so files
  the device plays as they are download exactly as before) and, for the rest, a progressive transcode to Opus 256 kbps
  stereo in Ogg (`TranscodingProfile` `Protocol: http`, `Container: ogg`; the answer is `stream.ogg`, fixture
  `items_playbackinfo/transcode.json`). An HLS answer fails the download. This is the one exception to ADR 0019.
- The download source writes `CONVERTED_DOWNLOAD` (1 or 0) into the download cache's content metadata of the key; the
  Downloads tab reads it and says "Converted copy". What really happened is recorded, not guessed from the codec.
- A conversion has no length and no ranges, and each request makes other bytes (R12): the source refuses to continue a
  converted copy, or to continue any copy as the other kind; "Download again" removes a converted partial copy first.
- Opus 256 rather than FLAC: the source is lossy E-AC-3, and Opus 256 is transparent at a fraction of the size.

## Consequences

- An interrupted converted download fails and starts over when downloaded again; originals still resume.
- Its progress has no percentage (the size is unknown until it ends).
- A complete Ogg file in the cache has its length, so it seeks offline.
- Playing a converted Atmos download does not show the "Dolby Atmos not available" note yet (#89).
