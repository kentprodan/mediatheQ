# 0050. Video stream selection: device profile from real capabilities, HLS fMP4 transcodes, server-decided subtitle delivery, downloads as originals

- Status: Proposed (owner approval pending)
- Date: 2026-11-12

## Context

Music streams are chosen by Jellyfin's `PlaybackInfo` with a `DeviceProfile` built from the phone's decoders and
the quality setting (media3 skill section 3, jellyfin skill section 5); conversions are HLS fMP4 through a lazy
media source (ADR 0019); downloads are the original or one converted Opus file (ADR 0024). Video adds video codecs,
HDR ranges, several audio and subtitle streams per file, and subtitle delivery methods the server decides from
`SubtitleProfile`s (`External`, `Embed`, `Hls`, `Encode`, `Drop`). Media3 plays MP4, Matroska, WebM, MPEG-TS and HLS,
side-loads WebVTT / TTML / SubRip / SSA, reads embedded text and PGS tracks, and decodes what the platform decoders
decode; HDR output needs the decoder profile and the display type (`Display.getHdrCapabilities`). Google's Pixel 9
page lists "HDR support" without naming Dolby Vision (checked 2026-11-12).

## Decision

- **Profile from facts:** `DeviceProfileBuilder.video(audioDecoders, videoDecoders, displayHdr, quality)` (pure,
  exhaustively tested): direct play containers `mp4`, `m4v`, `mkv`, `webm`, `ts`; video codecs present in
  `MediaCodecList` (`h264`, `hevc`, `av1`, `vp9`; `dvhe` / `dvh1` only with a `video/dolby-vision` decoder); audio
  codecs as for music with multichannel (`eac3` under the JOC rule, never `truehd` / `dts` on the phone);
  `CodecProfile` conditions `VideoRangeType EqualsAny` the ranges the display and the decoder profiles support (SDR
  always; HDR10 / HLG / HDR10+ with Main10 decoders and the display types; `DOVI*` only with the decoder and
  `HDR_TYPE_DOLBY_VISION`), `Width <= 3840`, `VideoBitDepth <= 10`, `AudioChannels <= 8`; `MaxStreamingBitrate` from
  the video quality per network kind (defaults Original on unmetered, 8 Mbps on metered).
- **Transcodes are HLS fMP4** (`Type=Video, Container=mp4, Protocol=hls, VideoCodec=hevc,h264, AudioCodec=aac,opus`,
  `MaxAudioChannels` 6, `BreakOnNonKeyFrames` true, `EnableSubtitlesInManifest` false), played by the existing
  `LazyStreamMediaSource` HLS child with the auth header and no cache; `StartTimeTicks` carries the resume point.
  A remux (`SupportsDirectStream`) is accepted as it is. The server tone-maps HDR it may not send.
- **Subtitles are the server's decision:** the app sends `AudioStreamIndex` / `SubtitleStreamIndex` and
  `SubtitleProfile`s (`External` for `subrip`, `webvtt`, `ass`, `ssa`, `mov_text`; `Embed` for those and `pgssub`;
  `Hls` for `webvtt`; `Encode` for `pgssub` / `dvdsub`, and for `ass` / `ssa` when "Burn in styled subtitles" is
  on). The answer's `DeliveryMethod` / `DeliveryUrl` become `SubtitleDelivery` entries: external ones are side-loaded
  as `MediaItem.SubtitleConfiguration` through the authenticated data source (URLs token-free, R10 applies to
  `DeliveryUrl`), embedded ones are selected with `TrackSelectionOverride`, burned-in ones are shown as such. A track
  change that needs a new server stream re-resolves at the current position with a new play session.
- **Defaults** come from Settings -> Playback -> Video (subtitle mode Off / Forced only / Language, preferred audio
  language), else the server's default indexes.
- **Downloads are originals only:** `DeviceProfileBuilder.videoDownload` has no transcoding profile; a `PlaybackInfo`
  answer that is not a direct play fails the download with a message. External text subtitles download beside the
  file under `<MediaId>:sub:<index>` keys. No `DownloadHelper`, no HLS downloads in Mobile 3.

## Consequences

- What the phone cannot decode is converted by the server, never failed on the client; a wrong HDR declaration shows
  as a transcode in the dashboard, and the player's info line says what plays.
- Downloads of videos the phone cannot direct-play are refused (the owner sees why); converted video downloads are a
  later ADR if wanted.
- `DeviceProfileBuilder.build` (music) is unchanged; the music profile and the video profile are separate functions
  with separate tests.
- New jellyfin R lines are expected from the fixtures (external subtitle start offset on transcodes, `DeliveryUrl`
  with `ApiKey`, resume thresholds); each gets a fixture and a test.
- ADR 0024 is amended (no converted downloads for video); ADR 0019 gains a note that video transcodes use the same
  lazy source.
