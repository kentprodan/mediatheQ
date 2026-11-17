# TV fluidity: what the other Android TV clients do, and what mediatheQ changes

Research notes (2026-11-17). The owner cannot use the TV app daily because of stutters (Home scrolling, Movies and
Series grids, opening pages, app start). Four FOSS Android TV clients for Jellyfin were read in full for performance and
feel; Moonfin was studied the day before (`other-clients.md`). Ideas only: Wholphin and the official client are GPL-2.0,
Void and JellyPlay GPL-3.0 (their own Kotlin could be reused with attribution, nothing is copied here).

| Client | UI stack | Size | Notes |
|---|---|---|---|
| jellyfin-androidtv (official) | Leanback Views (RecyclerView rows) + some Compose Foundation, no tv-material | 39k lines Kotlin | R8 + resource shrinking in release |
| Wholphin | Compose for TV, Media3 + optional MPV, Hilt, Room | 107k | R8; Coil tuned; cache windows; ScrollToTop rows |
| Void | Compose, Paging 3, MPV / Exo | 47k | no R8 in release; a priority dispatcher; prefetching |
| JellyPlay | Compose (KMP), TV flavor | large (generated SDK) | baseline profile for phone only (TV gap documented), Compose stability config |

## Findings in mediatheQ

1. **The build under test is the debug build.** Compose in debug runs without R8 and with extra runtime checks; on a
   Streamer the difference is large. Every studied client ships R8 in release. mediatheQ already has `daily` (release,
   R8, signed with the debug key): it is now what gets installed.
2. **No baseline profile for the TV app.** `:baselineprofile` covers the phone only, so every TV screen is JIT-compiled
   while it is first used (stutters on the first scrolls of each screen). JellyPlay documents the same gap; mediatheQ
   can close it because a real Streamer is reachable over adb (`useConnectedDevices`).
3. **TV Home recomposes all visible rows on every D-pad move.** `focusedVideo` (for the hero) is state read inside
   `Rows`, and `Cards` is built anew on each recomposition, so every row's content lambda changes. Wholphin keeps the
   header outside the scrolling list and computes the backdrop in a service with a delay; card callbacks are
   remembered.
4. **No Compose stability configuration.** The models come from `:core:model` / `:core:data`, which Compose does not
   compile, so they are unstable: with strong skipping they compare by identity, and every re-emitted list (a sync, a
   live update) recomposes everything. JellyPlay declares its immutable types in `compose-stability.conf`.
5. **The library sync competes with the UI.** Today's log shows 20-30 MB freed by the GC every few seconds while a sync
   runs at start. Void runs UI requests on a high-priority dispatcher and background work on a low one.
6. **Lists.** Wholphin gives grids a `LazyLayoutCacheWindow(ahead = 2 screens)` so posters are composed and their
   images requested before they scroll in, and a ScrollToTop `BringIntoViewSpec` so the focused row always lands at
   the same place. Wholphin turns crossfade off globally; the owner prefers a short fade (~150 ms).

What is already right: images are requested at the size drawn (`ArtworkInterceptor` + width buckets), list items have
keys and content types, the disk cache is the app's own.

## Owner decisions (2026-11-17)

See the memory note `tv-performance-decisions`: daily build; measure over adb; card focus 1.05x + border; fixed hero
that changes after ~0.5 s; focused row at a fixed top position; images fade ~150 ms; accelerating grid scroll with a
big letter; full sync only when idle; pages from the cache at once; short fade transitions by default; a top tab bar
instead of the side drawer; titles only on the focused card; 6 posters per row; a hidden FPS overlay.

## Measurements

Home scroll (20 right, 6 down, 15 right, 6 up) on the Google TV Streamer, `dumpsys gfxinfo`:

| Build | Frames | Janky | p50 | p90 | p99 |
|---|---|---|---|---|---|
| debug (main at #487) | 143 | 97.9 % | 300 ms | 850 ms | 1500 ms |
| daily (R8), same code | 866 | 12.5 % | 36 ms | 61 ms | 105 ms |

The daily build alone draws six times the frames for the same scroll and cuts the typical frame from 300 to 36 ms.
The target on a 60 Hz TV is under 16 ms: the code changes above have to close the rest. The script that drives the
D-pad and reads the stats is kept with the issue that measures them.
