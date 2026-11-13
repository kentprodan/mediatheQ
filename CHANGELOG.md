# Changelog

What changed in mediatheQ, newest first. The app shows this file in Settings -> About -> Changelog.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Play music from your Jellyfin server: library of albums, artists, genres, record labels, years, playlists and tracks, kept on the phone and synced in the background.
- Home with mixes and sections you arrange, Search with filters, album, artist, genre and playlist pages.
- Now Playing with waveform seek bar, synced lyrics, queue, favorites and ratings; colors from the cover.
- Original quality on Wi-Fi, Opus on mobile data, gapless playback, Dolby Atmos detection, bit-perfect USB DAC output.
- Audio profiles per output (phone, headphones, car, USB DAC) with normalization and equalizer.
- Downloads for offline listening, automatic downloads of favorites, offline mode, and Manage offline files.
- Several server accounts, a sync manager, and backup and restore of your settings.
- Settings for the theme, navigation, lists, pages, playback, headset and notification buttons, fades and automatic actions.
- About page, this changelog, a privacy page and Contact support through GitHub.
- A log of what the app does, kept on the phone, and Export logs in Settings -> About to share it for a bug report.
- Settings for each Home section (the gear in Settings -> Home page): display, rows, how many items, header, centered text, subtitles, a play button on covers, status marks; copy, apply to all, restore.
- Tapping a Home section's title opens its full list, with the same tools as the Library lists.
- Any Library sort can be reversed: tap the arrow next to it, or the chosen sort again in its menu.
- The app has its own icon (a Q made of a record and a play button), also for themed icons, and on the About page.
- A first TV app for Google TV: it installs with its own banner and shows a placeholder screen driven by the remote.

### Fixed
- Now Playing on a phone held sideways shows the controls without scrolling (smaller transport buttons, the album line in the menu).
- The waveform analysis no longer keeps the processor busy or stops making waveforms when one file never finishes decoding.
- Typing in search in list (Library) and in the Downloads filter no longer moves the cursor back.
- Bit-perfect USB turned on while a track plays takes effect at once, not only from the next track.
