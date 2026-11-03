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
- Color changes between tracks animate with the **effects** spec (section 4), never snap.

### Typography

- The system font by default; the user may pick a downloadable Google Font (ADR 0026), built in one place by
  `mediatheqTypography(font)`. Display-only fonts apply to display/headline/title styles. No font files in the APK.
- Use the default M3 type scale. For emphasis (album titles on Now Playing, hero headers) use the **emphasized** text styles if present in the pinned version; otherwise `FontWeight` bump in one central place in `MediatheqTypography`.
- Never set `fontSize` inline in screens. Use `MaterialTheme.typography.*`.

### Shape

- Use `MaterialTheme.shapes` roles. Expressive corners are larger - prefer `large` / `extraLarge` for cards and sheets.
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
| Main navigation (#122) | `AppShell` in `:app`, entries and style from Settings -> Navigation and media start (up to 6, Home always; default Home, Library, Search, Downloads). Portrait: `ShortNavigationBar` + `ShortNavigationBarItem` (labels hidden with 5+ entries at fontScale >= 1.5, the icon then carries the name), or `HorizontalFloatingToolbar` of icon tabs (selected = `FilledTonalIconButton`, `Role.Tab` + selected semantics), or the same with the **last** entry as `FloatingToolbarDefaults.StandardFloatingActionButton`. Landscape: collapsed `WideNavigationRail` on the left, or the portrait style. One entry: no navigation. | Albums, Artists, Playlists and Tracks are Library lists with a fixed filter (no filter bar), each its own tab. Transparency (0-100 %) changes only the bar / toolbar container color. |
| Screens under the bottom bars (#122) | Screens draw under the mini player and the bar; `LocalBottomBarsPadding` (in `:core:designsystem`) is their height | Scrolling content ends with `PaddingValues.plusBottomBars()`, fixed content uses `Modifier.bottomBarsPadding()`; `MqEmptyState` pads itself. A new screen with a list must do the same. |
| Mini player | `MqMiniPlayer` (custom) | Floating pill above the nav bar. See section 5. `coverModifier` carries the shared cover. |
| Screen headers (Album, Artist, Playlist) | `LargeFlexibleTopAppBar` with `exitUntilCollapsedScrollBehavior` | Title = item name, subtitle = artist / count. |
| Simple screens (Settings hub and its pages, Settings -> Playback -> Audio) | `TopAppBar` with a back button, in a `Scaffold` (`contentWindowInsets = WindowInsets(0)`) | |
| Album grid | `LazyVerticalGrid(GridCells.Fixed(columns))` | `columns` from DataStore, range 2-4, default 2. |
| Track rows | Expressive `ListItem` | Leading: track number or art. Trailing: duration + overflow menu. |
| Audio format badge (track rows) | `FormatBadgeIcon` in `:core:ui` (icons `MqIcons.Format*`, `MediatheqSizes.formatBadge` 18 dp, content color) | Before the artists (also when the line is replaced, e.g. Downloads): Dolby double-D (Atmos), five dots (surround / DTS:X), two dots (stereo), one dot (mono); none when the channels are unknown. Filled shapes, 20 of 24 wide. Named for TalkBack ("Dolby Atmos", "Surround 5.1", "Stereo"). |
| Grouped settings | `SegmentedListItem` with `ListItemDefaults.segmentedShapes(index, count)`, `SegmentedGap` between rows, one tonal container (`surfaceContainerHigh`, also when checked) | Group title `titleMedium` primary, heading. Choices: `FilterChip`s in a `FlowRow` under the title and summary, selected one with a check. On/off: the checkable overload (whole row toggles) + `Switch(onCheckedChange = null)`. Log out in `error` color. |
| Settings hub (#120) | `SettingsHubScreen` in `:feature:settings`: groups (Settings, Miscellaneous, About) of `SegmentedListItem` rows, leading icon (Material Symbols, `contentDescription = null`), whole row opens the page | Pages share `SettingsScaffold` (top bar with back + `LazyColumn` of groups). An entry is added in the issue that gives its page content; every page later gets "Restore defaults" (#121). "Manage offline files" shows only while Downloads is not in the navigation (#122) and opens Downloads with a back button. |
| Theme, font and colors (Settings -> Interface, #121) | Settings page (`SettingsPage.Theme`): chips for mode and color source, custom color = `MqColorSwatch` presets (`MqSeedPresets`) + hue `Slider` (saved on release), read-only swatches of the scheme on screen, choice dialogs (`MqDialog`, one row per option with a check, a tap applies and closes) | Swatches are `MqColorSwatch` (round, outlined, check when selected, radio semantics). |
| Media lists (Settings -> Interface, #123) | Settings page (`SettingsPage.MediaLists`): groups List header, Tracks, Covers and grids; whole-row switches; choice dialogs like the Theme page; "Hidden top buttons" is an `MqDialog` of checkbox rows with OK / Cancel (the checks live in the dialog until OK) | `:app` maps the stored settings to `MediaListStyle` and provides `LocalMediaListStyle`, so every list applies a change at once. |
| Navigation and media start (Settings -> Interface, #122) | Settings page (`SettingsPage.Navigation`): dialog rows for entries and styles, transparency `Slider` (10 % steps, saved on release), switches, choice dialogs like the Theme page | "Organize your navigation": `MqDialog` (OK / Cancel) with a `LazyColumn` of rows (`Checkbox` + label + drag handle, `rememberMqReorderState`, Move up / Move down actions); Home's checkbox disabled, unchecked rows disabled at 6. The draft lives in the ViewModel. |
| Restore defaults (every settings page) | `restoreDefaultsGroup` + `RestoreDefaultsDialog` in `:feature:settings`: own group at the bottom, `MqIcons.Restore` leading; `MqDialog` with no title, Cancel / Restore | Resets only that page's settings. |
| Play / Shuffle on album / artist header | `MqPlayShuffleButtons`: `Button` (Play, filled) + `FilledTonalButton` (Shuffle), half width each | Not the M3 `ButtonGroup` DSL: two plain buttons need no overflow menu and never collapse into one at fontScale 2.0. |
| Artist picture (Artist header) | `MqArtistAvatar` in `:core:designsystem`: `MaterialShapes.Cookie9Sided`, person icon without a picture | Accent shape, artists only. |
| Artist page sections (top tracks, albums, appears on) | `TrackRow(showCover = true)` + `AlbumCard` rows chunked by the Library column setting, inside one `LazyColumn` | Section titles: `titleMedium`, heading semantics. |
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
| Search results | Section titles (`titleMedium`, heading) + `ArtistRow`, `AlbumRow`, `TrackRow(showCover = true)`, `PlaylistRow` | Groups in that order, empty groups hidden. Recent searches: `ListItem` rows + a "Clear" `TextButton`. |
| Filters (Library: Albums/Artists/Playlists/Songs) | `MqFilterBar`: `FilterChip` row (default) or connected `ToggleButton` row | User setting, ADR 0014. Chips scroll and fit at fontScale 2.0; connected buttons share the width and ellipsize. |
| Library list header (#123) | `LibraryHeader` in `:feature:library` (replaces the top bar): icon + `titleLarge` title (back only for a list that is not a tab, unless hidden), top button row (Play, Shuffle, Play next, Add to queue on the whole filtered list) as `FilledTonalIconButton`s (Small icons), medium-size ones (Large icons) or `FilledTonalButton`s with labels (Normal), scrolling sideways; tool row: sort `TextButton` (label + arrow, invertible), filter `IconButton` (tonal while on, menu Favorites only / Downloaded only), view menu, Select (selects all), search in list (`OutlinedTextField` under the filter bar), item count `labelMedium` | Menus show a check and carry selected + radio (or checkbox) semantics. A filter that leaves nothing shows "No matches". |
| Artist / album / playlist rows | `ArtistRow` (round picture), `AlbumRow` (cover + album artists), `PlaylistRow` (cover + track count) in `:core:ui` | Shared `CoverRow`. Songs use `TrackRow(showCover = true)`. |
| Initial loading | `MqLoadingIndicator` (wraps `LoadingIndicator`, experimental) | Centered, only for full-screen loads > 300 ms. |
| Download progress | `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` | Determinate when size known. |
| Playback seek bar | `MqSeekBar` = `MqWaveformProgress` + elapsed / center slot / total (or `remainingLabel` "-7:25") | Keeps the dragged value until release, then seeks once. Disabled when the stream cannot be seeked. TalkBack reads the position against the total. |
| Quality label (Now Playing, #125) | `QualityLabelButton` in the seek bar's center slot: `labelLarge` `primary`, one line, 48 dp touch target, `Role.Button` | Kind (Dolby Atmos, Hi-Res Lossless, Lossless, High quality, High efficiency) or detail ("FLAC · 24/96 · 1250 kb/s", a conversion "Opus · 256 kb/s"); a tap switches, remembered. From what actually plays; hidden until the stream is known. |
| Waveform seek bar (#124) | `MqWaveformProgress` in `:core:designsystem` (`Canvas`, height `MediatheqSizes.waveformHeight`) | Styles Sharp, Smooth, Bars (up / down), Bars with reflection; played part `primary`, the rest `secondaryContainer` (the Slider's inactive track). No levels: the fallback, Basic (`Slider`) or Wavy (`LinearWavyProgressIndicator` under the seek area, its own semantics cleared); crossfade (effects spec) when levels arrive, the waveform grows in (spatial spec). Tap seeks (`SegmentTick`), drag seeks once on release (`GestureThresholdActivate` / `GestureEnd`). One slider node for TalkBack: progress range, set progress, state "1:23 of 4:05". Style setting in #126 (default Sharp + Basic). |
| Player colors | `rememberCoverColorScheme` + `MqCoverTheme` | Cover seed -> `SchemeContent` (ADR 0017), cached per cover, roles blended with the effects spec. |
| Queue screen | `QueueScreen` in `:feature:nowplaying`, inside the player overlay | Opened by the queue button in Now Playing's secondary row; back closes it before the player. |
| Lyrics (in place of the cover, #125) | `LyricsContent` in `:feature:nowplaying`, in the cover's square on a `surfaceContainer` `Surface` (`extraLarge`) | Toggled by the round `MqIconToggle` at the cover's bottom start and by the bottom bar; kept across tracks and rotation ("No lyrics" state). Only the cover carries the shared cover flight. Lines in `titleLarge`; the sung line `primary` + emphasized, others `onSurfaceVariant`; plain lyrics all `onSurface`. Keeps the sung line centered, pauses 3 s after a manual scroll. Tap a line = seek (`onClickLabel` "Play from here"), off when the stream cannot be seeked. |
| Audio screen (bit-perfect USB, equalizer) | `AudioSettingsScreen` in `:feature:settings` (`AudioSettingsKey`) | Opened from Settings -> Playback -> Audio and from Now Playing's Equalizer button (#125, the player closes first). Whole `ListItem` rows toggle (`Role.Switch`); presets as `FilterChip`s in a `FlowRow`; one `Slider` per band in 1 dB steps, set once on release. Disabled controls always say why in the supporting text. |
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
| Pull to refresh (Library) | `PullToRefreshBox` | |
| Home tab | `TopAppBar` (Settings gear `IconButton`) + full-width `FilledTonalButton` "Shuffle all" + section titles (`titleMedium`, heading) + `LazyRow` of `AlbumCard` (width `MediatheqSizes.albumCardInRow`) + favorite songs as `TrackRow(showCover = true)` | Sections: Recently played, Recently added, Favorite albums, Favorite songs; empty ones hidden. An album in several rows: only its first card has `sharedCover`. |
| Downloads tab (#168) | `TopAppBar` + totals (`titleSmall` "3.8 GB · 12 h", `LinearProgressIndicator` of the share of space, free space `bodySmall`) + sections (`titleMedium` primary, heading): Downloading (only while queued / downloading / waiting / failed: `TrackRow(showCover = true, supportingContent = ...)` with Queued / Waiting for Wi-Fi / Download failed in `error` or "Downloading n%" over a `LinearWavyProgressIndicator`), Albums and Playlists (`AlbumRow` / `PlaylistRow` with `details` "size · duration" and a menu Play / Shuffle / Remove download; tap opens the page), Songs (tracks downloaded alone) | Empty sections hidden. A download remembers what it was started from (album, playlist or alone); a collection's removal keeps tracks another one keeps. Later Movies and Series become sections. |
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

- Background: vertical gradient from cover scheme (`primaryContainer` -> `surface`). No blur of the cover image (no Liquid Glass look).
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
