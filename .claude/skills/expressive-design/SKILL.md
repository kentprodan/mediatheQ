---
name: expressive-design
description: Material 3 Expressive design rules for mediatheQ. Use for ANY work that creates or changes UI - theme, colors, shapes, motion, components, screens, navigation shell, mini player, Now Playing, grids, lists, haptics, previews and screenshot tests of UI.
---

# mediatheQ - Expressive design

Project rules for building mediatheQ's phone UI with Material 3 Expressive in Jetpack Compose.
Read fully before touching UI. When this file and generic Compose habits disagree, this file wins.
When this file and the official API disagree, the API wins - then update this file in the same PR.

## 0. Versions and sources of truth

- Library: `androidx.compose.material3:material3` **1.5.x**. Expressive APIs are NOT in stable 1.4.x.
  - As of 2026-10-18 the latest is `1.5.0-beta01` (2026-10-07). Pin it in `libs.versions.toml`.
  - Move to `1.5.0` stable as soon as it ships (record in an ADR).
- Before using any component or parameter you are not sure about:
  1. Check the API reference: https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary
  2. Check release notes for renames: https://developer.android.com/jetpack/androidx/releases/compose-material3
  3. Never invent parameters. If it does not compile against the pinned version, it does not exist.
- Design specs: https://m3.material.io (components, motion, shape, color).
- Old code in the archived project is reference only. Never copy UI code from it.

### Stability map for 1.5 (verify on each bump)

Stable in 1.5 (no opt-in needed):
- `MaterialExpressiveTheme`, `MaterialTheme.motionScheme` / `MotionScheme`
- Expressive buttons, `ButtonGroup`, `SplitButton`, toggle buttons (`ToggleButton`, `FilledTonalToggleButton`, `OutlinedToggleButton`, `ElevatedToggleButton`)
- FAB and FAB Menu, `FloatingToolbar`
- `WavyProgressIndicator` (linear + circular)
- Expressive `ListItem` and segmented list items
- Flexible app bars (`MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar`, `FlexibleBottomAppBar`), `TopAppBarScrollBehavior`
- Expressive menus (`DropdownMenuItem` with shapes, `SelectableDropdownMenuItem`, `CheckableDropdownMenuItem`)
- `SearchBarState` + slot-based `SearchBar`
- Stateful `Slider(state = SliderState(...))`

Still experimental (opt-in required):
- `MaterialShapes` (the 35 expressive shapes) and shape morphing
- `LoadingIndicator`
- Anything else annotated `@ExperimentalMaterial3ExpressiveApi` or `@ExperimentalMaterial3Api`

APIs that changed a lot during 1.5 alphas - always check the current signature:
`Slider` / `RangeSlider`, `BottomSheet` state (`rememberBottomSheetState`), `TopAppBarDefaults.*ScrollBehavior`, carousels, `PolygonShape`.

## 1. Architecture of the design system

- All design code lives in module `:core:designsystem`. Feature modules never import `androidx.compose.material3` theme internals directly for tokens - they use `MediatheqTheme` and `Mq*` components.
- Experimental opt-ins are allowed **only** inside `:core:designsystem`. Wrap the experimental component in an `Mq*` composable so features stay opt-in free. When the API graduates, only the wrapper changes.
- Folder layout inside `:core:designsystem`:
  - `theme/` - `MediatheqTheme`, color schemes, typography, shapes, spacing, motion helpers
  - `component/` - `Mq*` components (one file per component)
  - `icon/` - icon set (Material Symbols, rounded)
  - `preview/` - multipreview annotations and preview fixtures

## 2. Theme

`MediatheqTheme(options: MqThemeOptions = MqThemeOptions(), systemDark = isSystemInDarkTheme())` (#121):

- `MqThemeOptions` (design system types; `:app` maps the stored `ThemePreferences` to them): mode (System, Light,
  Dark, Black, System light/black), color source (Iris, Material You, Custom seed), player colors (Off, Cover, Cover
  vibrant), ignore surfaces, instant colors, font.
- `appColorScheme(...)` picks the scheme (pure, unit-tested); Black = the dark scheme with `background`, `surface`,
  `surfaceDim`, `surfaceContainerLowest` pure black (containers keep their tones).
- A scheme change blends app-wide with the effects spec, unless instant colors is on. Schemes compare by identity:
  `remember` them, or every recomposition restarts the blend.
- `LocalMqTheme` carries the options and the resolved dark/black state for parts with their own colors (the player).

### Color - Iris

- Default palette: **Iris**. Seed `#5B45D6` (light), primary in dark `#A391FF`.
- Generate the full tonal schemes once with Material Theme Builder (seed `#5B45D6`, scheme variant per ADR), export to Kotlin, store as `IrisLightColorScheme` / `IrisDarkColorScheme`. Do not hand-pick individual roles.
- Material You (dynamic color) and a custom seed (`SchemeVibrant`, like Iris) are the other color sources (#121).
- Screens use **color roles only** (`MaterialTheme.colorScheme.primaryContainer`, etc.). No hex values outside `theme/`.
- Contrast: keep text on its matching `on*` role. No custom alpha on text below 0.6.

### Color - player from cover art

- The Now Playing background and the mini player tint come from the current album cover.
- Pipeline (exact libraries in ADR): Coil 3 bitmap -> seed color (Palette or color-utilities) -> full `ColorScheme` from seed -> wrap the player subtree in its own `MaterialTheme(colorScheme = coverScheme)`.
- Cache the scheme per album ID. Never recompute on recomposition.
- Options (#121): Off (app theme), Cover (`SchemeContent`), Cover vibrant (`SchemeVibrant`); "ignore background and
  surfaces" keeps those roles from the app theme (merged after the blend); Black keeps the player black.
- Fallback: Iris scheme when no cover or extraction fails.
- The artist page (and the genre and playlist pages, default off) are the only other places with an image theme (ADR 0029): `MqCoverTheme` around the whole page, seeded by the artist image (else the backdrop), via `imageColorScheme`; its own setting turns it on or off.
- Color changes between tracks animate with the **effects** spec (section 4), never snap.

### Typography

- The system font by default; the user may pick a downloadable Google Font (ADR 0026), built in one place by
  `mediatheqTypography(font)`. Display-only fonts apply to display/headline/title styles. No font files in the APK.
- Use the default M3 type scale. For emphasis (album titles on Now Playing, hero headers) use the **emphasized** text styles if present in the pinned version; otherwise `FontWeight` bump in one central place in `MediatheqTypography`.
- Never set `fontSize` inline in screens. Use `MaterialTheme.typography.*`.

### Shape

- Use `MaterialTheme.shapes` roles. Expressive corners are larger - prefer `large` / `extraLarge` for cards and sheets.
- Settings -> Advanced -> Rounded corner size (#137) scales every role of `mediatheqShapes` (Small 0.5x, Normal = the M3 scale, Large 1.5x), so anything that reads `MaterialTheme.shapes` follows. Full-round shapes (buttons, the mini player pill, chips' full corners) and `MaterialShapes` accents stay as they are. Never add a per-screen corner value.
- Album art: rounded rect from `MaterialTheme.shapes.medium` (grid) and `extraLarge` (Now Playing).
- Mini player: fully rounded pill (`CircleShape` / full corner).
- `MaterialShapes` (cookie, sunny, etc.) only for **accents**: artist avatars, play button morph, empty states. Never for album covers (it crops art). Experimental - only via `Mq*` wrappers.

### Spacing

- `MediatheqSpacing` tokens: `xs 4`, `s 8`, `m 12`, `l 16`, `xl 24`, `xxl 32` (dp). Screen horizontal padding = `l`.
- Fixed sizes (e.g. an album card's width in a row) live in `MediatheqSizes`.
- No raw `.dp` literals in feature modules except `0.dp`.

## 3. Component map for mediatheQ

| UI element | Component | Notes |
|---|---|---|
| Main navigation (#122) | `AppShell` in `:app`, entries and style from Settings -> Navigation and media start (up to 6, Home always; default Home, Library, Search, Downloads). Portrait: `ShortNavigationBar` + `ShortNavigationBarItem` (labels hidden with 5+ entries at fontScale >= 1.5, the icon then carries the name), or `HorizontalFloatingToolbar` of icon tabs (selected = `FilledTonalIconButton`, `Role.Tab` + selected semantics), or the same with the **last** entry as `FloatingToolbarDefaults.StandardFloatingActionButton`. Landscape: collapsed `WideNavigationRail` on the left, or the portrait style. One entry: no navigation. | Albums, Artists, Playlists and Tracks are Library lists opened as navigation entries (no Back), each its own tab. Transparency (0-100 %) changes only the bar / toolbar container color. |
| Screens under the bottom bars (#122) | Screens draw under the mini player and the bar; `LocalBottomBarsPadding` (in `:core:designsystem`) is their height | Scrolling content ends with `PaddingValues.plusBottomBars()`, fixed content uses `Modifier.bottomBarsPadding()`; `MqEmptyState` pads itself. A new screen with a list must do the same. |
| Mini player | `MqMiniPlayer` (custom) | Floating pill above the nav bar, or `docked` full width on the bottom bar (top corners `extraLarge`; only with the Bottom bar style, else floating). `progressStyle` Hidden / Line (3 dp `primary` on the bottom edge) / Background (`secondaryContainer` fill), drawn from `progress` in the draw phase. Swipe to skip = `swipeToChangeTrack` (also TalkBack actions). See section 5. `coverModifier` carries the shared cover. |
| Screen headers (other pages) | `LargeFlexibleTopAppBar` with `exitUntilCollapsedScrollBehavior` | Title = item name, subtitle = artist / count. |
| Album page header (#132) | `AlbumHeader` in `:feature:album` under a `LargeFlexibleTopAppBar` (`exitUntilCollapsedScrollBehavior`; transparent while a cover image or color draws under it; its title fades in over the last 30 % of the collapse) | Background from Settings -> Album page: the cover edge to edge (`RectangleShape`) fading into `surface`, a gradient from the cover's `primaryContainer` (`rememberCoverColors`, whatever the player color option) with the cover (`extraLarge`, 60 % wide), or no background. Title `headlineMediumEmphasized` centered (heading), the edition as the version line (`titleMedium` `onSurfaceVariant`, `albumTitle`), "duration · year · quality" `bodyMedium` (quality: the best track, `albumQuality`), album artists as `TextButton`s, then a `FlowRow` of buttons: Play (`Button`) and Resume (`FilledTonalButton`) with labels, Shuffle / Play next / Add to queue (`FilledTonalIconButton`), `MqFavoriteToggle`, More (`OverflowMenu`: hidden buttons, Add to playlist, Download); stars only when the setting and ratings are on. |
| Album page sections (#132) | Tracks (`TrackRow`: number, thumbnail, multiline title and artists per setting, `showArtists = false` when they are the album's), More from <artist> (`LazyRow` of `AlbumCard`, width `albumCardInRow`, no shared cover), About (the overview `bodyMedium` `onSurfaceVariant`, three lines, a tap expands with the spatial spec; then `ListItem` facts: genres, record labels, release date, date added, play count, file size and format; missing ones hidden) | Order from Settings -> Album page; section titles `titleMedium`, heading. Dates in medium style, read in UTC. |
| Album page settings (#132) | Settings page (`SettingsPage.Album`): groups Header, Content, Tracks; choice dialogs like the Library page, "Hidden buttons" as checkbox rows with OK / Cancel, "Page content order" as an `OrganizeDialog` whose checks are fixed (the switches hide sections) | Then the shared Restore defaults group. |
| Simple screens (Settings hub and its pages, Settings -> Playback -> Output settings) | `TopAppBar` with a back button, in a `Scaffold` (`contentWindowInsets = WindowInsets(0)`) | |
| Album grid | `LazyVerticalGrid(GridCells.Fixed(columns))` | `columns` from DataStore, range 2-4, default 2. |
| Track rows | Expressive `ListItem` | Leading: track number or art. Trailing: duration + overflow menu. |
| Audio format badge (track rows) | `FormatBadgeIcon` in `:core:ui` (icons `MqIcons.Format*`, `MediatheqSizes.formatBadge` 18 dp, content color) | Before the artists (also when the line is replaced, e.g. Downloads): Dolby double-D (Atmos), five dots (surround / DTS:X), two dots (stereo), one dot (mono); none when the channels are unknown. Filled shapes, 20 of 24 wide. Named for TalkBack ("Dolby Atmos", "Surround 5.1", "Stereo"). |
| Grouped settings | `SegmentedListItem` with `ListItemDefaults.segmentedShapes(index, count)`, `SegmentedGap` between rows, one tonal container (`surfaceContainerHigh`, also when checked) | Group title `titleMedium` primary, heading. Choices: `FilterChip`s in a `FlowRow` under the title and summary, selected one with a check. On/off: the checkable overload (whole row toggles) + `Switch(onCheckedChange = null)`. Log out in `error` color. |
| Settings hub (#120) | `SettingsHubScreen` in `:feature:settings`: groups (Settings, Miscellaneous, About) of `SegmentedListItem` rows, leading icon (Material Symbols, `contentDescription = null`), whole row opens the page | Pages share `SettingsScaffold` (top bar with back + `LazyColumn` of groups). An entry is added in the issue that gives its page content; every page later gets "Restore defaults" (#121). "Manage offline files" always shows (#149) and opens Downloads with a back button (also when Downloads is a navigation entry and another tab is open). |
| Theme, font and colors (Settings -> Interface, #121) | Settings page (`SettingsPage.Theme`): chips for mode and color source, custom color = `MqColorSwatch` presets (`MqSeedPresets`) + hue `Slider` (saved on release), read-only swatches of the scheme on screen, choice dialogs (`MqDialog`, one row per option with a check, a tap applies and closes) | Swatches are `MqColorSwatch` (round, outlined, check when selected, radio semantics). |
| Media lists (Settings -> Interface, #123) | Settings page (`SettingsPage.MediaLists`): groups List header, Tracks, Covers and grids; whole-row switches; choice dialogs like the Theme page; "Hidden top buttons" is an `MqDialog` of checkbox rows with OK / Cancel (the checks live in the dialog until OK) | `:app` maps the stored settings to `MediaListStyle` and provides `LocalMediaListStyle`, so every list applies a change at once. |
| Now playing screens (Settings -> Interface, #126) | Settings page (`SettingsPage.NowPlaying`): groups Compact player, Expanded player; switches and choice dialogs like the Media lists page; rows that do not apply are disabled and say why (stars while ratings are off, the fallback while the style is a plain bar) | The settings travel in `NowPlayingUiState.Playing.settings`; a plain bar style reads no waveform. |
| Navigation and media start (Settings -> Interface, #122) | Settings page (`SettingsPage.Navigation`): dialog rows for entries and styles, transparency `Slider` (10 % steps, saved on release), switches, choice dialogs like the Theme page | "Organize your navigation": `MqDialog` (OK / Cancel) with a `LazyColumn` of rows (`Checkbox` + label + drag handle, `rememberMqReorderState`, Move up / Move down actions); Home's checkbox disabled, unchecked rows disabled at 6. The draft lives in the ViewModel. |
| Advanced settings (#137) | Settings page (`SettingsPage.Advanced`, from the hub's Advanced entry and Interface -> Advanced settings): group Display with choice dialogs (Keep screen on, Screen orientation, Rounded corner size) and whole-row switches (Show track number in Now Playing, Hide status bar) | Applied app-wide: orientation and the status bar by `WindowSettings` in `:app`, Keep screen on by `NowPlayingLayout` (`Modifier.keepScreenOn()`, Always or while the full player is open), the corners through `MqThemeOptions.cornerScale`. The track number is "3 / 12" (`labelLarge` `onSurfaceVariant`) above the Now Playing title: the place in the queue in play order. Then the shared Restore defaults group. |
| Playback hub (#138) | Settings page (`SettingsPage.Playback`): group Playback with one `SegmentedListItem` per sub-page (leading icon, summary; an entry comes with its page: Playback, Decoding and transcoding, Output settings, Audio focus, Headset buttons, Notification buttons, Transitions, Automatic actions now); normalization and the equalizer are per output (#140) | Sub-pages: Decoding and transcoding (`SettingsPage.Decoding`: bitrate chips per network, mobile first, Force instant transcoding switch) and Playback (`SettingsPage.PlaybackBehavior`: eight whole-row switches, the last one Bluetooth lyrics, #143), Audio focus (#139: Short interruption choice dialog, Do not resume switch), Headset buttons (#139: five choice dialogs, Slower click detection switch), Notification buttons (#139: previous / next switches, Action 1-3 choice dialogs), Transitions (#142, `SettingsPage.Transitions`: group Fades with Fade in and Fade out as `Slider`s, 0 = "Disabled" to 1000 ms in 100 ms steps, saved on release, named for TalkBack with the value as state), Automatic actions (#141, `SettingsPage.AutoActions`: group Device events with five whole-row switches; a summary states what Android allows, ADR 0033); choice dialogs are the shared `ChoiceDialog` (like the Theme page); each ends with the shared Restore defaults group. |
| Offline, cache, and download (#144) | `SettingsPage.Storage` is a hub like the Playback hub (`HubRow`: General, Media, Playback, Images); pages `StorageGeneral` (Offline mode switch, Automatic offline mode choice dialog), `StorageMedia` (Wi-Fi only downloads switch, Download quality choice dialog, Max simultaneous downloads chips 1-4; group Favorites: Automatic downloads of favorites choice dialog, Remove when unfavorited switch disabled while Disabled, #145), `StoragePlayback` (intro `bodyMedium` `onSurfaceVariant`, cache size chips), `StorageImages` (three switches) | Each page ends with the shared Restore defaults group. |
| Manage media providers (#147) | `ProvidersScreen` in `:feature:settings` (its own ViewModel): the accounts first (so list index = row for `rememberMqReorderState`), each a `surfaceContainerHigh` row with drag handle, `Lan` icon (`primary`), name `bodyLarge`, "user · address" `bodyMedium` `onSurfaceVariant`, "Active" `labelMedium` `primary`, `OverflowMenu` (Make active, Edit address, Sign in again, Sync now on the active one, Sign out and remove in `error`); Move up / Move down actions; then a full-width `FilledTonalButton` "Add media provider" | The picker, switch, remove (checkbox "Also delete its downloads", off) and Edit address dialogs are `MqDialog`s; Add and Sign in again open the onboarding screens on the Settings stack (`AddAccountKey`, `returnSteps`). |
| Sync manager (#148) | `SyncManagerSheet` in `:feature:settings`: a `ModalBottomSheet` from the hub's Sync manager entry (Miscellaneous, `MqIcons.Sync`), content `SyncManagerContent` (title `titleLarge` heading; one `surfaceContainerHigh` card per account, `shapes.large`: `Lan` icon, name, "user · address", Retry `IconButton` (`MqIcons.Refresh`, "Check <name> again"); status pills (full-round `Surface`, `labelLarge`): Online / Offline / Checking and Successful / Failed: <error in words> / Running / Never synced; a running sync shows "<step>: n of total" and a `LinearWavyProgressIndicator`, else "<time ago> · <tracks> in <duration>"; inactive accounts say they sync when active), then a full-width `Button` Sync all, disabled offline or while syncing, the reason under it `bodyMedium` `onSurfaceVariant` | Screenshot `SyncManagerContent` on `surfaceContainerLow`, not the sheet (popups render blank). The connection check asks each server (`checkServer`, 10 s) only with a network and offline mode off. |
| Backup & restore (#150) | `BackupScreen` in `:feature:settings` (`BackupSettingsKey`, hub Miscellaneous, `MqIcons.Restore`): the note `bodyMedium` `onSurfaceVariant`, a group of two `SegmentedListItem`s (Create a backup `MqIcons.Download`, Pick a backup to restore `MqIcons.Restore`, each with a summary; disabled while busy), then how it went in a `shapes.large` card (`secondaryContainer`, `errorContainer` for a failure, live region) with OK | The restore asks first in an `MqDialog` with `RestoreSummary` (made on, app version, settings count, one line per account); screenshot it in `MqDialogContent`. Files come from the system picker (`CreateDocument` / `OpenDocument`). Manage media providers shows a restored account (no token) with "Signed out" `labelMedium` `error` and a menu of Sign in and Sign out and remove only. |
| Restore defaults (every settings page) | `restoreDefaultsGroup` + `RestoreDefaultsDialog` in `:feature:settings`: own group at the bottom, `MqIcons.Restore` leading; `MqDialog` with no title, Cancel / Restore | Resets only that page's settings. |
| Play / Shuffle on album / artist header | `MqPlayShuffleButtons`: `Button` (Play, filled) + `FilledTonalButton` (Shuffle), half width each | Not the M3 `ButtonGroup` DSL: two plain buttons need no overflow menu and never collapse into one at fontScale 2.0. |
| Artist picture (Artist header) | `MqArtistAvatar` in `:core:designsystem`: `MaterialShapes.Cookie9Sided`, person icon without a picture | Accent shape, artists only. |
| Artist page header (#133) | `ArtistHeader` under a transparent `LargeFlexibleTopAppBar` (title fades in over the last 30 % of the collapse) | Background from Settings -> Artist page: the backdrop 16:9 edge to edge (`MqCover(aspectRatio, onError)`, a failed load falls back to the artist image), the artist image square edge to edge, the image color gradient behind `MqArtistAvatar` (50 % wide), or none; both fade into `surface`. Name `headlineMediumEmphasized` centered (heading), "albums · tracks · duration" `bodyMedium`, a `FlowRow` of Play (`Button`), Shuffle / Play next / Add to queue (`FilledTonalIconButton`), `MqFavoriteToggle`, More (`OverflowMenu`: hidden buttons, Add to playlist, Download). |
| Artist page sections (#133) | Albums and Appears on as `AlbumCard` rows chunked by the Artist page columns (portrait / landscape), Top tracks (`TrackRow(showCover = true)`, "Show all" `TextButton` in the title opening `ArtistTopTracksScreen`), About (biography `bodyMedium` `onSurfaceVariant`, three lines, a tap expands with the spatial spec; `ListItem` facts: genres, albums, tracks, favorite, rating stars) | Order from Settings -> Artist page; empty sections left out. Section titles: `titleMedium`, heading semantics. |
| Artist page settings (#133) | Settings page (`SettingsPage.Artist`): groups Header, Content; choice dialogs, columns as chips (`ChoiceItem`), "Hidden buttons" as checkbox rows with OK / Cancel, "Page content order" as an `OrganizeDialog` with fixed checks | Then the shared Restore defaults group. |
| Genre page (#134) | `GenreHeader` like the artist's: the most played album's cover square edge to edge (else the first album's), its color as a gradient behind a 60 % cover, or none; name, "albums · artists · tracks · duration", Play / Shuffle / Play next / Add to queue / More (Add to playlist; no Download). Sections: Artists and Album artists as `ArtistCard` rows, Albums grid (Library columns), Top tracks (all tracks, most played first, N shown, "Show all" opens `GenreTopTracksScreen`) | Order and switches from Settings -> Genre page (same dialog patterns as the Artist page). |
| Playlist page header (#135) | `PlaylistHeader` like the genre's: the playlist image square edge to edge, its color as a gradient behind a 60 % image (also when Image is chosen and there is no image: the placeholder), or none; name, a `DownloadForOffline` mark when all is downloaded and "tracks · duration"; Play and Resume (labels), Shuffle / Play next / Add to queue (`FilledTonalIconButton`), `MqFavoriteToggle`, More (hidden buttons, Download, Edit = selection mode, Rename, Delete) | Settings -> Playlist page: Header background, Hidden buttons, Dynamic colors, Restore defaults. The tracks keep their drag handles, swipes and selection below. |
| Disc headers (multi-disc albums) | `DiscHeader` in `:core:ui` (titleSmall, primary, heading semantics) | Only when the album has more than one disc. |
| Cover flight grid -> album | `Modifier.mqSharedCover(sharedCoverKey(id))` in `:core:designsystem` | Slow spatial spec; inert in previews and tests. |
| Like / Download toggles | Toggle buttons (icon) | Shape morphs on checked. |
| Icon toggles (favorite, shuffle, repeat) | `MqIconToggle`: `FilledTonalIconToggleButton` + `IconButtonDefaults.toggleableShapes()` | Round off, squarish on; ToggleOn haptics when the tap turns it on, ToggleOff otherwise; optional state description. |
| Favorite (Now Playing, album / artist header) | `MqFavoriteToggle` (an `MqIconToggle`) | Filled heart when on. Track menus use `FavoriteMenuItem` in `:core:ui`. |
| Shuffle / Repeat (Now Playing) | `MqIconToggle` in `MqTransportControls`' `leading` (repeat) and `trailing` (shuffle) slots (#125) | Repeat cycles off -> all -> one from the mode shown; `MqIcons.RepeatOne` for one; state description "Off" / "All tracks" / "This track". |
| Rating (Now Playing, album / artist header) | `MqStarRating(halfStars, allowHalf)`: five `IconButton` stars | Value in half stars (1-10, the server's 0-10). Filled / half (`StarHalf`, auto-mirrored) / outlined stars, filled in primary. Whole stars: tapping the current star clears it. Half stars: tap = whole, again = half, again = cleared. Each star says what a tap does; the row has a state description ("3.5 of 5 stars"). Hidden when the rating setting is Off. No haptics (plain taps). |
| Player transport controls | `MqTransportControls`: `ButtonGroup` of large icon buttons (`customItem` + `animateWidth`), optional `leading` / `trailing` slots | Play/Pause is the largest (Wide), round while paused, squarish while playing. The group drops one size when the row is narrower than the full sizes (320 dp phone, fontScale 2.0), so nothing goes to its overflow menu. |
| Context actions on long-press | Expressive `DropdownMenu` or `ModalBottomSheet` | Sheet for > 5 actions. |
| Search | `SearchBarDefaults.InputField` + `SearchBarState` on a search bar `Surface` | Fixed on top of the Search tab, results below. Not the collapsed `SearchBar`: in 1.5 it disables the soft keyboard and expects an expanded bar. Clear-text button in the trailing slot. |
| Search results | Section titles (`titleMedium`, heading) + `ArtistRow`, `AlbumRow`, `TrackRow(showCover = true)`, `PlaylistRow` | Groups in that order, then Genres (`GenreRow`, opens the genre page, not selectable; #130) and Record labels (`RecordLabelRow`, opens the label page; #131), empty groups hidden. Recent searches: `ListItem` rows + a "Clear" `TextButton`. Filter chips under the field (#136): `FilterChip`s All, Artists, Albums, Songs, Playlists, Genres (check on the selected one, disabled when the kind has nothing), one scrolling `Row` or a `FlowRow`; All = the groups (20 each, best match first), another chip = its whole kind sorted by the setting; the chosen chip stays (All shows while it has nothing). Songs carry a `MqFavoriteToggle` (`TrackRow(onFavorite)`, hidden while selecting; the cover's favorite overlay is then left out). |
| Search page settings (#136) | Settings page (`SettingsPage.Search`): group Results with whole-row switches (Single line filter chips, Show favorite button) and choice dialogs (Default track action with "Same as default" first, Sort by Relevance / Title) | Then the shared Restore defaults group. |
| Library hub (#129, ADR 0028) | `LibraryHubScreen` in `:feature:library` (root of the Library tab): `TopAppBar` with the Library icon (`primary`) + title, then a tile per chosen list (`LibraryShortcut`, order and look from Settings -> Page settings -> Library page) in a `LazyVerticalGrid`: tonal `Card` (`surfaceContainer`, `shapes.large`), icon (`primary`) over a centered `titleSmall` label (2 lines at most), or one row per tile (icon then `titleMedium`) in List style | Columns 2-4 (landscape 3-6) are capped so a tile stays at least `MediatheqSizes.libraryTileMinWidth` x font scale wide: words never break at fontScale 2.0. Gaps Compact / Normal / Large = spacing `s` / `m` / `l`. Labels and icons (`LibraryShortcut.label` / `.icon`) live in `:core:ui`. A tile opens its list on the Library tab's stack, with Back. |
| Genres (#130) | The Genres list in `LibraryScreen`: `GenreRow` (row cover of the genre's first album, or the `Genres` icon; name; "12 albums · 80 tracks") or, from the view menu, `GenreCard`s in the album columns (square cover or the icon on `surfaceContainerHigh`, centered name and counts) | Sorts Name, Album count, Track count; no filter, Select or search in list; top buttons play each genre's tracks once. Row and card live in `:core:ui` (Search uses the row). |
| Genre page (#130) | `GenreScreen` in `:feature:genre`: `LargeFlexibleTopAppBar` (name, back), counts `bodyMedium` `onSurfaceVariant` centered, `MqPlayShuffleButtons`, then Albums (grid rows in the album columns), Album artists (`LazyRow` of `ArtistCard`, width `albumCardInRow`), Tracks (`TrackRow(showCover = true)`, the first 10, then a centered "Show all" `TextButton`) | Section titles `titleMedium`, heading. Not found: `MqEmptyState` with the `Genres` icon and Back. Genres have no favorite or rating. |
| Record labels (#131) | The Record labels list in `LibraryScreen`: `RecordLabelRow` (row cover of the label's first album, or the `RecordLabel` icon; name; "12 albums"). A label's page is the Albums list with the label's name and icon as title, Back | Sorts Name, Album count; no filter, Select, search in list or grid. Top buttons play the shown labels' albums once. "[no label]" (MusicBrainz) is never shown (jellyfin R18). |
| Years (#129) | The Years list in `LibraryScreen`: `ListItem` per year (`CalendarMonth` icon, year, "n albums"), "Unknown year" last; a year opens its albums (`YearKey`, the Albums list of that year, title = the year) | Sorts Year (newest first) and Album count; no filter, Select or search in list (years are no items of their own); top buttons play the albums' tracks in year order. |
| Library list header (#123) | `LibraryHeader` in `:feature:library` (replaces the top bar): icon + `titleLarge` title (back on a list opened from the hub or a year, never on a list that is a navigation entry; hidden by the Media lists setting), top button row (Play, Shuffle, Play next, Add to queue on the whole filtered list) as `FilledTonalIconButton`s (Small icons), medium-size ones (Large icons) or `FilledTonalButton`s with labels (Normal), scrolling sideways; tool row: sort `TextButton` (label + arrow, invertible), filter `IconButton` (tonal while on, menu Favorites only / Downloaded only), view menu (album columns; Albums only), Select (selects all), search in list (`OutlinedTextField` under the header), item count `labelMedium` | Menus show a check and carry selected + radio (or checkbox) semantics. A filter that leaves nothing shows "No matches". |
| Artist / album / playlist rows | `ArtistRow` (round picture), `AlbumRow` (cover + album artists), `PlaylistRow` (cover + track count) in `:core:ui` | Shared `CoverRow`. Songs use `TrackRow(showCover = true)`. |
| Initial loading | `MqLoadingIndicator` (wraps `LoadingIndicator`, experimental) | Centered, only for full-screen loads > 300 ms. |
| Download progress | `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` | Determinate when size known. |
| Playback seek bar | `MqSeekBar` = `MqWaveformProgress` + elapsed / center slot / total (or `remainingLabel` "-7:25") | Keeps the dragged value until release, then seeks once. Disabled when the stream cannot be seeked. TalkBack reads the position against the total. |
| Quality label (Now Playing, #125) | `QualityLabelButton` in the seek bar's center slot: `labelLarge` `primary`, one line, 48 dp touch target, `Role.Button` | Kind (Dolby Atmos, Hi-Res Lossless, Lossless, High quality, High efficiency) or detail ("FLAC · 24/96 · 1250 kb/s", a conversion "Opus · 256 kb/s"); a tap switches, remembered. From what actually plays; hidden until the stream is known. |
| Waveform seek bar (#124) | `MqWaveformProgress` in `:core:designsystem` (`Canvas`, height `MediatheqSizes.waveformHeight`) | Styles Sharp, Smooth, Bars (up / down), Bars with reflection; played part `primary`, the rest `secondaryContainer` (the Slider's inactive track). No levels: the fallback, Basic (`Slider`) or Wavy (`LinearWavyProgressIndicator` under the seek area, its own semantics cleared); crossfade (effects spec) when levels arrive, the waveform grows in (spatial spec). Tap seeks (`SegmentTick`), drag seeks once on release (`GestureThresholdActivate` / `GestureEnd`). One slider node for TalkBack: progress range, set progress, state "1:23 of 4:05". Style setting in #126 (default Sharp + Basic). |
| Player colors | `rememberCoverColorScheme` + `MqCoverTheme` | Cover seed -> `SchemeContent` (ADR 0017), cached per cover, roles blended with the effects spec. |
| Queue screen | `QueueScreen` in `:feature:nowplaying`, inside the player overlay | Opened by the queue button in Now Playing's secondary row; back closes it before the player. |
| Lyrics (in place of the cover, #125) | `LyricsContent` in `:feature:nowplaying`, in the cover's square on a `surfaceContainer` `Surface` (`extraLarge`) | Toggled by the round `MqIconToggle` at the cover's bottom start and by the bottom bar; kept across tracks and rotation ("No lyrics" state). Only the cover carries the shared cover flight. Lines in `titleLarge`; the sung line `primary` + emphasized, others `onSurfaceVariant`; plain lyrics all `onSurface`. Keeps the sung line centered, pauses 3 s after a manual scroll. Tap a line = seek (`onClickLabel` "Play from here"), off when the stream cannot be seeked. |
| Output settings (#140) | `OutputListScreen` in `:feature:settings` (`OutputSettingsKey`, from the Playback hub): intro `bodyMedium` `onSurfaceVariant`, group Outputs with one `SegmentedListItem` per output (kind icon Smartphone / Headphones / Usb, name, "Current output" in `primary`, a Delete `IconButton` "Forget <name>" except on the phone, confirm `MqDialog`) | Phone first, then by name. |
| Output page (#140) | `OutputScreen` (`OutputKey(id)`; `null` = the current output, opened by Now Playing's Equalizer button on its Equalizer tab, the player closes first): `MqTabButtons` Settings / Equalizer under the top bar (the output's name). Settings: group Processing (Volume normalization chips, Bypass processing switch, Bit-perfect USB switch on USB outputs only), then Restore defaults. Equalizer: switch, preset chips (`ChoiceItem`), Bands item with one `Slider` per band in 1 dB steps, set once on release | Disabled controls always say why in the supporting text (bypass, bit-perfect playing). |
| Tabs as a connected button group | `MqTabButtons` in `:core:designsystem` (`ToggleButton`s with `ButtonGroupDefaults.connected*ButtonShapes`, equal widths) | Each button has `Role.Tab` and selected semantics. |
| Atmos not available (Now Playing) | Dolby mark (`formatBadge` size) + `labelLarge` "Dolby Atmos not available", then `labelMedium` `onSurfaceVariant` "Playing in stereo on this output" | Only while an Atmos track plays converted; merged for TalkBack. The "Dolby Atmos" badge for real passthrough comes with TV 1. |
| Bit-perfect indicator (Now Playing) | Tonal pill (`secondaryContainer`, `CircleShape`) "Bit-perfect · 96 kHz" + `labelMedium` note "Phone volume does not apply" | Only while the current track really plays bit-perfect; merged for TalkBack. |
| Drag to reorder (queue, playlist) | `rememberMqReorderState` + `Modifier.mqReorderItem` / `mqReorderHandle` in `:core:designsystem` | Drag handle icon, not a button; haptics on pick-up, each swap and drop; one commit on drop. Rows also get Move up / Move down custom accessibility actions. Off while shuffled. |
| Swipe to remove (queue) | `SwipeToDismissBox` | `errorContainer` background with a delete icon, drawn only while swiping (rows are rounded). |
| Swipe actions (track rows, #123) | `TrackRow(onSwipe = ...)` -> `SwipeableTrack` (`SwipeToDismissBox` that resets) + `trackSwipes(...)` in the screen | Sides from Settings -> Media lists; `GestureThresholdActivate` haptic at the threshold, the row springs back; `secondaryContainer` background with the action's icon and name while swiping; each side is also a custom accessibility action. Add to playlist opens the picker, Rate an `MqRatingDialog`. |
| Track row details (#123) | `TrackDetails` on the trailing side of `TrackRow`, from `LocalMediaListStyle` | Up to two, first above second: duration / codec / bitrate text `bodySmall`, favorite or downloaded icon (`MediatheqSizes.trackDetailIcon`, primary), rating as five small stars, "n plays". |
| Cover overlays (#123) | `CoverOverlays` on `AlbumCard` (top end) and row covers (compact, bottom end) | Favorite heart and downloaded icon on a `secondaryContainer` pill, named for TalkBack; each on or off in Settings -> Media lists. Album text centered in grids by default. |
| Rating dialog | `MqRatingDialog` in `:core:designsystem` (`MqDialog` + `MqStarRating`, Cancel only) | A star tap rates and closes. |
| Track / album overflow menu | `OverflowMenu` + `QueueMenuItems` in `:core:ui` | `DropdownMenu` (up to 5 actions). Screenshot the items in place: popups render blank in screenshot tests. |
| Multi-select (every media list, #123) | `SelectionToolbar` in `:core:ui` (`HorizontalFloatingToolbar`: Clear, "n selected", Play, Play next, Add to queue, extra actions, More menu with Add to playlist, Download, Favorite, Select all); `SelectableList` adds it and Back; `ListSelection` holds the keys | Long press (LongPress haptic) starts selecting, taps then toggle; the list pads its bottom by `SelectionToolbarSpace`. Albums, artists and playlists act by their tracks in list order. The playlist page adds Remove while editable. |
| Selectable rows and cards | `TrackRow` / `AlbumRow` / `ArtistRow` / `PlaylistRow(selected = ...)` = the selectable `ListItem` overload; `AlbumCard(selected = ...)` | A filled check replaces the cover of selected rows (on the cover's top start for cards); no menu, swipe or drag handle while selecting. |
| Open-source licenses (Settings -> About) | `LicensesScreen` in `:feature:settings`: `TopAppBar` with back + `LazyColumn` of `ListItem` rows (name, "version · license", expand arrow) | One row open at a time: license text `bodySmall` `onSurfaceVariant`, then the library's NOTICE (if any) under a "Notice" heading (`titleSmall`, heading semantics), and an "Open the project page" `TextButton`. The app first, then copied assets, then libraries by name (ADR 0022). |
| Dialogs (rename, delete confirm, new playlist) | `MqDialog` in `:core:designsystem` (wraps `BasicAlertDialog`) | Title, optional content, text buttons. Screenshot `MqDialogContent` in place: dialog windows render blank. |
| Add to playlist (track / album menus, Library "New playlist") | `AddToPlaylistKey` dialog entry (Navigation 3 `DialogSceneStrategy`) showing `MqDialogContent`: playlist rows, "New playlist" switches to a name field | Menu item `AddToPlaylistMenuItem` in `:core:ui`. Library Playlists tab: "+" in the top bar opens it with no tracks. |
| Pull to refresh (Library lists) | `PullToRefreshBox` | The hub has none: it lists no items. |
| Home tab (#127) | `TopAppBar` (Settings gear) + full-width `FilledTonalButton` "Shuffle all" + the chosen sections (`HomeSection`, order and visibility from Settings -> Page settings -> Home page) in a `PullToRefreshBox` (new random sets; a sync when online) | Header: title `titleMedium` heading + `MqIcons.ChevronRight` (the full list is #183). Display per section: Row (`LazyRow` of `AlbumCard` / `ArtistCard` / `PlaylistCard`, width `albumCardInRow`), Grid (the same row with 1-3 cards per column), Carousel (`MqCoverCarousel`), List (`TrackRow(showCover = true)`; track sections are always rows). Empty sections hidden. An album in several sections: only its first card has `sharedCover`. |
| Home Shortcuts (#128) | `MixShortcuts` in `:feature:home`: Track mix (`Shuffle`), Album mix (`Library`), Decade mix (`Radio`) as filled tonal `Card`s (`secondaryContainer`, `shapes.large`, icon over `labelLarge` label) in one row of 3 equal columns; Decade mix only when an album has a year | Decade mix opens a `ModalBottomSheet` with `DecadeList` (title `titleMedium` heading, one `ListItem` per decade, "1990s"); screenshot `DecadeList`, not the sheet (popups render blank). No more indicator on the header. Its own settings come with the section gear (#182). |
| Library page settings (#129) | Settings page (`SettingsPage.Library`): Library shortcuts (`OrganizeDialog`: `Checkbox` + label + drag handle rows, Move up / Move down actions, at least one checked, OK / Cancel; draft in the ViewModel), Style and Spacing choice dialogs, Columns and Columns in landscape as `FilterChip`s (Grid only) | Then the shared Restore defaults group. `OrganizeDialog` / `OrganizeDraft` also draw "Organize your navigation" (#122). |
| Home page settings (#127) | Settings page (`SettingsPage.Home`): every section as a `surfaceContainerHigh` row with drag handle (`rememberMqReorderState` on the page's list, sections first, `canDropOn` sections only), title and eye `MqIconToggle` (Visibility / VisibilityOff, state Shown / Hidden; hidden rows dimmed); Move up / Move down custom actions | Then "Reset active rows" (confirm dialog) and the shared Restore defaults group. The gear per section is #182. |
| Cover carousel | `MqCoverCarousel` in `:core:designsystem` (M3 `HorizontalMultiBrowseCarousel`, stable in 1.5) | Covers `extraLarge`, title and subtitle white on a black scrim gradient over the art; height and preferred width `MediatheqSizes.carouselItem`. |
| Artist / playlist cards | `ArtistCard` (`MqArtistAvatar`, name centered) and `PlaylistCard` (square cover, name, track count) in `:core:ui` | Same widths and text styles as `AlbumCard`. |
| Manage offline files (Downloads tab, #168, #149) | `DownloadsScreen`: `TopAppBar` "Manage offline files" (the navigation label stays Downloads) + `MqTabButtons` Files / Queue / Rules (tab in `rememberSaveable`). Files: summary card (`surfaceContainerHigh`, `shapes.large`: "Manual downloads: n tracks · size", "Automatic downloads: ..." `bodyMedium`, total `titleSmall`, `LinearProgressIndicator` of the share of space, free space `bodySmall`, `OutlinedButton` Cleanup), a filter `OutlinedTextField` (words, case and diacritics ignored), then sections (`titleMedium` primary, heading): Albums and Playlists (`AlbumRow` / `PlaylistRow` with `details` "size · duration" and a menu Play / Shuffle / Remove download; tap opens the page), Songs (tracks downloaded alone); nothing finished: `MqEmptyState` under the summary. Queue: full-width `FilledTonalButton` Pause all / Resume all, then every unfinished download as `TrackRow(showCover = true, supportingContent = ...)` with Queued / Waiting for Wi-Fi / Paused / Download failed in `error` or "Downloading n%" over a `LinearWavyProgressIndicator` (`queueStatus`: the user's pause wins over waiting); menu Download again (failed), Remove download (= cancel); empty: `MqEmptyState`. Rules: `ListItem`s for the #145 option and Remove when unfavorited, `FilledTonalButton` Change rules (Settings -> Offline, cache, and download -> Media); Disabled: `MqEmptyState` with the same action. Cleanup: `MqDialog` "Clean up offline files?" with what goes (failed downloads and leftover data, size), Clean up / Cancel; nothing to clean: no confirm button. | Empty sections hidden. A favorite's automatic download (#145) shows "Auto" before the details (rows) or as its status (songs) and has no Remove: it goes by unfavoriting. A download remembers what it was started from (album, playlist or alone); a collection's removal keeps tracks another one keeps. Screenshot the cleanup body with `MqDialogContent`. Later Movies and Series become sections. |
| Download (album, playlist, track menus) | `DownloadMenuItem` / `RemoveDownloadMenuItem` in `:core:ui` | Remove only in the Downloads tab. Header toggle later (after #48). |
| Offline mode, nothing downloaded (Home, Library tab, Search) | `NothingDownloaded` in `:core:ui` (`MqEmptyState`: cloud-off icon, "Nothing downloaded", "Open Downloads" switches tab, or opens Downloads on the current tab when it is not in the navigation) | Offline mode (#63): the tabs list only downloads; this shows when a tab has none. Search says "Offline mode: nothing downloaded matches" in its no-results state. |

If an element is not in this table, pick the closest M3 component, then add a row here in the same PR.

## 4. Motion

- Always read specs from the theme: `MaterialTheme.motionScheme`. Never hard-code `tween(300)` or custom `spring()` values in features.
- **Spatial** specs (may overshoot) for position, size, scale, shape, offset:
  `motionScheme.defaultSpatialSpec()`, `fastSpatialSpec()`, `slowSpatialSpec()`
- **Effects** specs (no overshoot) for color, alpha, blur:
  `motionScheme.defaultEffectsSpec()`, `fastEffectsSpec()`, `slowEffectsSpec()`
- Picking speed: small elements (buttons, toggles) = fast; containers and nav = default; full-screen (mini player -> Now Playing) = slow.
- Mixing rule: never use a spatial spec on color/alpha (overshoot causes flicker).
- Screen transitions: navigation transitions use the theme specs; shared element for album art between grid -> Album screen and mini player -> Now Playing (`SharedTransitionLayout`).
- Respect "Remove animations": if system animator scale is 0, skip non-essential motion.

## 5. mediatheQ-specific patterns

### Mini player (floating pill)

- Floats above the nav bar (or toolbar) with `l` margin left/right and `s` above it; beside the rail in landscape. Lists scroll under it and end above it (`plusBottomBars`, #122).
- Content: cover (rounded), title + artist (single line, marquee off), play/pause, next.
- Background: `surfaceContainerHigh` from the cover-derived scheme; tonal elevation, no heavy shadow.
- Tap or swipe up -> Now Playing (shared element on cover). Swipe down on Now Playing returns.
- Hidden when nothing is queued; enter/exit with spatial spec (slide + scale), alpha with effects spec.

### Now Playing

- Background (Settings -> Now playing screens, #126): gradient from the cover scheme (`primaryContainer` -> `surface`, default), solid (the cover scheme's `surfaceContainerHigh`, so every on-surface role keeps its contrast), or the app theme's `surface` (read outside `MqCoverTheme`). No blur of the cover image (no Liquid Glass look).
- Cover tap (play / pause, lyrics, none) acts only while the cover shows; `MqCoverPlayButton` over the cover while paused when chosen.
- Large cover `extraLarge` shape, max width `MediatheqSizes.playerCoverMax`, centered; a horizontal swipe on it changes track (`GestureThresholdActivate`).
- Portrait (#125): close button; cover (or lyrics); title (emphasized, one line, `basicMarquee` unless animations are off) with `MqFavoriteToggle` and the track menu (Add to playlist, Download, Go to album, Go to artist); artists (one opens its page, several a menu) and album; waveform with elapsed, quality label, remaining; bit-perfect badge / Atmos note; stars (hidden when ratings are off); repeat, previous, play/pause, next, shuffle; bottom bar Queue, Lyrics, Equalizer.
- Landscape (wider than tall, by `BoxWithConstraints`, not the configuration): the cover square on the left; on the right a scrolling column with the close button and the bottom bar's buttons in one row, then the same blocks; stars only when at least `MediatheqSizes.playerRatingMinHeight` tall.
- Artist, album, Equalizer and Add to playlist close the player and open behind it (`NowPlayingHost(onNavigate)`).
- Atmos badge: small tonal chip "Dolby Atmos" only when the current stream is E-AC-3 JOC or TrueHD Atmos passthrough (TV 1). Until then an Atmos track that plays converted shows the "Dolby Atmos not available" note (component map).

### Album grid

- Square cover, title (1 line) and artist (1 line) under it. Ellipsize, never wrap.
- BlurHash placeholder, crossfade to image with effects spec.

### Haptics (subtle)

- Use `LocalHapticFeedback.current.performHapticFeedback(...)` with semantic types only.
- Allowed moments: toggles (like, shuffle, repeat), long-press menu open, seek bar snapping to chapter/section, drag reorder in queue, a swipe that acts (track row swipe actions, Now Playing's cover changing track).
- Never on plain taps, scroll, or navigation.
- Respect the system haptics setting (do not force vibration).

### Empty, error and offline states

- One `MqEmptyState(icon, title, body, action?)` component for all screens.
- Errors: inline message with retry button; snackbar only for transient failures.
- Offline (offline mode: the setting, or no network, #63): every tab works and lists only what is downloaded; a tab with nothing shows `NothingDownloaded`. Settings (Home gear) needs no network, so Log out is always reachable.

## 6. Accessibility (non-negotiable)

- Touch targets >= 48 dp (M3 enforces by default - do not remove `minimumInteractiveComponentSize`).
- Every icon-only button has `contentDescription` (string resource, English, translatable).
- Layouts survive `fontScale = 2.0` without clipping (test in previews).
- Color is never the only signal (selected state also changes icon fill or shape).
- Group track rows for TalkBack: one focusable row reading "Title, Artist, Duration".

## 7. Previews and screenshot tests

- Testing tool: **Compose Preview Screenshot Testing** (official Google tool). Not Roborazzi, not Paparazzi. Setup details live in the `testing` skill.
- Every `Mq*` component and every screen gets screenshot previews:
  - light + dark
  - Iris + one dynamic-color-like scheme (fixed test scheme, not device dynamic color)
  - `fontScale` 1.0 and 2.0
  - phone width (Pixel 9). Tablet width later.
- Use one shared multipreview annotation from `:core:designsystem/preview`, e.g. `@MqPreviews`.
- Previews are stateless: screen composables take `UiState` + lambdas. No ViewModel, no Hilt in previews.
- Preview fixtures (fake albums, tracks, covers) live in `preview/` and are deterministic (no random, no current time).
- Screenshot tests cannot verify motion. For motion-critical behavior (mini player -> Now Playing), add a Compose UI test using `mainClock` control.

## 8. Don'ts

- No hex colors, raw `.dp`, `fontSize`, or custom `tween`/`spring` in feature modules.
- No `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` outside `:core:designsystem`.
- No Liquid Glass / heavy blur / iOS-style translucency.
- No font files and no per-screen fonts (fonts come only from `mediatheqTypography`, ADR 0026).
- No `MaterialShapes` on album art.
- No Material 2 (`androidx.compose.material`) imports.
- No ripple/indication removal to "look cleaner".
- No new component without a preview and screenshot reference.

## 9. Done checklist (run before `/end`)

- [ ] Uses only theme tokens (color roles, typography, shapes, spacing, motion scheme).
- [ ] Experimental APIs only inside `Mq*` wrappers.
- [ ] Component map updated if a new UI element was introduced.
- [ ] `@MqPreviews` added; screenshot references generated and committed.
- [ ] Screenshot validation task passes.
- [ ] Checked at `fontScale` 2.0 and in dark mode.
- [ ] TalkBack labels on all icon buttons.
- [ ] Verified on Pixel 9 (manual test queued with `/test`) for motion and haptics.
