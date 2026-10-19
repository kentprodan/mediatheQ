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
- [ ] Pixel 9 on mobile data with Tailscale on: fresh install, enter the server's Tailscale address (100.x or MagicDNS name) with port 8096 -> sign-in works over http
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

