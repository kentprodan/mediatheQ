# 0039. Dolby Atmos passthrough over HDMI: capabilities from Media3, decision in `AudioPolicy`, truth from the audio track

- Status: Accepted
- Date: 2026-11-11

## Context

TV 1 must send E-AC-3 JOC (M4A) and TrueHD (MKA) music untouched over HDMI: Streamer -> LG TV -> eARC -> LG DSN11RG.
The media3 skill (section 4) already states the rules: capabilities from the real route, never transcode Atmos where
passthrough is possible, no EQ / normalization / offload in passthrough, never silently downgrade, the badge only when
the actual output is Atmos. Today `AudioRoute` is `UsbDac | Other`, `AudioMode` is `Offload | Processed | BitPerfect`,
and `DeviceProfileBuilder` declares `eac3` only with an `audio/eac3-joc` decoder and never `truehd`.

What the platform and Media3 offer (read 2026-11-11):
- `DefaultAudioSink` chooses passthrough by itself when `AudioCapabilities` reports the encoding supported for the
  routed device; `MediaCodecAudioRenderer` prefers the sink's passthrough over a decoder. No custom sink is needed.
- `AudioCapabilities.getCapabilities(context, audioAttributes, routedDevice, spatializerChannelMasks)` and
  `supportsEncoding(C.ENCODING_E_AC3_JOC | C.ENCODING_DOLBY_TRUEHD)`; `AudioCapabilitiesReceiver` reports changes
  (HDMI plug, the TV's surround setting, routing). Both `@UnstableApi`.
- The platform's `AudioManager.getDirectProfilesForAttributes` (API 33) answers for the routed device only and Google
  says to ask again at every device change; `AudioDeviceInfo.TYPE_HDMI` (23), `TYPE_HDMI_ARC` (23), `TYPE_HDMI_EARC`
  (31); `AudioFormat.ENCODING_E_AC3_JOC` (28), `ENCODING_DOLBY_TRUEHD` (25).
- `AnalyticsListener.onAudioTrackInitialized(AudioSink.AudioTrackConfig)` carries the real `encoding` of the track the
  sink opened: a JOC or TrueHD encoding there means the bitstream left the device untouched.
- Google states the Streamer can decode Atmos on the device, so a JOC decoder likely exists next to passthrough.

## Decision

1. **Capabilities snapshot from Media3.** `PassthroughCapabilities` (fun interface in `:core:playback`, like
   `AudioDecoders`) returns the MIME types the sink can pass through on the current route (`audio/eac3-joc`,
   `audio/eac3`, `audio/ac3`, `audio/true-hd`), built from `AudioCapabilities` for the routed `AudioDeviceInfo` and the
   player's `AudioAttributes`. Only `MediatheqPlaybackService` touches `AudioCapabilities`; the service registers one
   `AudioCapabilitiesReceiver` and re-runs the output decision on change. The platform API is not used: the sink decides
   from `AudioCapabilities`, so our request to Jellyfin and the sink's behavior read the same answer.
2. **Route and mode.** `OutputKind.Hdmi` (`OutputId` `hdmi:<hash of product name>`), `AudioRoute.Hdmi` for
   `TYPE_HDMI` / `TYPE_HDMI_ARC` / `TYPE_HDMI_EARC`, `AudioMode.Passthrough`. `AudioPolicy.decide` gives `Passthrough`
   when the route is HDMI, the output profile's **Dolby Atmos passthrough** switch (default on, HDMI outputs only,
   ADR 0032 amended) is on and the playing track's codec is in the passthrough set. In `Passthrough`: offload off
   (decided per route: off whenever the set is not empty and the switch is on), equalizer off, normalization off,
   volume 1, no fades. `wantsPassthrough(settings, route, capabilities)` is the pure per-route function.
3. **DeviceProfile.** `DeviceProfileBuilder.build(decoders, passthrough, quality)`: `eac3` with a JOC decoder **or** JOC
   passthrough; `truehd` only with TrueHD passthrough. No new containers.
4. **Never transcode Atmos where passthrough is possible.** A pure `atmosRequest(track, passthrough, quality)` in
   `StreamResolver` asks for the **original** (no bitrate cap, the passthrough profile) of a `SpatialFormat.DolbyAtmos`
   track whose codec passes through, whatever the network quality says; every other track follows the quality rules
   of ADR 0019. The jellyfin skill section 5 rule becomes code, with a contract fixture of the request.
5. **Truth on screen.** `AudioOutputState.passthroughEncoding` is set from `onAudioTrackInitialized` (cleared on
   `onAudioTrackReleased`); `AudioOutputInfo.atmosPassthrough` is true only for a JOC or TrueHD encoding. The TV Now
   Playing badge "Dolby Atmos" reads that flag, never the metadata. A JOC track that plays decoded (route without
   passthrough, decoder present) shows "Dolby Atmos not available · Decoded on this output"; a server conversion keeps
   "Playing in stereo on this output" (`atmosConverted`).
6. **Failure is visible.** A passthrough track whose `AudioTrack` fails to open or write (route changed under it) is
   re-prepared once (same path as the bit-perfect `-32` handling); a second failure is a `PlaybackError` with the
   reason, never a silent PCM downgrade. The next track re-selects its stream after a route change (skill rule 3).

## Consequences

- The phone is unchanged: its route is never HDMI, `PassthroughCapabilities` is empty there, and every existing
  `AudioPolicy` / `DeviceProfileBuilder` test keeps passing with an empty passthrough set.
- `AudioCapabilities` and `AudioCapabilitiesReceiver` are unstable API: checked at every Media3 bump, used in one class.
- Passthrough cannot be proven on the JVM: the decision functions are unit tested exhaustively; the chain is proven on
  the Streamer with the soundbar's display (E-AC-3 JOC is the acceptance criterion; TrueHD is a second manual item).
- Android TV's "Surround sound" setting (Auto / Never / Manual) is outside the app; the manual test lists the expected
  values, and the Settings page explains that the switch cannot override the system.
- Bluetooth outputs on the TV box pass nothing through: Atmos tracks then decode (JOC decoder) or convert, with the
  note shown, as on the phone.

## Amendment (2026-11-13, #241)

- The switch is our decision only: `DefaultAudioSink` passes through whatever `AudioCapabilities` allows, so with the
  switch off an Atmos file can still leave as a bitstream while the mode says `Offload` / `Processed` (offload,
  normalization and EQ then follow the mode; the badge follows the opened encoding). A real "off" would need a sink
  built with default capabilities; not needed for TV 1.
- E-AC-3 JOC on a route that takes only E-AC-3 passes through as E-AC-3 (Media3's fallback, the 5.1 core): the
  policy treats it as passthrough too. A TrueHD track counts as Atmos only when the file is Atmos.
- A phone on USB-C to an HDMI display is an HDMI output too (its own profile); "the phone's route is never HDMI"
  holds for the normal setup only.
- The retry after a failed passthrough AudioTrack follows what the last opened track was, not the current mode: a
  route change is often decided before the write error arrives.

## Amendment (2026-11-13, #242)

- Stream requests use the route's passthrough set only while the output's switch is on: with the switch off, Atmos
  tracks follow the quality setting like any other track (owner decision).
- "No bitrate cap" is sent as the explicit large cap (1000000000) the app always sends, not an absent
  `MaxStreamingBitrate`: Jellyfin replaces a missing value with its own lower default.
- JOC on a route that takes only plain E-AC-3: the profile does not declare `eac3` (rule 3 asks for JOC), so the
  server converts it to stereo with the note, although `AudioPolicy` would pass a direct-played JOC file through as
  the 5.1 core. Kept: no such route in the owner's chain; revisit if one appears.
