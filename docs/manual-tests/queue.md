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

