# 0016. Opus in Ogg for streaming on metered networks

- Status: Accepted
- Date: 2026-10-19

## Context

On metered networks (cellular, metered Wi-Fi) the app asks Jellyfin for at most 256 kbps, so lossless and high-bitrate files are converted. The media3 skill wants Opus in a container Media3 extracts reliably (Ogg or WebM/Matroska), chosen once. On the owner's Jellyfin 12.2.0 both work: `stream.ogg` arrives as `audio/ogg` (starts with `OggS`), `stream.webm` as `video/webm`. Opus decoding uses the platform decoder, present on Android 10+ (our minSdk is 31).

Findings on 12.2.0 that shape the request:

- Without `MusicStreamingTranscodingBitrate` in the device profile Jellyfin converts music at 128 kbps whatever the cap; with it set to 256000 the stream is 256 kbps.
- Live transcodes answer `Accept-Ranges: none` and have no length, so they cannot be seeked; the original files (direct play) can.
- The transcoding URL contains the access token as `ApiKey`; the app strips it and sends the `Authorization` header (R1).

## Decision

- Transcoding target: **Opus in Ogg**, 256 kbps, at most 2 channels (`DeviceProfileBuilder.OPUS_256`).
- Reduced quality applies on any metered network (`NetworkMonitor.isMetered`); unmetered Wi-Fi and Ethernet get the original file.
- Seeking a converted stream is not supported yet: `PlaybackStream.canSeek = false` for transcodes. Seekable converted streams (HLS) are a separate issue.

## Consequences

- On metered networks the seek bar must be disabled for transcoded tracks (#13 reads `canSeek`).
- A device test on the Pixel 9 confirms Ogg/Opus plays (`/test`); if it fails there, switch to WebM with a new ADR.
- Gapless across converted tracks is not guaranteed (media3 skill, section 8).
