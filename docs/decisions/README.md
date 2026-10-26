# Architecture Decision Records

Each important decision gets one short file: `NNNN-title.md`.
Format: Status, Context, Decision, Consequences. Superseded ADRs stay in the folder with status `Superseded by NNNN`.

| # | Title | Status |
|---|---|---|
| [0001](0001-fresh-start-and-project-identity.md) | Fresh start and project identity | Accepted |
| [0002](0002-phase-order.md) | Phase order: music phone, TV Atmos music, then video | Accepted |
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
| [0014](0014-library-filter-styles.md) | Library filters come in two styles, chosen by the user | Accepted |
| [0015](0015-cover-images-without-token.md) | Cover images load without the access token | Accepted |
| [0016](0016-opus-in-ogg-for-metered-streaming.md) | Opus in Ogg for streaming on metered networks | Superseded by 0019 |
| [0017](0017-cover-colors.md) | Player colors from the cover with material-color-utilities | Accepted |
| [0018](0018-streaming-cache-and-prefetch.md) | Streaming cache keys and prefetch with CacheWriter | Accepted |
| [0019](0019-hls-for-converted-streams.md) | HLS for converted streams, chosen by a lazy media source | Accepted |
| [0020](0020-saved-queue-flag-in-shared-preferences.md) | A saved-queue flag in SharedPreferences for media buttons | Accepted |
| [0021](0021-offline-mode-filters-the-library.md) | Offline mode filters the library in the repositories | Accepted |

Template for new ADRs:

```
# NNNN. Title

- Status: Proposed | Accepted | Superseded by NNNN
- Date: YYYY-MM-DD

## Context
## Decision
## Consequences
```
