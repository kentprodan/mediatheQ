# 0055. The audio sink is wrapped for passthrough recovery and the passthrough switch

- Status: Accepted (owner, 2026-11-24)
- Date: 2026-11-24

## Context

The media3 skill forbids a custom `AudioSink` without an ADR. Two problems on the TV need one (#377, engine research
section 3.2):

- HDMI often drops for a few seconds at playback start, and some TV audio HALs never resume a paused passthrough
  track. Media3 then either throws on the dead track and reselects against a sink with no capabilities, or reselects
  on the capabilities change: both end with Atmos decoded to PCM, or with silence.
- The output's passthrough switch was not real (ADR 0039 amendment): `DefaultAudioSink` passes through whatever
  `AudioCapabilities` allows, so with the switch off Atmos still left as a bitstream.

Moonfin solved the first with tested code under GPL-3.0, which the owner chose to copy.

## Decision

- `PassthroughRenderersFactory` (a `DefaultRenderersFactory`) builds the stock `DefaultAudioSink` and wraps it twice:
  - `PassthroughRecoveryAudioSink` (Moonfin's, adapted): holds a dead bitstream track and defers a capabilities loss
    for up to 5 s (`RouteFlapHold`), and asks for a rebuild when a bitstream track's position stays frozen with data
    waiting (`PassthroughSilenceRecovery`: 700 ms, 3 s grace at start, 3 s between recoveries, then a short write
    hold). The service rebuilds with a seek in place on the main thread.
  - `PassthroughPolicyAudioSink`: a bitstream not in the route's passthrough set while the output's switch is on
    (`AudioOutputState.requestPassthrough`) is unsupported here, so the renderer decodes it (MediaCodec, then FFmpeg,
    ADR 0054). It only narrows: it never claims what the sink below refuses.
- Both are `ForwardingAudioSink`s: everything not named above is a straight delegate, and until a dead track is proven
  the sink behaves exactly like the stock one.

## Consequences

- The passthrough switch now works: off means decoded, with the "Decoded on this output" note. A change applies from
  the next track, like a route change.
- `AudioSink` is unstable API; these two classes are checked at each Media3 bump (1.11 changed `configure`).
- The recovery cannot be proven on the JVM beyond its decision classes (Moonfin's tests, copied): the manual test is a
  pause of several minutes with Atmos passthrough on the Streamer, and an HDMI input switch at playback start.
