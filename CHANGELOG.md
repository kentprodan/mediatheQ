# Changelog

What changed in mediatheQ, newest first. The app shows this file in Settings -> About -> Changelog.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Dolby Vision Blu-ray remuxes (profile 7) play as they are, no server conversion: the TV enters Dolby Vision on players that decode profile 8 (the Streamer), and plays them in HDR10 elsewhere. Films with both Dolby Vision and HDR10+ play directly too, without the black screen some players show.
- Crossfade (Settings -> Playback -> Transitions): 1 to 12 seconds between tracks of different albums and in shuffled queues, with fade curves and smart fades that start where a track gets quiet. Albums playing in order stay gapless; no crossfade on bit-perfect or passthrough outputs, and only for tracks downloaded or fully cached.
- Bit-perfect USB: 24-bit files reach the DAC with all 24 bits, and DACs that take only 24 or 32-bit audio now play bit-perfect too. Files above 192 kHz play at half their rate on other outputs, where some phones stall.
- DTS, DTS-HD, TrueHD and Dolby Atmos (E-AC-3) play from the original file on the phone and on the TV without passthrough, decoded on the device by FFmpeg instead of converted by the server. A track the device cannot decode at all is skipped instead of playing silence.
- TV Settings -> Home rows: each row can show 1 to 5 cards across the screen (long press a row).
- TV Settings -> Display -> Logo size: Small, Medium or Large logos as titles on movie, series and artist pages.
- TV: your Jellyfin picture shows in the side menu and on Who's watching? (the initial when you have none).
- The app (phone and TV) in Romanian, following the device language; on the phone it can also be picked for mediatheQ alone in Android Settings -> Apps -> Language.
- TV movie, series, artist, album and playlist pages can take their colors from the poster or cover (Settings -> Display -> Accent from the poster).
- TV movie, series and artist pages can show the title as its Jellyfin logo (Settings -> Display -> Title on pages: Logo, Logo and name, Name only). Logos arrive with the next library sync.
- TV choices show radio buttons, checkboxes and switches (sort, filters, quality, subtitles, audio tracks, zoom), and a choice menu opens on the current choice.
- TV Movies and Series have tabs: All, Collections (movies), Genres, Favorites and Studios or Networks; a genre or studio opens its own grid. Studios arrive with the next library sync.
- TV tabs (Library, Movies, Series) slide with the tab you pick, and each tab keeps the card you were on.
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
- A featured carousel at the top of TV Home (Settings -> Display): as a card or across the whole top, showing new movies, new albums or a Home section you pick, optionally without what you watched; it moves on every 8 seconds and stops while you are on it.
- Any TV Home row can be an immersive list (Settings -> Home rows, long press a row): the focused card's picture and details appear behind the row.

### Changed
- On the TV, a film switches the display to its frame rate before it starts and waits for the TV and the sound system to settle, so the first second of sound is no longer lost and Dolby Atmos is not decoded because the sound started too early. Two films that want the same mode keep it, without a black screen in between.
- Volume normalization now raises quiet tracks too, not only lowers loud ones, with a limiter so nothing clips. A raised track plays without battery offload.
- The player keeps up to two minutes of a film ahead and sizes its buffer to the device's memory, so large 4K remuxes neither stall on a network dip nor run the app out of memory.
- TV Settings, round 3: Home rows live in Settings. Reorder rows picks a row up with Select and moves it with Up and Down. Each row has its own level (Show on Home, Immersive list, Design, Size). Genres turned on join the rows, in any order, with their own design and size. Movies, Series and Music each have a pages level for the title and logo. Home shows can keep Home to movies and series. Who's watching? shows round profile pictures with the name under them.
- TV Settings, round 2: Account comes first, Library is now Server and library, and a new Screens category holds Home (carousel design, sources, rows), Movies, Series and Music, each with its own title and logo size. Menu style goes Floating, then Modal or Standard; every choice says what it does. The last level shows a panel of its own tone, row descriptions are smaller and greyer, and subtitles that follow the TV can open the TV's caption settings.
- TV Settings look like the Google TV system Settings: full screen, categories with round icons, a preview of the next level on the right, Right or Select to go in and Left or Back to come out, and values picked from a list with a radio instead of a dialog.
- TV Home's spacing follows Google's guideline: 28 dp margins at the top and bottom, 16 dp from a row's title to its cards.
- TV Home scrolls as little as it can: the row you move to comes in at the bottom with the next one peeking, and the carousel simply scrolls away. Immersive rows grow when you reach them, with their details above 16:9 pictures.
- TV Home's wide cards show the movie's or series' Jellyfin thumb (the 16:9 picture with its title), else the backdrop. Thumbs arrive with the next library sync.
- TV cards follow Google's spacing: the title sits 8 dp under a poster, Classic and Compact cards have 12 dp padding, wide cards 16 dp beside the picture with two lines of description.
- TV Home scrolls as little as it can: the row you move to comes in at the bottom with the next one peeking, and the carousel simply scrolls away. Immersive rows grow when you reach them, with their details above 16:9 pictures.
- TV Home's wide cards show the movie's or series' Jellyfin thumb (the 16:9 picture with its title), else the backdrop. Thumbs arrive with the next library sync.
- TV cards follow Google's spacing: the title sits 8 dp under a poster, Classic and Compact cards have 12 dp padding, wide cards 16 dp beside the picture with two lines of description.
- TV Home's wide cards show the movie's or series' Jellyfin thumb (the 16:9 picture with its title), else the backdrop. Thumbs arrive with the next library sync.
- TV cards follow Google's spacing: the title sits 8 dp under a poster, Classic and Compact cards have 12 dp padding, wide cards 16 dp beside the picture with two lines of description.
- TV cards follow Google's spacing: the title sits 8 dp under a poster, Classic and Compact cards have 12 dp padding, wide cards 16 dp beside the picture with two lines of description.
- TV logo sizes are one step smaller: Large is the old Medium, Medium the old Small, and Small smaller still.
- TV Movies -> Studios and Series -> Networks show each studio's or network's picture with its logo from Jellyfin, four across.
- TV: after closing the app with Back, the next start asks Who's watching? again; a short trip out with the Home button still does not.
- TV Settings -> Carousel shows: pick several sections at once (checkboxes); by default the carousel mixes new movies, new episodes and Continue watching, each slide saying where it comes from.
- TV Sort and Filter open as a side panel from the right; Genre and Decade take several choices at once (any of them), and changing a filter no longer makes the panel flicker.
- The TV side menu has no Now playing entry any more: the card of what plays, at the bottom of the menu, opens Now Playing.
- The TV side menu: Settings -> Display -> Menu style chooses Modal (over the dimmed screen) or Standard (pushes the screen aside); your profile sits at the top (Select switches profile), and Library is now called Music.
- While an output's equalizer is off, its Preset row says why the preset and bands are grey.
- Moving between screens now slides in the direction you go (forward or back) instead of a plain fade; the back gesture shrinks the screen as you drag.
- Optimized builds: the app is about a fifth of the size and smoother; install the `daily` build for everyday use.

### Fixed
- A device whose battery-saving audio offload fails to start, or resets, now plays on normally without it instead of staying silent or stuttering; a stall is remembered until the next system update.
- TV: Dolby Atmos passthrough survives the HDMI link dropping for a few seconds at playback start, and a soundbar that stays silent after a pause gets its audio back on its own. The passthrough switch now really decodes the audio when it is off.
- TV: the first card of every screen keeps clear of the menu, also when focused; an immersive row shows the focused card's details just above its cards and scrolls into place in one move.
- TV Home's carousel moves on by itself again when an accessibility service (a launcher, a button remapper) is on.
- TV: Back from the open menu goes Home with the menu still on Home, not on the profile or the page.
- TV: the Standard menu style slides the screen back smoothly when the menu closes, without a jump.
- On the TV, holding Left or Right on the progress (music and movies) seeks faster the longer you hold, also with remotes that send a held key as separate presses.
- Now Playing on a phone held sideways shows the controls without scrolling (smaller transport buttons, the album line in the menu).
- The waveform analysis no longer keeps the processor busy or stops making waveforms when one file never finishes decoding.
- TV Home: Left on the carousel goes back a slide; only the first slide lets you into the menu.
- Typing in search in list (Library) and in the Downloads filter no longer moves the cursor back.
- Bit-perfect USB turned on while a track plays takes effect at once, not only from the next track.
- A tap right after going back no longer reaches the page you are leaving: the Settings gear on Home no longer opens that page's menu (#189).
