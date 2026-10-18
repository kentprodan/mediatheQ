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

```kotlin
@Composable
fun MediatheqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,      // user setting "Material You", default OFF
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (darkTheme) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        darkTheme -> IrisDarkColorScheme
        else -> IrisLightColorScheme
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = MediatheqShapes,
        typography = MediatheqTypography,
        content = content,
    )
}
```

### Color - Iris

- Default palette: **Iris**. Seed `#5B45D6` (light), primary in dark `#A391FF`.
- Generate the full tonal schemes once with Material Theme Builder (seed `#5B45D6`, scheme variant per ADR), export to Kotlin, store as `IrisLightColorScheme` / `IrisDarkColorScheme`. Do not hand-pick individual roles.
- Material You (dynamic color) is optional, off by default, Android 12+.
- Screens use **color roles only** (`MaterialTheme.colorScheme.primaryContainer`, etc.). No hex values outside `theme/`.
- Contrast: keep text on its matching `on*` role. No custom alpha on text below 0.6.

### Color - player from cover art

- The Now Playing background and the mini player tint come from the current album cover.
- Pipeline (exact libraries in ADR): Coil 3 bitmap -> seed color (Palette or color-utilities) -> full `ColorScheme` from seed -> wrap the player subtree in its own `MaterialTheme(colorScheme = coverScheme)`.
- Cache the scheme per album ID. Never recompute on recomposition.
- Fallback: Iris scheme when no cover or extraction fails.
- Color changes between tracks animate with the **effects** spec (section 4), never snap.

### Typography

- System font. No custom font files.
- Use the default M3 type scale. For emphasis (album titles on Now Playing, hero headers) use the **emphasized** text styles if present in the pinned version; otherwise `FontWeight` bump in one central place in `MediatheqTypography`.
- Never set `fontSize` inline in screens. Use `MaterialTheme.typography.*`.

### Shape

- Use `MaterialTheme.shapes` roles. Expressive corners are larger - prefer `large` / `extraLarge` for cards and sheets.
- Album art: rounded rect from `MaterialTheme.shapes.medium` (grid) and `extraLarge` (Now Playing).
- Mini player: fully rounded pill (`CircleShape` / full corner).
- `MaterialShapes` (cookie, sunny, etc.) only for **accents**: artist avatars, play button morph, empty states. Never for album covers (it crops art). Experimental - only via `Mq*` wrappers.

### Spacing

- `MediatheqSpacing` tokens: `xs 4`, `s 8`, `m 12`, `l 16`, `xl 24`, `xxl 32` (dp). Screen horizontal padding = `l`.
- No raw `.dp` literals in feature modules except `0.dp`.

## 3. Component map for mediatheQ

| UI element | Component | Notes |
|---|---|---|
| Bottom navigation (Home, Library, Search, Downloads) | `ShortNavigationBar` + `ShortNavigationBarItem` | Phone. On tablet/foldable later: `NavigationSuiteScaffold` -> `WideNavigationRail`. |
| Mini player | `MqMiniPlayer` (custom) | Floating pill above the nav bar. See section 5. |
| Screen headers (Album, Artist, Playlist) | `LargeFlexibleTopAppBar` with `exitUntilCollapsedScrollBehavior` | Title = item name, subtitle = artist / count. |
| Simple screens (Settings) | `TopAppBar` / `MediumFlexibleTopAppBar` | |
| Album grid | `LazyVerticalGrid(GridCells.Fixed(columns))` | `columns` from DataStore, range 2-4, default 2. |
| Track rows | Expressive `ListItem` | Leading: track number or art. Trailing: duration + overflow menu. |
| Grouped settings | Segmented list items | |
| Play / Shuffle on album header | `ButtonGroup` | Play = filled, Shuffle = tonal. |
| Like / Download toggles | Toggle buttons (icon) | Shape morphs on checked. |
| Player transport controls | `ButtonGroup` with large icon buttons | Play/Pause is the largest, morphs round -> squarish. |
| Context actions on long-press | Expressive `DropdownMenu` or `ModalBottomSheet` | Sheet for > 5 actions. |
| Search | `SearchBar` with `SearchBarState` | Docked on Search tab, results below. |
| Filters (Library: Albums/Artists/Playlists/Songs) | `FilterChip` row or connected `ButtonGroup` | Pick one per ADR, use everywhere. |
| Initial loading | `MqLoadingIndicator` (wraps `LoadingIndicator`, experimental) | Centered, only for full-screen loads > 300 ms. |
| Download progress | `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` | Determinate when size known. |
| Playback seek bar | `Slider` (stateful) | Waveform scrubber is a later feature - keep seek bar behind `MqSeekBar`. |
| Multi-select actions (playlist edit) | `FloatingToolbar` | Appears on selection. |
| Pull to refresh (Library) | `PullToRefreshBox` | |

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

- Floats above the nav bar with `l` margin left/right and `s` above the bar. Does not cover content: lists get bottom content padding = pill height + spacing.
- Content: cover (rounded), title + artist (single line, marquee off), play/pause, next.
- Background: `surfaceContainerHigh` from the cover-derived scheme; tonal elevation, no heavy shadow.
- Tap or swipe up -> Now Playing (shared element on cover). Swipe down on Now Playing returns.
- Hidden when nothing is queued; enter/exit with spatial spec (slide + scale), alpha with effects spec.

### Now Playing

- Background: vertical gradient from cover scheme (`primaryContainer` -> `surface`). No blur of the cover image (no Liquid Glass look).
- Large cover `extraLarge` shape, max width 360 dp, centered.
- Below: title (emphasized), artist, seek bar, transport `ButtonGroup`, secondary row (queue, lyrics, output device, Atmos badge when active).
- Atmos badge: small tonal chip "Dolby Atmos" only when the current stream is E-AC-3 JOC or TrueHD Atmos.

### Album grid

- Square cover, title (1 line) and artist (1 line) under it. Ellipsize, never wrap.
- BlurHash placeholder, crossfade to image with effects spec.

### Haptics (subtle)

- Use `LocalHapticFeedback.current.performHapticFeedback(...)` with semantic types only.
- Allowed moments: toggles (like, shuffle, repeat), long-press menu open, seek bar snapping to chapter/section, drag reorder in queue.
- Never on plain taps, scroll, or navigation.
- Respect the system haptics setting (do not force vibration).

### Empty, error and offline states

- One `MqEmptyState(icon, title, body, action?)` component for all screens.
- Errors: inline message with retry button; snackbar only for transient failures.
- Offline: Downloads tab works fully; other tabs show the offline empty state.

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
- No custom fonts.
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
