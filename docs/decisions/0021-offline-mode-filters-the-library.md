# 0021. Offline mode filters the library in the repositories

- Status: Accepted
- Date: 2026-10-26

## Context

Without a network only downloads play. Until #63 the app shell covered Home, Library and Search with an offline
state, so downloads were reachable only from the Downloads tab. Offline mode (a setting, and automatic without a
network) must instead show only what is on the device in every tab and on the album, artist and playlist pages,
start nothing that is not on the device, and bring the whole library back at once when it is turned off.

Downloads live in Media3's own download index (`:core:download`), not in Room, while every screen reads the library
from Room through `LibraryRepository` and `PlaylistRepository`.

## Decision

- Filter in the repositories, not in the screens: `OfflineFilter` (`:core:data`) combines the setting with
  `NetworkMonitor.online`; while it is on, every list read keeps only completed downloads. Screens get the flag from
  `LibraryRepository.offline` only to choose their empty state. Album, artist and playlist pages follow without changes.
- The completed downloads reach SQL as a list parameter: each list query takes `all: Boolean` and
  `downloaded: List<String>` (item ids) and adds `(:all OR <holds a downloaded track>)`. No Room table mirrors the
  download index: no migration, and nothing to keep in step with Media3.
- `DownloadRepository.completedIds` feeds it: built from the download index only (no library join, which would make
  the library depend on itself), emitted once the index is read and only when the set changes (not with progress).
  `OfflineFilter` takes the download repository through a `Provider`: that repository reads the library's tracks.
- The player sees no network while the setting is on (`OfflineModeNetwork` in `:core:playback`), so a restored queue
  or a media button never streams; such tracks are skipped with the #61 message.
- The app shell's offline state is removed.

## Consequences

- Each downloaded id is one bound variable; SQLite allows 32766. The library is under 10k tracks (architecture skill),
  so the list fits; a test runs a query with 10,000 ids. A much larger library would need a Room table of downloaded
  ids instead (new ADR).
- New list queries must take the same two parameters, or they would leak non-downloaded items into offline mode.
- Add to playlist lists every playlist, also in offline mode (`LibraryRepository.allPlaylists`): adding needs the
  server, not downloads.
