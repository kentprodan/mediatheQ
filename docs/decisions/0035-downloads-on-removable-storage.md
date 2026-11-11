# 0035. Downloads on removable storage: moved with a restart, guarded while the card is out

- Status: Accepted
- Date: 2026-11-11

## Context

#146 lets downloads and the persistent image cache live on an SD card (or any removable volume an app can write to
without a permission: `Context.getExternalFilesDirs`). The download cache is one Media3 `SimpleCache` shared by the
download manager, the player, the waveform analyzer and the Downloads tab. Its content index lives in Media3's SQLite
database under the cache's uid, which is a `.uid` file in the cache folder; the download index (`DefaultDownloadIndex`)
lives in the same database and knows nothing about folders.

Two Media3 behaviors decide the design:
- A `SimpleCache` that finds one span file with the wrong length (`getSpan`) drops **every** span whose file is missing
  and stores that. With the card out, one read would erase the whole index and the next start would delete the files.
- One folder can have one open `SimpleCache`; swapping it under live users (data sources, running downloads, held hole
  spans) needs locks around every caller.

## Decision

- **The location is read once per process** (SharedPreferences `download_storage`, read before the cache opens, like
  ADR 0020): the files folder of internal storage or of a removable volume, plus its name for messages.
- **A move restarts the app.** Downloads pause, the move waits until the manager is idle, the cache folder is copied
  with its `.uid` file and each copy's length is checked, then the new location is saved and the app restarts. The
  copy opens at the next start with the same uid, so the content index and the download index stay as they are. Any
  failure (no room, a copy error, the location not saved) deletes the copy and changes nothing. At start, a `downloads`
  folder in any location not in use is deleted: what a finished move left behind, or a copy a killed move left.
- **The cache is guarded** (`GuardedCache`): no call reaches the `SimpleCache` while the location's volume is not
  mounted (mount broadcasts, `Environment.getExternalStorageState`) or its folder is gone. Calls that write throw a
  `CacheException`, reads answer "nothing cached", so streams go to the server. No move starts from a missing card. The cache opens on first use with the volume there, so a card put
  in after the app started works without a restart.
- **While the card is out** (from the eject broadcast on), downloads are paused (the download service pauses them again
  after Media3 resumes them on start; they resume only while the service runs), `completedIds` is
  empty and `isDownloaded` false: offline mode lists them as missing, online they stream. The Downloads tab keeps
  listing them with a notice; free space shows 0. A new download waits paused until the card is back. Nothing is
  deleted; everything comes back when the card does.
- The persistent image cache follows the location at the next start (internal while the card is out). The streaming
  cache and the covers of downloaded albums (`download_covers`, one small file per album) stay internal.

## Consequences

- No locks around the cache's users; a move costs a restart (playback stops), said in the move dialog.
- A card pulled out between the guard's check and SimpleCache's own read can still drop spans (a small window;
  ejecting from the system settings sends the eject broadcast first).
- Android kills an app that keeps files open on a volume being unmounted; playing a downloaded track while ejecting
  the card ends the app.
- A download removed while the card is out leaves its bytes on the card (the index forgets it).
- A cover can show for an album whose tracks are on the missing card.
- Adoptable storage needs nothing (it is internal storage); USB drives are not offered (apps get no files folder on
  them).
