# 0056. Bit-perfect in the DAC's container, float on that route only

- Status: Accepted (owner, 2026-11-24, engine plan in `docs/research/audio-video-engine.md`)
- Date: 2026-11-24

## Context

Bit-perfect USB (#22, #65, #165) set a 16-bit mixer attribute, since Media3's `DefaultAudioSink` writes only 16-bit or
float PCM and an AudioTrack reaches a bit-perfect mixer only in its exact encoding. A 24-bit file lost its bit depth,
and a DAC that offers only 24 or 32-bit attributes got no bit-perfect at all. PixelPlayer solves both with a custom
`AudioOutputProvider` that widens the sink's samples into the DAC's integer container (research, section 3.3).

## Decision

- The player's sink is a `DefaultAudioSink` we build: float output whenever `FloatOutputProbe` (min buffer size, then a
  real float AudioTrack, once per process) says the device takes it, a processor chain that halves rates above 192 kHz
  (`HiResRateCap`, Sonic), and `BitPerfectOutputProvider` over the stock AudioTrack provider.
- Decoders are told the sink takes float only on a bit-perfect DAC route (`AudioOutputState.floatOutput`: the setting,
  a USB DAC, the probe): `PassthroughPolicyAudioSink` answers "with transcoding" for float elsewhere, exactly what a
  sink without float output says. So everywhere else decoding stays 16-bit with the processors, as before, and devices
  whose FLAC decoder breaks float (PixelPlayer) are only exposed on that route.
- `MixerAttributeSelector` matches rate and channel mask and picks the encoding: on a float pipeline 24, then 32, then
  16 bits; on a 16-bit one 16, then 24, then 32. Float mixer attributes are never picked.
- `BitPerfectOutputProvider` opens the AudioTrack in the chosen encoding when the sink's config (16-bit or float, not
  offload or tunneling) has the attribute's rate and mask, and `PcmWideningAudioOutput` (adapted from PixelPlayer,
  GPL-3.0) converts every write: 16 and 24-bit sample values are kept exactly, only the container changes.
- `AudioOutputController` still sets and clears the preference; the provider only reads it. It does not replace
  `AudioTrackReopener`: an AudioTrack opened before the preference still stays on the mixer, whatever its encoding.

## Consequences

- 24-bit files reach the DAC with all 24 bits, and DACs offering only 24 / 32-bit attributes now get bit-perfect.
- Float is decided when a decoder starts. A decoder already running when the route turns bit-perfect keeps 16-bit
  until it restarts; the reopen of #165 restarts it when the setting turns on mid-track.
- On a bit-perfect route with no attribute for the rate, a float track goes to the normal mixer as float, and the
  192 kHz cap does not apply there (the float path skips the processors).
- Only one player sets the preference today. With two players (crossfade, #219) only the active one may clear it.
- Proof needs the owner's DAC: `dumpsys media.audio_flinger` shows the bit-perfect thread's format.
