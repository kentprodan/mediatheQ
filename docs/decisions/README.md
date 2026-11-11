# Architecture Decision Records

Each important decision gets one short file: `NNNN-title.md`.
Format: Status, Context, Decision, Consequences. Superseded ADRs stay in the folder with status `Superseded by NNNN`.

| # | Title | Status |
|---|---|---|
| [0001](0001-fresh-start-and-project-identity.md) | Fresh start and project identity | Accepted |
| [0002](0002-phase-order.md) | Phase order: music phone, TV Atmos music, then video | Superseded by 0025 |
| [0003](0003-build-toolchain.md) | Build toolchain: stable AGP, JDK 21, convention plugins | Accepted |
| [0004](0004-material3-expressive-version.md) | Material 3 Expressive on material3 1.5.x | Accepted |
| [0005](0005-navigation-3.md) | Navigation 3 | Accepted |
| [0006](0006-room-3.md) | Room 3 for local database | Accepted |
| [0007](0007-screenshot-testing.md) | Compose Preview Screenshot Testing (standalone plugin) | Accepted |
| [0008](0008-jellyfin-12-own-client.md) | Jellyfin 12+ only, own thin HTTP client | Accepted |
| [0009](0009-project-skills.md) | Project rules live in Claude Code skills | Accepted |
| [0010](0010-sdk-levels.md) | SDK levels: minSdk 31, targetSdk 37, compileSdk 37 | Accepted |
| [0011](0011-github-copilot-role.md) | Role of GitHub Copilot | Accepted |
| [0012](0012-iris-color-scheme.md) | Iris color scheme: Vibrant variant, spec 2025 | Accepted |
| [0013](0013-token-encryption-and-local-cleartext.md) | Token encryption with the Android Keystore; cleartext only on local networks | Accepted |
| [0014](0014-library-filter-styles.md) | Library filters come in two styles, chosen by the user | Superseded by 0028 |
| [0015](0015-cover-images-without-token.md) | Cover images load without the access token | Accepted |
| [0016](0016-opus-in-ogg-for-metered-streaming.md) | Opus in Ogg for streaming on metered networks | Superseded by 0019 |
| [0017](0017-cover-colors.md) | Player colors from the cover with material-color-utilities | Accepted |
| [0018](0018-streaming-cache-and-prefetch.md) | Streaming cache keys and prefetch with CacheWriter | Accepted |
| [0019](0019-hls-for-converted-streams.md) | HLS for converted streams, chosen by a lazy media source | Accepted |
| [0020](0020-saved-queue-flag-in-shared-preferences.md) | A saved-queue flag in SharedPreferences for media buttons | Accepted |
| [0021](0021-offline-mode-filters-the-library.md) | Offline mode filters the library in the repositories | Accepted |
| [0022](0022-open-source-licenses-at-build-time.md) | Open-source licenses generated at build time with AboutLibraries | Accepted |
| [0023](0023-baseline-profile-and-macrobenchmarks.md) | Baseline profile and macrobenchmarks in `:baselineprofile` | Accepted |
| [0024](0024-converted-downloads.md) | Converted downloads are one progressive Opus file | Accepted |
| [0025](0025-milestones-by-platform-and-tests.md) | Milestones by platform, a Polish milestone and a Tests milestone | Accepted |
| [0026](0026-downloadable-google-fonts.md) | App fonts are downloadable Google Fonts | Accepted |
| [0027](0027-on-device-waveform-analysis.md) | Waveforms are computed on the device from whole local files | Accepted |
| [0028](0028-library-hub-of-tiles.md) | The Library is a hub of tiles | Accepted |
| [0029](0029-artist-page-colors-from-image.md) | The artist page takes its colors from the artist image | Accepted |
| [0030](0030-playback-behavior-in-the-session-player.md) | Playback behavior lives in the session's player; weighted shuffle by default | Accepted |
| [0031](0031-own-audio-focus-handling.md) | The playback service handles audio focus itself | Accepted |
| [0032](0032-audio-profiles-per-output.md) | Audio settings per output device | Accepted |
| [0033](0033-automatic-actions-only-while-the-service-lives.md) | Automatic actions work only while the playback service lives | Accepted |
| [0034](0034-bluetooth-lyrics-in-the-session-title.md) | Bluetooth lyrics ride in the session title, the real title in the display title | Accepted |
| [0035](0035-downloads-on-removable-storage.md) | Downloads on removable storage: moved with a restart, guarded while the card is out | Accepted |
| [0036](0036-one-database-per-account.md) | One database per account; switching accounts restarts the app | Accepted |

Template for new ADRs:

```
# NNNN. Title

- Status: Proposed | Accepted | Superseded by NNNN
- Date: YYYY-MM-DD

## Context
## Decision
## Consequences
```
