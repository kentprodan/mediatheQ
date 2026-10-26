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
- [x] Pixel 9: Home -> Log out -> Database Inspector shows every table empty - passed 2026-10-26 (via #51)

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
- [x] Pixel 9: the same controls work from the lock screen (owner: run once the in-app player from #13 exists) - passed 2026-10-25
- [x] Pixel 9: unplug wired headphones (or disconnect Bluetooth) while playing -> playback pauses; a phone call -> music pauses and resumes after - passed 2026-10-25 (Bluetooth)
- [x] Pixel 9: tapping the notification opens the app - passed 2026-10-19
- [x] Pixel 9: Jellyfin dashboard shows the playing track, its progress moving, and it disappears when playback stops (Stopped reported, transcode ended) - passed 2026-10-25
- [x] Pixel 9: `.\gradlew.bat :app:connectedDebugAndroidTest` -> PlaybackServiceTest passes (service starts, controller connects) - passed 2026-10-25
- [ ] Pixel 9: Home -> Log out while music plays -> playback stops


## #13 Mini player and Now Playing with cover colors
- [x] Pixel 9: play an album -> the mini player slides in above the nav bar with cover, title, artist; play/pause and next work from it; the list above is not hidden behind it - passed 2026-10-25
- [x] Pixel 9: tap the mini player, and separately swipe it up -> Now Playing opens, the cover flies from the pill into the large cover; swipe down, the close button and Back each return to the mini player with the cover flying back - passed 2026-10-25
- [x] Pixel 9: Now Playing colors follow the cover (try 3 albums with very different covers); changing track to another album blends the colors, no snap; an album without cover stays Iris - passed 2026-10-25
- [x] Pixel 9 on Wi-Fi: the seek bar moves smoothly, dragging it seeks; on mobile data (transcode) it is disabled - passed 2026-10-25 (Wi-Fi part)
- [x] Pixel 9: on the album page the playing track shows the equalizer icon and a colored title; it moves to the next track when the player advances - passed 2026-10-25
- [x] Pixel 9: motion feels right (no jank opening/closing Now Playing, mini player enter/exit) and there are no haptics on plain taps - passed 2026-10-25
- [x] Pixel 9: TalkBack on the album page reads "Now playing" on the playing track; the mini player announces "Open player" - passed 2026-10-25

## #14 Queue: view, reorder, remove, persistence and restore
- [x] Pixel 9: play an album, seek into track 3, then reboot the phone -> open the app: the mini player shows track 3 paused; Play continues at about the same position; the queue screen lists the whole album - passed 2026-10-25
- [x] Pixel 9: play an album, wait 15 s, `adb shell am force-stop io.github.kentprodan.mediatheq` -> reopen: same queue, same track, paused, position at most ~10 s behind - passed 2026-10-25
- [x] Pixel 9: Now Playing -> queue button -> drag a track by its handle up and down (also past the screen edge so the list scrolls) -> a light tick on pick-up and each swap, the order changes and the player plays in the new order; no jumps or flicker - passed 2026-10-25
- [x] Pixel 9: swipe a queue entry left and right -> it is removed; removing the playing entry continues with the next; Back closes the queue, a second Back closes the player - passed 2026-10-25
- [x] Pixel 9: album track menu -> Play next / Add to queue, album top bar menu -> Play next (whole album), Songs tab menu -> Add to queue -> the queue screen shows them in the right places; with nothing playing, Play next starts playback - passed 2026-10-25
- [x] Pixel 9: album started with Shuffle -> the queue shows the shuffled order without drag handles; Play next on a track plays right after the current one - passed 2026-10-25
- [x] Pixel 9: TalkBack on the queue: rows read "Now playing" for the current one and offer Move up / Move down / Remove from queue actions - passed 2026-10-25

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
- [x] Pixel 9: app open on an album page -> in Jellyfin web mark that album and one of its tracks as favorite -> within a few seconds the album heart fills and the track menu says "Remove from favorites" - passed 2026-10-25
- [x] Pixel 9: play a track, tap the heart and give it 4 stars in Now Playing, let it play to the end (or skip) -> in Jellyfin web (or `GET /UserItems/{id}/UserData`) the track is still a favorite with Rating 8 (R2) - passed 2026-10-25
- [x] Pixel 9 + server: rate a track 3 stars, then tap the 3rd star again -> the stars clear in the app; the server's user data shows Rating 0 or none, and Jellyfin web shows it unrated (R14 confirmation) (rating checked indirectly: Jellyfin web does not show user ratings) - passed 2026-10-26
- [x] Pixel 9: airplane mode, tap the heart on an album -> it fills, then goes back with "Could not save that change"; same with Now Playing open (message shows over the player) - passed 2026-10-25 (owner wants offline changes kept, see #56)
- [x] Pixel 9: app open, restart the Jellyfin server -> after it is back, a favorite set in Jellyfin web still reaches the app within a few seconds (reconnect) - passed 2026-10-25
- [x] Pixel 9: press Home with nothing playing, wait 1 min, change a favorite in Jellyfin web -> it is not live (socket closed); open the app again -> socket reconnects; with music playing in the background the change shows live - passed 2026-10-25
- [x] Pixel 9 + server: add an album in Jellyfin (library scan) while the app is open -> it appears in Library within about 30 s without pull-to-refresh - passed 2026-10-25
- [x] Pixel 9: heart toggle gives a toggle haptic and morphs round -> squarish; stars give no haptic; TalkBack reads "Favorite, on/off", "Rate 3 stars", "Clear rating" and the rating state "3 of 5 stars" - passed 2026-10-25
## #20 Downloads and offline mode
- [x] Pixel 9: on Wi-Fi, album menu -> Download -> the Downloads tab shows the album's tracks with progress, then done, and the storage line grows; turn on airplane mode -> Home/Library/Search show "You are offline"; Downloads -> tap the first track -> the whole downloaded album plays through, seeking works, no errors - passed 2026-10-25 (after #58)
- [x] Pixel 9: in airplane mode, play a track that is not downloaded (e.g. from the restored queue) -> it fails at once with no long spinner, and the downloaded tracks still play - passed 2026-10-25 (fails at once; no message shown, see #61)
- [x] Pixel 9: on mobile data only (Wi-Fi off), download a track -> the row and the notification say "Waiting for Wi-Fi"; turn Wi-Fi on -> the download starts by itself - passed 2026-10-25
- [ ] Pixel 9: start downloading a large album, reboot the phone mid-way, open the app -> the unfinished downloads continue (finished ones stay done)
- [x] Pixel 9: Downloads -> row menu -> Remove download -> the row goes away and the storage line shrinks; Jellyfin web still has the track (R5) - passed 2026-10-25
- [x] Pixel 9: play a downloaded album while online -> Jellyfin's dashboard shows the session as Direct Play and the play count goes up - passed 2026-10-25 (Direct Play; play count not checked)

## #21 Audio quality: offload, gapless, album normalization, prefetch and 2 GB cache
- [ ] Pixel 9: on Wi-Fi play a gapless FLAC album (e.g. a live album) with the screen off for 15+ minutes -> no gaps or clicks between tracks; `adb shell dumpsys media.audio_flinger` shows an offloaded output while playing; battery use for the session is low (Settings -> Battery). Repeat with an MP3/AAC album and note whether it offloads (it may not, by design)
- [ ] Pixel 9: queue a quiet album, then a loud one, screen off -> both play at a similar loudness; note whether the first second of the loud album is louder (accepted lag with offload)
- [x] Pixel 9: play a few tracks on Wi-Fi, then turn on airplane mode and play them again -> they play from the start to the end with no error; tracks never played fail at once - passed 2026-10-25
- [x] Pixel 9: on Wi-Fi start an album and wait ~1 minute -> the next track starts instantly; Jellyfin's dashboard shows the sessions as Direct Play - passed 2026-10-25

## #53 Playback does not start: Play, Shuffle and track taps do nothing
- [x] Pixel 9: album -> Play, Shuffle, tap a track, mini player play/pause and next -> playback starts and the buttons work - passed 2026-10-25
- [x] Pixel 9: while playing, the notification and the lock screen play/pause, next and previous work; a Bluetooth headset play/pause works - passed 2026-10-25

## #58 Android 17 blocks all LAN connections: ACCESS_LOCAL_NETWORK is not declared or requested
- [x] Pixel 9 (Android 17): open the app without the permission -> "Allow access to your home network" -> Allow -> system dialog -> Allow -> the app opens and reaches the LAN server (album downloads complete) - passed 2026-10-25
- [x] Pixel 9: Settings -> Apps -> mediatheQ -> Permissions -> Nearby devices -> Don't allow, back to the app -> the gate shows again; deny twice -> the button says "Open settings" and opens the app settings; allow there -> back in the app it opens - passed 2026-10-25
- [ ] Pixel 9 on mobile data with Tailscale on: sign-in, browse and playback over the Tailscale address work with the permission granted (and note whether they also work with it denied)

## #19 Home screen
- [ ] Pixel 9: open Home after a sync -> Shuffle all, Recently played, Recently added, Favorite albums and Favorite songs show (empty ones hidden); tap an album that is in two rows -> one cover flies into the album page, no flicker or missing cover; back -> Home keeps its scroll; Shuffle all starts a shuffled queue; a favorite song plays the favorites from it

## #22 USB DAC bit-perfect and equalizer
- [ ] Pixel 9: Now Playing -> Audio (tune button) -> turn the Equalizer on, pick Bass boost, then move the 60 Hz band -> the sound changes at once and the preset becomes Custom; `adb shell dumpsys media.audio_flinger` shows no offloaded output while the EQ is on (and offload again after turning it off); light/dark and font size 200% look right
- [ ] Pixel 9 without a DAC, Bit-perfect USB on -> playback is normal, no indicator on Now Playing, the EQ stays available
- [ ] Pixel 9 + USB DAC (pending: no DAC yet), Bit-perfect USB on: play 44.1, 48 and 96 kHz files -> the DAC shows the file's rate each time; Now Playing shows "Bit-perfect · <rate>" and "Phone volume does not apply"; the Audio screen says the EQ is unavailable and why
- [ ] Pixel 9 + USB DAC (pending): a gapless album that switches 44.1 -> 96 kHz -> the DAC follows the rate on the second track (if not: the mixer attribute is set too late, see media3 skill section 6)
- [ ] Pixel 9 + USB DAC (pending): unplug the DAC mid-track -> playback pauses (becoming noisy), the indicator goes away; play again on the speaker -> normal volume and EQ work; phone volume keys do nothing while bit-perfect

## #23 Lyrics: synced and plain
- [ ] Pixel 9: play a track with a `.lrc` file -> Now Playing -> lyrics button -> the sung line is marked and stays centered, in time with the voice by ear (note whether the LRC `[offset:]` tag is applied; if not, new R-line in the jellyfin skill); tap a later line -> playback jumps there; scroll by hand -> the list stays put ~3 s, then returns to the sung line; back -> the player
- [ ] Pixel 9: a track with plain (`.txt`) lyrics -> lines show and scroll, none marked, taps do nothing; a track without lyrics -> "No lyrics"; airplane mode on a streamed track -> "Couldn't load lyrics" + Try again; light/dark and font size 200% look right

## #24 Dolby Atmos on phone: detection, unavailable state, stereo fallback
- [ ] Pixel 9: play an Atmos M4A (E-AC-3 JOC) track on Wi-Fi -> it plays (stereo, no error, no silence); Now Playing shows the Dolby mark with "Dolby Atmos not available" / "Playing in stereo on this output"; Jellyfin's dashboard shows the session as Transcode; the next non-Atmos track hides the note
- [ ] Pixel 9: album and search lists show the format badges (Dolby mark on Atmos tracks, two dots on stereo, five dots on multichannel); they read right in light/dark and at font size 200%; TalkBack reads "Dolby Atmos" / "Stereo" with the row

## #25 Settings screen
- [ ] Pixel 9: Home -> gear -> Settings: every group shows (Account, Playback, Downloads and storage, Appearance, Ratings, About), light/dark and font size 200% read right; Audio opens bit-perfect and the equalizer; Theme showcase opens; back returns; Now Playing has no Tune button any more
- [ ] Pixel 9 on mobile data: Quality on mobile data 128 kbps, play a FLAC album -> Jellyfin's dashboard shows a 128 kbps Opus transcode; back on Wi-Fi with Original -> the next track direct plays
- [ ] Pixel 9: turn Download on Wi-Fi only off, on mobile data download an album -> it downloads (on: it says Waiting for Wi-Fi)
- [ ] Pixel 9: Material You on -> colors follow the wallpaper at once, and after a cold start with no Iris flash; off -> Iris
- [x] Pixel 9: Ratings -> Half stars: on Now Playing tap a star (whole), again (half), again (cleared); the rating shows the same in Jellyfin's web; Off hides the stars on Now Playing, album and artist pages (rating checked indirectly: Jellyfin web does not show user ratings) - passed 2026-10-26
- [ ] Pixel 9: Volume normalization Off -> a quiet and a loud album play at their own loudness; Album -> evened out, without restarting playback

## #38 Seekable streams on metered networks (HLS transcode)
- [ ] Pixel 9 on mobile data (quality 256 kbps): play a FLAC album, drag the seek bar to the middle of a track -> playback jumps there within about a second and carries on; the seek bar is enabled; Jellyfin's dashboard shows a Transcode session
- [ ] Pixel 9 on Wi-Fi with quality 128 kbps: the same seek works; with Original a FLAC track direct plays as before (seek works, cached replay without network)
- [ ] Pixel 9: pause a converted track for 3+ minutes, then play -> it resumes (or restarts the track cleanly), no endless buffering
- [ ] Pixel 9: repeat one on a converted track -> it replays from the start; turning Wi-Fi off/on mid-track keeps playing
- [ ] Pixel 9: an Atmos M4A on Wi-Fi Original (converted to stereo) can be seeked

## #41 Shuffle and repeat toggles on Now Playing
- [x] Pixel 9: on Now Playing tap Shuffle on and off, then Repeat three times (off -> all -> one -> off) -> a subtle haptic tick on each tap; none with Settings -> Sound & vibration -> Vibration & haptics -> Touch feedback off - passed 2026-10-26
- [x] Pixel 9: play an album from track 4, turn Shuffle on -> track 4 keeps playing, Next goes through every other track once, the queue screen shows track 4 first - passed 2026-10-26
- [x] Pixel 9: set Repeat one, force-stop the app, reopen -> the repeat button still shows repeat one - passed 2026-10-26

## #43 Playback resumption from the system (media card, headphone Play)
- [x] Pixel 9: play something, pause, reboot the phone -> after unlock the media card shows the last track (with its cover when the server was reachable) -> Play on the card resumes it at the saved position - passed 2026-10-26
- [x] Pixel 9 + Bluetooth headphones: playback paused, app swiped away from Recents -> Play on the headphones resumes the last queue at the saved track and position - passed 2026-10-26
- [x] Pixel 9 + Bluetooth headphones: log out, swipe the app away, press Play on the headphones -> nothing starts and the app does not crash - passed 2026-10-26

## #51 Log out leaves sync staging rows and the sync checkpoint in the database
- [x] Pixel 9: fresh sign-in on home Wi-Fi, let the first sync start, force-stop and reopen (sync resumes), Home -> Log out -> `adb exec-out run-as io.github.kentprodan.mediatheq cat databases/mediatheq.db` (+ `-wal`, `-shm`): every table empty, including the `*_staging` tables and `sync_state` - passed 2026-10-26
- [x] Pixel 9: during a sync, Log out and sign in again at once as the same user -> the library fills completely (album and track counts match the server), no partial library - passed 2026-10-26

## #52 Library filters flash as chips on cold start when Connected buttons is chosen
- [x] Pixel 9: Library -> view menu -> Connected buttons; close the app from Recents, reopen, go to Library -> the filters show as connected buttons from the first frame, no chip row flashes (the bar may appear together with the list) - passed 2026-10-26

## #56 Keep favorite and rating changes made offline and send them when back online
- [x] Pixel 9: airplane mode on, favorite an album and rate a track (no error message, both stay); turn the network back on with the app in the foreground -> Jellyfin web shows both within a few seconds (a downloaded track instead of an album: offline only downloads show) (rating checked indirectly: Jellyfin web does not show user ratings) - passed 2026-10-26
- [x] Pixel 9: airplane mode on, favorite a track, force-stop the app, reopen it (heart still filled), network back on -> Jellyfin web shows the favorite - passed 2026-10-26
- [x] Pixel 9: airplane mode on, favorite a track, Home -> Log out, sign in again with network -> the track is not a favorite in the app or in Jellyfin web (logged out on mobile data with Tailscale off: offline the settings cannot be reached, see #80) - passed 2026-10-26

## #80 Offline, Settings and Log out cannot be reached
- [ ] Pixel 9: signed in with the library synced, airplane mode on, open the app -> Home shows "You are offline" with a gear in the top bar; gear -> Settings opens (and Audio inside it); Back -> offline state again
- [ ] Pixel 9: still in airplane mode, gear -> Settings -> Log out -> onboarding shows at once; with the network back, sign in -> the library syncs from scratch (nothing from before shows before the sync)
