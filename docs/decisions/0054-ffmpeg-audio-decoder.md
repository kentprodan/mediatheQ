# 0054. Jellyfin's FFmpeg audio decoder behind the platform decoders

- Status: Accepted (owner, 2026-11-24)
- Date: 2026-11-24

## Context

The player used only the platform's `MediaCodec` decoders. What a device lacks went to the server for conversion:
DTS and DTS-HD everywhere (neither the Pixel 9 nor the Streamer decodes them), TrueHD unless the route passes it
through, E-AC-3 JOC (Atmos) on the Pixel (Media3 1.11 removed the plain E-AC-3 fallback). A conversion is lossy (Opus
256 for music) and costs the server CPU. The engine research (`docs/research/audio-video-engine.md`, section 3.1) found
every serious Android client adds Media3's FFmpeg audio extension. The owner chose the prebuilt one Jellyfin publishes
(`org.jellyfin.media3:media3-ffmpeg-decoder`, GPL-3.0, used by jellyfin-androidtv, Wholphin, Findroid and Rhythm)
over an own build.

## Decision

- `:core:playback` depends on `org.jellyfin.media3:media3-ffmpeg-decoder` (1.9.0+1, about 1.4 MB per ABI). Its
  Media3 1.9 dependencies resolve to our 1.11.
- The player's `DefaultRenderersFactory` uses `EXTENSION_RENDERER_MODE_ON` (the platform first, FFmpeg for what it
  lacks) and decoder fallback. With ON, a format both can handle stays on `MediaCodec`, so AC-3 / E-AC-3 / JOC still
  reach passthrough and Atmos is never decoded by FFmpeg on a route that takes it. `PREFER` is not used: it would only
  matter for formats both decode, and none of ours needs FFmpeg first.
- `AudioDecoders` adds what `FfmpegLibrary.supportsFormat` reads to the platform's list, so the device profile declares
  it: `truehd` and `dts` / `dca` with a decoder (no longer TrueHD only with passthrough), and `eac3` (JOC) on the
  phone, which FFmpeg decodes to its 5.1 bed.
- A track whose audio no renderer takes runs a silent clock in Media3 without an error: `SkipFailedTrackListener`
  treats it as the track's failure (skip, or pause on the last).

## Consequences

- DTS, DTS-HD MA, TrueHD and Atmos E-AC-3 play from the original file on the phone and on the TV without passthrough,
  decoded on the device (lossless for TrueHD and DTS-HD MA), instead of a server conversion. The "Decoded on this
  output" note (ADR 0039) covers Atmos decoded this way.
- Passthrough is unchanged: the route's passthrough set still wins.
- No platform DTS decoders are hidden. Other clients report wrong output from some vendors' DTS decoders, but neither
  of our devices has one. Revisit with a quirk entry if a device shows it.
- The extension is unstable API; check it at each Media3 bump, and bump the Jellyfin artifact when they publish one
  for a newer Media3.
