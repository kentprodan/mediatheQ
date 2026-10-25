# Manual test queue

Items are added by `/end` and run with `/test`.

## #1 Project skeleton: Gradle, version catalog, convention plugins, empty app
- [x] Pixel 9: `.\gradlew.bat installDebug`, open mediatheQ from the launcher -> app opens and shows "mediatheQ" centered, no crash - passed 2026-10-25
- [ ] MacBook Air M1: fresh clone, `./gradlew assembleDebug` -> BUILD SUCCESSFUL

## #8 ADR: role of GitHub Copilot and copilot-instructions.md
- [ ] GitHub: open the next PR -> a Copilot code review appears on it (needs automatic Copilot review enabled in the repo settings)

## #4 Design system foundation: MediatheqTheme, Iris colors, type, shapes, spacing, motion, screenshot setup
- [x] Pixel 9: install, open the app, Home -> "Open theme showcase", scroll through colors, type and shapes in light mode -> readable, Iris violet, no clipped text - passed 2026-10-25
- [x] Pixel 9: switch the system to dark mode, reopen -> dark Iris scheme, readable - passed 2026-10-25

## #6 Navigation shell: Navigation 3, four tabs with own back stacks, mini player slot
- [x] Pixel 9: Library -> Open detail -> Open next; switch to Search and back to Library -> still on "Library 2" - passed 2026-10-25
- [x] Pixel 9: in "Library 2", swipe back slowly from the screen edge -> the predictive back preview shows "Library 1", releasing goes there; at the Library root, back returns to Home; at the Home root, back closes the app - passed 2026-10-25

## #7 Connect to Jellyfin and log in (password + Quick Connect)
- [x] Pixel 9 on home Wi-Fi: fresh install, enter `192.168.178.34:8096` -> "Sign in to trueNAS"; wrong password -> "Wrong username or password."; right password -> the tab shell opens - passed 2026-10-25
- [x] Pixel 9 on mobile data with Tailscale on: fresh install, enter the server's Tailscale address (100.x or MagicDNS name) with port 8096 -> sign-in works over http - passed 2026-10-19
- [x] Pixel 9: Sign in -> "Use Quick Connect" -> approve the code from the Jellyfin web UI (Settings -> Quick Connect) -> the tab shell opens within ~5 s - passed 2026-10-25
- [x] Pixel 9: Home -> "Log out" -> back at "Welcome to mediatheQ"; Jellyfin dashboard -> Devices no longer shows an active session for the phone - passed 2026-10-25
- [x] Pixel 9: sign in, force-stop the app, reopen -> goes straight to the tab shell (token read back and decrypted) - passed 2026-10-25
- [x] Pixel 9: `.\gradlew.bat :core:datastore:connectedDebugAndroidTest` -> AndroidKeystoreTokenCipherTest passes (Keystore round trip, fresh IV) - passed 2026-10-25
- [x] Pixel 9: on the server screen enter `http://example.com` -> "Use https:// for servers outside your home network or Tailscale." - passed 2026-10-25

## #9 Library sync: Room 3 database, full sync from Jellyfin, FTS5 search index
- [x] Pixel 9 on home Wi-Fi: fresh install, sign in -> the first sync runs by itself; measure the time from sign-in until it finishes (JVM check on the dev PC: 5.8 s) and note it in issue #9 - passed 2026-10-25
- [x] Pixel 9: after the sync, Library counts match the server: 455 album artists, 989 albums, 2729 tracks, 11 playlists (642 entries). Until #10 there is no Library screen: check with App Inspection -> Database Inspector on `mediatheq.db` - passed 2026-10-25
- [x] Pixel 9: start a fresh sync, swipe the app away mid-sync, reopen within an hour -> the sync continues and ends with the same counts - passed 2026-10-25
- [ ] Pixel 9: Home -> Log out -> Database Inspector shows every table empty - failed 2026-10-25, see #51

## #10 Library screen: albums grid, artists, playlists, songs
- [ ] Pixel 9, **release build** (`.\gradlew.bat installRelease` or a signed R8 build), synced library (989 albums): Library -> Albums, fling the grid top to bottom and back with 2, 3 and 4 columns -> no visible stutter; covers fade in from their BlurHash
- [x] Pixel 9: the Library title sits right under the status bar (no double gap), the last row is not hidden behind the navigation bar - passed 2026-10-25
- [x] Pixel 9: change columns to 4 and sort Albums by Year, switch to Songs and back, force-stop and reopen -> 4 columns and Year are kept, the scroll position per tab is kept while the app runs - passed 2026-10-25
- [x] Pixel 9: view menu -> Connected buttons -> the filters become connected buttons; reopen the app -> still connected buttons - passed 2026-10-25 (chips flash first, see #52)
- [x] Pixel 9: pull down on Albums -> the refresh spinner shows while the sync runs, then hides - passed 2026-10-25
- [x] Pixel 9: Artists tab lists only album artists (455 on the test server) - passed 2026-10-25

## #11 Album screen: header, track list, play and shuffle
- [x] Pixel 9: Library -> tap an album -> the cover flies from the grid into the album header and back on Back; it feels smooth (corners go from rounded to more rounded during the flight; a BlurHash flash is acceptable while the larger cover loads) - passed 2026-10-25
- [x] Pixel 9: force-stop the app, reopen, open an album at once (cold database) -> the cover still flies; if it jumps or only fades, note it in issue #11 - passed 2026-10-25
- [x] Pixel 9: on an album page, start a predictive back gesture halfway and cancel -> the page stays, no stuck cover; complete it -> the cover flies back into the grid - passed 2026-10-25
- [x] Pixel 9: a multi-disc album shows "Disc 1", "Disc 2" headers; scrolling collapses the large title into the top bar - passed 2026-10-25
- [x] Pixel 9 (after #12): Play starts track 1, tapping a track starts there, Shuffle does not always start at track 1 - passed 2026-10-25 (after the #53 fix)

## #12 Playback core: MediaLibraryService, PlaybackConnection, stream selection, reporting
- [x] Pixel 9 on home Wi-Fi: open a FLAC album -> Play -> music plays; Jellyfin dashboard -> Activity/Sessions shows "Direct playing" for the phone - passed 2026-10-19
- [x] Pixel 9 on mobile data (Tailscale on): play a FLAC track -> Jellyfin shows "Transcoding" to Opus ~256 kbps; `adb shell dumpsys connectivity | findstr METERED` shows the active network is metered (if Tailscale makes it look unmetered, note it in issue #12) - passed 2026-10-19
- [x] Pixel 9 on mobile data: Opus/Ogg plays without errors (ADR 0016 device check); seeking is not possible yet (expected) - passed 2026-10-19
- [x] Pixel 9: notification shows the track with cover; play/pause, next and previous work from the notification - passed 2026-10-19
- [ ] Pixel 9: the same controls work from the lock screen (owner: run once the in-app player from #13 exists)
- [ ] Pixel 9: unplug wired headphones (or disconnect Bluetooth) while playing -> playback pauses; a phone call -> music pauses and resumes after
- [x] Pixel 9: tapping the notification opens the app - passed 2026-10-19
- [ ] Pixel 9: Jellyfin dashboard shows the playing track, its progress moving, and it disappears when playback stops (Stopped reported, transcode ended)
- [x] Pixel 9: `.\gradlew.bat :app:connectedDebugAndroidTest` -> PlaybackServiceTest passes (service starts, controller connects) - passed 2026-10-25
- [ ] Pixel 9: Home -> Log out while music plays -> playback stops


## #13 Mini player and Now Playing with cover colors
- [ ] Pixel 9: play an album -> the mini player slides in above the nav bar with cover, title, artist; play/pause and next work from it; the list above is not hidden behind it - failed 2026-10-25, see #53 (fixed; retest)
- [ ] Pixel 9: tap the mini player, and separately swipe it up -> Now Playing opens, the cover flies from the pill into the large cover; swipe down, the close button and Back each return to the mini player with the cover flying back
- [ ] Pixel 9: Now Playing colors follow the cover (try 3 albums with very different covers); changing track to another album blends the colors, no snap; an album without cover stays Iris
- [ ] Pixel 9 on Wi-Fi: the seek bar moves smoothly, dragging it seeks; on mobile data (transcode) it is disabled
- [ ] Pixel 9: on the album page the playing track shows the equalizer icon and a colored title; it moves to the next track when the player advances
- [ ] Pixel 9: motion feels right (no jank opening/closing Now Playing, mini player enter/exit) and there are no haptics on plain taps
- [ ] Pixel 9: TalkBack on the album page reads "Now playing" on the playing track; the mini player announces "Open player"

## #14 Queue: view, reorder, remove, persistence and restore
- [ ] Pixel 9: play an album, seek into track 3, then reboot the phone -> open the app: the mini player shows track 3 paused; Play continues at about the same position; the queue screen lists the whole album
- [ ] Pixel 9: play an album, wait 15 s, `adb shell am force-stop io.github.kentprodan.mediatheq` -> reopen: same queue, same track, paused, position at most ~10 s behind
- [ ] Pixel 9: Now Playing -> queue button -> drag a track by its handle up and down (also past the screen edge so the list scrolls) -> a light tick on pick-up and each swap, the order changes and the player plays in the new order; no jumps or flicker
- [ ] Pixel 9: swipe a queue entry left and right -> it is removed; removing the playing entry continues with the next; Back closes the queue, a second Back closes the player
- [ ] Pixel 9: album track menu -> Play next / Add to queue, album top bar menu -> Play next (whole album), Songs tab menu -> Add to queue -> the queue screen shows them in the right places; with nothing playing, Play next starts playback
- [ ] Pixel 9: album started with Shuffle -> the queue shows the shuffled order without drag handles; Play next on a track plays right after the current one
- [ ] Pixel 9: TalkBack on the queue: rows read "Now playing" for the current one and offer Move up / Move down / Remove from queue actions

## #16 Playlist screen and editing
- [ ] Pixel 9: open an album with 300+ tracks (or the largest album) -> top bar menu -> Add to playlist -> pick a playlist -> the dialog closes, the playlist shows all tracks in album order (count matches), no error; the server's playlist (Jellyfin web) has the same count
- [ ] Pixel 9: Library -> Playlists -> "+" -> name "mediatheq test" -> Create -> the playlist appears; add 3 tracks from album track menus -> within a few seconds the playlist cover changes from the placeholder to the server's collage
- [ ] Pixel 9: on a playlist, drag a track by its handle up and down (also past the screen edge) -> ticks on pick-up and each swap, the new order stays after leaving and reopening, and Jellyfin web shows the same order
- [ ] Pixel 9: long press a track -> selection mode with the floating toolbar; select 2 more -> Remove -> the 3 tracks disappear at once and stay gone after reopening; the Library playlist row shows the new count
- [ ] Pixel 9: turn on airplane mode, remove a track from a playlist -> it disappears, then comes back with the "could not be saved" snackbar
- [ ] Pixel 9: playlist menu -> Rename (new name shows, also in Library search), then Delete -> confirm -> the screen closes, the playlist is gone from Library and Jellyfin web; the tracks are still in the library
- [ ] Pixel 9: TalkBack on a playlist row offers Move up / Move down; the toolbar buttons read "Clear selection" and "Remove from playlist"

## #17 Search: diacritic-insensitive local search
- [ ] Pixel 9: Search tab on the real library -> type "mosoi" letter by letter -> results update while typing with no visible lag (~100 ms) and "Moșoi" appears; Artists / Albums / Tracks / Playlists groups in that order
- [ ] Pixel 9: open a result, then come back -> the query stays; clear the field -> recent searches show; Clear -> the list is empty and stays empty after restarting the app
- [ ] Pixel 9: motion feels right (keyboard and results, no jank while typing) and there are no haptics on plain taps; TalkBack reads the clear-text button label

## #20 Downloads and offline mode
- [ ] Pixel 9: on Wi-Fi, album menu -> Download -> the Downloads tab shows the album's tracks with progress, then done, and the storage line grows; turn on airplane mode -> Home/Library/Search show "You are offline"; Downloads -> tap the first track -> the whole downloaded album plays through, seeking works, no errors
- [ ] Pixel 9: in airplane mode, play a track that is not downloaded (e.g. from the restored queue) -> it fails at once with no long spinner, and the downloaded tracks still play
- [ ] Pixel 9: on mobile data only (Wi-Fi off), download a track -> the row and the notification say "Waiting for Wi-Fi"; turn Wi-Fi on -> the download starts by itself
- [ ] Pixel 9: start downloading a large album, reboot the phone mid-way, open the app -> the unfinished downloads continue (finished ones stay done)
- [ ] Pixel 9: Downloads -> row menu -> Remove download -> the row goes away and the storage line shrinks; Jellyfin web still has the track (R5)
- [ ] Pixel 9: play a downloaded album while online -> Jellyfin's dashboard shows the session as Direct Play and the play count goes up

## #21 Audio quality: offload, gapless, album normalization, prefetch and 2 GB cache
- [ ] Pixel 9: on Wi-Fi play a gapless FLAC album (e.g. a live album) with the screen off for 15+ minutes -> no gaps or clicks between tracks; `adb shell dumpsys media.audio_flinger` shows an offloaded output while playing; battery use for the session is low (Settings -> Battery). Repeat with an MP3/AAC album and note whether it offloads (it may not, by design)
- [ ] Pixel 9: queue a quiet album, then a loud one, screen off -> both play at a similar loudness; note whether the first second of the loud album is louder (accepted lag with offload)
- [ ] Pixel 9: play a few tracks on Wi-Fi, then turn on airplane mode and play them again -> they play from the start to the end with no error; tracks never played fail at once
- [ ] Pixel 9: on Wi-Fi start an album and wait ~1 minute -> the next track starts instantly; Jellyfin's dashboard shows the sessions as Direct Play

## #53 Playback does not start: Play, Shuffle and track taps do nothing
- [x] Pixel 9: album -> Play, Shuffle, tap a track, mini player play/pause and next -> playback starts and the buttons work - passed 2026-10-25
- [ ] Pixel 9: while playing, the notification and the lock screen play/pause, next and previous work; a Bluetooth headset play/pause works
