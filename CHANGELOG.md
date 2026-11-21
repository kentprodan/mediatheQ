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
- Sign in on the TV: enter the server address, then approve a Quick Connect code from your phone, or use your user name and password.
- The TV app's frame: a side menu (Home, Library, Search, Now playing, Settings) that opens when you move into it, with what is playing at its foot.
- Home on the TV: the same sections as on the phone, as rows of covers; select opens an album, artist or playlist or plays a track or mix, and a long press (or the menu key) offers Play, Shuffle and Add to queue.
- The Library on the TV: albums, artists, playlists and genres as grids of covers, sorted as you choose, with an A to Z strip to jump through long lists, and the albums of a genre on its own page.
- Album and playlist pages on the TV: the cover, the quality (Hi-Res, Dolby Atmos), Play, Resume, Shuffle and Favorite, and the tracks by disc; a long press on a track offers Play next, Add to queue and Favorite.
- The artist page on the TV: their picture over the backdrop, the biography (select to read it all), Play all and Shuffle all, their albums, the albums they appear on and their most played tracks.
- Now Playing on the TV: the cover, what plays and its quality, seeking with Left and Right, the controls and the queue beside it; the launcher's Now Playing card opens it, and the side menu shows the cover of what plays.
- On the TV, Dolby Atmos music goes untouched over HDMI to a soundbar or receiver that takes it, with nothing in between that would change the sound.
- On the TV, Dolby Atmos music plays in its original quality over HDMI even with a lower streaming quality set, and the Dolby Atmos badge shows only while Atmos really reaches the soundbar.
- Now Playing says when a Dolby Atmos track is decoded on the device or plays in stereo on the current output.
- Settings on the TV: Dolby Atmos passthrough for the HDMI output, streaming quality, Sync now with its progress, the account with Sign out, the version and the open-source licenses.
- Search on the TV: results in rows (albums, artists, songs, playlists) while you type, diacritics optional, and your recent searches.
- Lyrics on the TV's Now Playing: the line being sung large in the queue's place, or the whole text for plain lyrics.
- Movies and Series in the Library: after a sync they show as poster grids with how many there are, sorted by name, date added or year, with an Unwatched filter. Playing them comes in a later update.
- Download a movie, an episode or a whole season (as the original file, with its subtitle files) and watch it offline; the Downloads tab lists Movies and Seasons.
- Follow a series (its page: Keep the next 3 episodes downloaded): the next unwatched episodes download by themselves, and the ones you watched make room for the next.
- Several addresses for one server (Settings -> Manage media providers -> Edit address -> Other addresses): at home the app uses the LAN address, away the Tailscale one, by itself.
- Lyrics from LRCLIB (Settings -> Playback -> Playback, off by default): synced lyrics for tracks your server has none for.
- More like this at the bottom of an album page: albums from your library the server finds similar.
- Start radio from a track, album, artist, genre or playlist menu (and Now Playing): the server mixes similar music from your library into a new queue.
- Sleep timer on Now Playing (the moon button): stop after this track or a few tracks, after 5 to 60 minutes, or at a time you pick; the last choice is one tap away. On the TV too, from the moon button of Now Playing.
- Movies and Series can be tabs of their own (Settings -> Navigation), each grid keeps its sort, and Search finds movies, series and episodes.
- Picture-in-picture: swipe home while a video plays and it goes on in a small window with play / pause and next episode; Back keeps the sound going when you ask for it.
- Movies and episodes: chapter marks and a chapters list, a preview picture while you drag the seek bar, and Skip intro / Skip credits (or skipping by itself) from the server's Intro Skipper segments.
- Settings -> Playback -> Video: quality on Wi-Fi and mobile data, default audio and subtitle language, burned-in styled subtitles, auto-play of the next episode; HDR10, HLG, HDR10+ and Dolby Vision play as they are when the phone can show them, and the player says what plays (tap the title).
- Continue watching and Next up on Home, plus recently added movies and episodes; an episode goes on to the next one after a 10-second countdown you can cancel.
- Audio and subtitles for movies and episodes: pick them in the player, on the movie page or from an episode's menu; the choice is remembered for that video or for its whole season, and subtitles follow your system caption style.
- Movie and series pages: the backdrop, poster, facts and format (4K, HDR, 5.1), Resume where you left off, Favorite and Watched; a series shows Next up and its seasons as tabs with each episode's progress.
- Watch movies: Play or Resume on a movie opens it full screen with play and pause, the seek bar and 10-second jumps; Back pauses it and the mini player brings it back. The music queue comes back paused when the movie ends.
- App mode: choose Music, Video or Both after signing in, at the top of Home or in Settings -> Interface -> App mode. Video hides the music, Music stays as before, and the sync only fetches what the mode shows.
- Movies and Series on the TV: poster grids in the side menu, sorted by name, date added, year, runtime or at random, filtered by watched, favorites, genre or decade, with an A to Z strip; Settings -> Show chooses music, video or both.
- Movie and series pages on the TV: the backdrop, facts and format, Play or Resume, Favorite and Watched, audio and subtitles kept per movie; series with Next up, season tabs, episodes and watched for a season or the whole series; and the TV video player with fast seeking, a bar that hides itself, the next episode countdown and the screen kept on while playing.
- Remote control: Jellyfin web (and soon the phone) can pause, skip and seek mediatheQ on the TV and the phone, and send a movie to play from where you were; the TV opens its player on it.
- Playlists edited without a connection keep your changes and send them when the server is back; if the playlist also changed on the server meanwhile, you choose Merge, Replace or Skip.

### Changed
- The TV side menu: Settings -> Display -> Menu style chooses Modal (over the dimmed screen) or Standard (pushes the screen aside); your profile sits at the top (Select switches profile), and Library is now called Music.
- While an output's equalizer is off, its Preset row says why the preset and bands are grey.
- Moving between screens now slides in the direction you go (forward or back) instead of a plain fade; the back gesture shrinks the screen as you drag.
- Optimized builds: the app is about a fifth of the size and smoother; install the `daily` build for everyday use.

### Fixed
- Now Playing on a phone held sideways shows the controls without scrolling (smaller transport buttons, the album line in the menu).
- The waveform analysis no longer keeps the processor busy or stops making waveforms when one file never finishes decoding.
- Typing in search in list (Library) and in the Downloads filter no longer moves the cursor back.
- Bit-perfect USB turned on while a track plays takes effect at once, not only from the next track.
- A tap right after going back no longer reaches the page you are leaving: the Settings gear on Home no longer opens that page's menu (#189).
