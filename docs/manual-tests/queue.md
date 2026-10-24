# Manual test queue

Items are added by `/end` and run with `/test`.

## #1 Project skeleton: Gradle, version catalog, convention plugins, empty app
- [ ] Pixel 9: `.\gradlew.bat installDebug`, open mediatheQ from the launcher -> app opens and shows "mediatheQ" centered, no crash
- [ ] MacBook Air M1: fresh clone, `./gradlew assembleDebug` -> BUILD SUCCESSFUL

## #8 ADR: role of GitHub Copilot and copilot-instructions.md
- [ ] GitHub: open the next PR -> a Copilot code review appears on it (needs automatic Copilot review enabled in the repo settings)

## #4 Design system foundation: MediatheqTheme, Iris colors, type, shapes, spacing, motion, screenshot setup
- [ ] Pixel 9: install, open the app, Home -> "Open theme showcase", scroll through colors, type and shapes in light mode -> readable, Iris violet, no clipped text
- [ ] Pixel 9: switch the system to dark mode, reopen -> dark Iris scheme, readable

## #6 Navigation shell: Navigation 3, four tabs with own back stacks, mini player slot
- [ ] Pixel 9: Library -> Open detail -> Open next; switch to Search and back to Library -> still on "Library 2"
- [ ] Pixel 9: in "Library 2", swipe back slowly from the screen edge -> the predictive back preview shows "Library 1", releasing goes there; at the Library root, back returns to Home; at the Home root, back closes the app

## #7 Connect to Jellyfin and log in (password + Quick Connect)
- [ ] Pixel 9 on home Wi-Fi: fresh install, enter `192.168.178.34:8096` -> "Sign in to trueNAS"; wrong password -> "Wrong username or password."; right password -> the tab shell opens
- [x] Pixel 9 on mobile data with Tailscale on: fresh install, enter the server's Tailscale address (100.x or MagicDNS name) with port 8096 -> sign-in works over http - passed 2026-10-19
- [ ] Pixel 9: Sign in -> "Use Quick Connect" -> approve the code from the Jellyfin web UI (Settings -> Quick Connect) -> the tab shell opens within ~5 s
- [ ] Pixel 9: Home -> "Log out" -> back at "Welcome to mediatheQ"; Jellyfin dashboard -> Devices no longer shows an active session for the phone
- [ ] Pixel 9: sign in, force-stop the app, reopen -> goes straight to the tab shell (token read back and decrypted)
- [ ] Pixel 9: `.\gradlew.bat :core:datastore:connectedDebugAndroidTest` -> AndroidKeystoreTokenCipherTest passes (Keystore round trip, fresh IV)
- [ ] Pixel 9: on the server screen enter `http://example.com` -> "Use https:// for servers outside your home network or Tailscale."

## #9 Library sync: Room 3 database, full sync from Jellyfin, FTS5 search index
- [ ] Pixel 9 on home Wi-Fi: fresh install, sign in -> the first sync runs by itself; measure the time from sign-in until it finishes (JVM check on the dev PC: 5.8 s) and note it in issue #9
- [ ] Pixel 9: after the sync, Library counts match the server: 455 album artists, 989 albums, 2729 tracks, 11 playlists (642 entries). Until #10 there is no Library screen: check with App Inspection -> Database Inspector on `mediatheq.db`
- [ ] Pixel 9: start a fresh sync, swipe the app away mid-sync, reopen within an hour -> the sync continues and ends with the same counts
- [ ] Pixel 9: Home -> Log out -> Database Inspector shows every table empty

## #10 Library screen: albums grid, artists, playlists, songs
- [ ] Pixel 9, **release build** (`.\gradlew.bat installRelease` or a signed R8 build), synced library (989 albums): Library -> Albums, fling the grid top to bottom and back with 2, 3 and 4 columns -> no visible stutter; covers fade in from their BlurHash
- [ ] Pixel 9: the Library title sits right under the status bar (no double gap), the last row is not hidden behind the navigation bar
- [ ] Pixel 9: change columns to 4 and sort Albums by Year, switch to Songs and back, force-stop and reopen -> 4 columns and Year are kept, the scroll position per tab is kept while the app runs
- [ ] Pixel 9: view menu -> Connected buttons -> the filters become connected buttons; reopen the app -> still connected buttons
- [ ] Pixel 9: pull down on Albums -> the refresh spinner shows while the sync runs, then hides
- [ ] Pixel 9: Artists tab lists only album artists (455 on the test server)

## #11 Album screen: header, track list, play and shuffle
- [ ] Pixel 9: Library -> tap an album -> the cover flies from the grid into the album header and back on Back; it feels smooth (corners go from rounded to more rounded during the flight; a BlurHash flash is acceptable while the larger cover loads)
- [ ] Pixel 9: force-stop the app, reopen, open an album at once (cold database) -> the cover still flies; if it jumps or only fades, note it in issue #11
- [ ] Pixel 9: on an album page, start a predictive back gesture halfway and cancel -> the page stays, no stuck cover; complete it -> the cover flies back into the grid
- [ ] Pixel 9: a multi-disc album shows "Disc 1", "Disc 2" headers; scrolling collapses the large title into the top bar
- [ ] Pixel 9 (after #12): Play starts track 1, tapping a track starts there, Shuffle does not always start at track 1

## #12 Playback core: MediaLibraryService, PlaybackConnection, stream selection, reporting
- [x] Pixel 9 on home Wi-Fi: open a FLAC album -> Play -> music plays; Jellyfin dashboard -> Activity/Sessions shows "Direct playing" for the phone - passed 2026-10-19
- [x] Pixel 9 on mobile data (Tailscale on): play a FLAC track -> Jellyfin shows "Transcoding" to Opus ~256 kbps; `adb shell dumpsys connectivity | findstr METERED` shows the active network is metered (if Tailscale makes it look unmetered, note it in issue #12) - passed 2026-10-19
- [x] Pixel 9 on mobile data: Opus/Ogg plays without errors (ADR 0016 device check); seeking is not possible yet (expected) - passed 2026-10-19
- [x] Pixel 9: notification shows the track with cover; play/pause, next and previous work from the notification - passed 2026-10-19
- [ ] Pixel 9: the same controls work from the lock screen (owner: run once the in-app player from #13 exists)
- [ ] Pixel 9: unplug wired headphones (or disconnect Bluetooth) while playing -> playback pauses; a phone call -> music pauses and resumes after
- [x] Pixel 9: tapping the notification opens the app - passed 2026-10-19
- [ ] Pixel 9: Jellyfin dashboard shows the playing track, its progress moving, and it disappears when playback stops (Stopped reported, transcode ended)
- [ ] Pixel 9: `.\gradlew.bat :app:connectedDebugAndroidTest` -> PlaybackServiceTest passes (service starts, controller connects)
- [ ] Pixel 9: Home -> Log out while music plays -> playback stops


## #13 Mini player and Now Playing with cover colors
- [ ] Pixel 9: play an album -> the mini player slides in above the nav bar with cover, title, artist; play/pause and next work from it; the list above is not hidden behind it
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

## #18 Favorites, ratings and live updates via WebSocket
- [ ] Pixel 9: app open on an album page -> in Jellyfin web mark that album and one of its tracks as favorite -> within a few seconds the album heart fills and the track menu says "Remove from favorites"
- [ ] Pixel 9: play a track, tap the heart and give it 4 stars in Now Playing, let it play to the end (or skip) -> in Jellyfin web (or `GET /UserItems/{id}/UserData`) the track is still a favorite with Rating 8 (R2)
- [ ] Pixel 9 + server: rate a track 3 stars, then tap the 3rd star again -> the stars clear in the app; the server's user data shows Rating 0 or none, and Jellyfin web shows it unrated (R14 confirmation)
- [ ] Pixel 9: airplane mode, tap the heart on an album -> it fills, then goes back with "Could not save that change"; same with Now Playing open (message shows over the player)
- [ ] Pixel 9: app open, restart the Jellyfin server -> after it is back, a favorite set in Jellyfin web still reaches the app within a few seconds (reconnect)
- [ ] Pixel 9: press Home with nothing playing, wait 1 min, change a favorite in Jellyfin web -> it is not live (socket closed); open the app again -> socket reconnects; with music playing in the background the change shows live
- [ ] Pixel 9 + server: add an album in Jellyfin (library scan) while the app is open -> it appears in Library within about 30 s without pull-to-refresh
- [ ] Pixel 9: heart toggle gives a toggle haptic and morphs round -> squarish; stars give no haptic; TalkBack reads "Favorite, on/off", "Rate 3 stars", "Clear rating" and the rating state "3 of 5 stars"
