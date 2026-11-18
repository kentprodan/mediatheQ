# Android mobile design guidelines: what mediatheQ follows, and where the phone app departs

Notes from developer.android.com/design/ui/mobile (all guide pages, read 2026-11-18 at the owner's request) and the
Material 3 component docs (material-components-android `docs/components`, which mirror m3.material.io incl. the M3
Expressive updates). Goal (owner): a clean, native, professional phone app without the "AI-coded" feel, using the
official components where they fit. The TV counterpart is `tv-design-guidelines.md`.

## Color
- Use accent colors mindfully: the eye goes to vibrant color. Primary goes to the crucial call to action (a FAB, the one
  filled button); secondary and tertiary follow down the hierarchy.
- "An oversaturated look can result in using only the base roles primary, secondary, tertiary": use the less vibrant
  container roles and outline roles for hierarchy.
- Surfaces are most of the screen and are neutral ("the human eye needs space to relax"). Tonal surfaces come from the
  neutral palettes.
- Dynamic color (wallpaper) first; a static scheme as the fallback. Content color (from artwork) works with one content
  source per view; don't mix many color sources in one view.
- Semantic colors keep one meaning across the app. Color is never the only affordance.
- Contrast: text 4.5:1, non-text 3:1.

## Theme, type, icons
- Follow the system: light / dark, dynamic color, contrast, font size (sp). Locking a theme is discouraged.
- One icon style everywhere (Filled, Outlined, Rounded, Sharp or Two tone). Toggle buttons: outlined icon off, filled
  icon on; default icon buttons use filled icons.
- Body text never under 12 sp.

## Layout
- Compact margins 16 dp; 8 dp grid (4 dp for icons and small parts); consistent spacing between like elements.
- Implicit containment (whitespace, type, dividers) by default; explicit containment (cards) to group content with its
  actions. Not everything in a card.
- "Don't overwhelm your user with too many actions per view." One highest action (FAB); secondary actions in the top
  bar or near their content; rare actions in an overflow menu.
- Aspect ratios 16:9, 3:2, 4:3, 1:1, 3:4, 2:3.
- Edge to edge: transparent status and gesture bars, content under them; top app bars collapse on scroll; a translucent
  status protection only when content scrolls under it, never two protections stacked; no tap targets in gesture insets.
- Adaptive: navigation bar on compact, navigation rail on medium and expanded; list-detail for settings on large screens.

## Navigation
- Navigation bar: 3-5 top-level destinations of the same level. Drawer only for more, and less ergonomic.
- Tabs and the toolbar are secondary navigation inside a destination.
- Predictive back: full-screen surfaces scale to 90 % and fade through at 35 %; shared elements detach and follow the
  gesture.
- Settings are usually not a top-level destination: a top bar icon or the overflow menu, last.

## Settings (patterns/settings)
- Respect system settings; "your app may not need its own settings". Polite defaults (most users' choice, low risk,
  less battery and data).
- Include infrequent preferences only. Frequent actions stay contextual (e.g. captions in the player).
- Avoid in settings: app information (version, licenses), account management, copies of system settings.
- Group by topic, headings between groups; more than 15 settings -> subscreens; search for deep hierarchies.
- Labels: short, the important word first, impersonal ("Notifications"), no generic verbs (Set, Change, Edit, Modify,
  Manage, Use, Select, Choose), no repeat of the section title. Supporting text shows the current state, not a
  description, and only when needed.
- Switch or checkbox for on/off; radio buttons in a dialog or subscreen for one of many (never for 2 or fewer); slider
  for ranges.

## Components (M3 + M3 Expressive)
- Buttons, in order of emphasis: elevated / filled, tonal, outlined, text. Five sizes (XS-XL), round or square, shape
  morph on press and on select. Toggle buttons for binary choices (Save, Favorite).
- Icon buttons, in order of emphasis: filled, tonal, outlined, standard. "For the lowest emphasis use standard."
- Button groups (standard, connected) replace segmented buttons. Split button for an action with options.
- FAB: the screen's primary action; medium FAB recommended; extended FABs small / medium / large; the FAB menu (2-6
  actions) replaces speed dials and stacked small FABs.
- Toolbars: floating toolbar (standard or vibrant, can pair with a FAB) for the page's actions; docked toolbar replaces
  the bottom app bar.
- App bars: search app bar (home pages where search is key), small, medium flexible, large flexible (title, subtitle,
  start or center aligned, collapse on scroll).
- Navigation bar (expressive): 64 dp high, 56 dp active indicator, horizontal items at 600 dp and up.
- Lists: standard and segmented (expressive); cards: elevated, filled, outlined (same function, style only).
- Carousel: multi-browse, uncontained, hero, full-screen.
- Chips: assist (action), filter (filter content), input (user's entries), suggestion (generated).
- Progress: one indicator type per kind of activity across the app; wavy "when a more expressive style is
  appropriate"; the loading indicator replaces indeterminate circular progress for waits under 5 s.
- Sliders (standard, centered, range; stops), menus, search bar / search view, bottom sheets, dialogs, snackbars.

## Audit of the phone app (2026-11-18, from the screenshot references)

What gives the "AI-coded" feel, by weight:

1. **Purple everywhere.** Iris is MCU `SchemeVibrant` (ADR 0012) and the default color source: its neutrals are
   chromatic, so background, surfaces and containers are violet (dark background `#13062C`, light `#FBF4FF`, containers
   lilac). On top, tonal buttons, chips, settings rows, the search field and the nav bar are all `secondaryContainer`
   lilac. The guideline wants neutral surfaces and accent only where it means something. Same diagnosis as TV (#517).
2. **Too many equal-weight actions.** Album / artist / playlist / genre headers: one filled button plus 4-5 tonal icon
   buttons in tinted circles (19 `FilledTonalIconButton` uses in features). Search song rows carry a tonal heart each.
   Home opens with a full-width "Shuffle all" bar. The guideline: one prominent action, the rest standard icon buttons,
   rare ones in the overflow.
3. **Two prominent buttons side by side** (album: Play filled + Resume tonal, both large with labels).
4. **Containers on every row.** Settings rows are each a tinted segmented block; group headings in `primary`. The
   guideline allows segmented lists, but with neutral containers and fewer accents.
5. **Settings carry what the guideline keeps out**: About, Changelog, Contact support, Manage media providers
   (accounts), Manage offline files, Sync manager; generic labels ("Manage ...") ; a very large number of options.
6. **Now Playing**: tinted gradient background, five round tonal transport buttons, a row of stars, lyrics in two
   places, the quality label in `primary` as large as the times.
7. Smaller: centered multi-line metadata in album headers ("Hi-Res Lossless (FLAC 24/96)" wraps); Home has no search
   entry; covers and placeholder art also lean violet.

What already follows the guidelines and stays: edge to edge with insets, `ShortNavigationBar` / `WideNavigationRail`,
flexible app bars, expressive list items, `ButtonGroup` transport, `MqTabButtons` as a connected button group, the
loading indicator, 48 dp targets, fontScale 2.0 checks, predictive back motion (#405), one rounded icon set.

## Owner decisions (2026-11-18)

- **Color**: Material You is the default color source; Iris stays as the option and the fallback, made neutral like
  TV (#517): neutral surfaces and containers, Iris only on the primary roles (main button, progress, active item).
  Placeholder art neutral.
- **Now Playing**: a dark neutral background tinted lightly by the cover; the cover color only on Play, the progress and
  accents. Transport: Play / Pause large and filled (expressive morph), previous / next large standard icon buttons,
  repeat / shuffle small standard icon buttons colored only when on. Stars stay as a row, smaller and grey when empty.
  The quality label small (badge style); accent only for Dolby Atmos and bit-perfect. Lyrics only from the bottom bar.
- **Page headers** (album, artist, playlist, genre, movie, series): start aligned; Play (filled) and Shuffle
  (tonal or outlined) as buttons, Favorite and More as standard icon buttons, Play next / Add to queue / Download in
  More. One main button that turns into Resume when there is a resume point; Play from start in More. Short quality
  ("Hi-Res"), the details in About.
- **Home**: a search app bar on top (profile avatar and Settings in it); Shuffle all becomes an extended FAB that
  shrinks to its icon on scroll.
- **Lists and cards**: the favorite on rows is a standard icon toggle (outlined / filled), no tonal circle. Cards with
  start-aligned text, no container (centering stays a setting). Artists in a circle; no `MaterialShapes` on pictures.
- **Shell**: the expressive navigation bar on a neutral container, accent only on the active indicator; the mini player
  a neutral floating pill with a thin progress line.
- **Library**: the hub as a list with icons by default (grid stays an option); list headers keep Play and Shuffle,
  the rest goes to the overflow; sort and filter as chips; view, select and search as top bar icons.
- **Icons**: Material Symbols Rounded, outlined by default, filled for the active tab and toggles that are on.
- **Type**: section titles `titleLarge`, metadata `bodyMedium` `onSurfaceVariant`, emphasized styles only for the
  page title and Now Playing.
- **Settings**: the hub only holds settings by topic; accounts (servers, sync) move under the profile avatar; About,
  Changelog and Contact support become one About entry at the end; offline files stay in Downloads. Segmented rows on
  neutral containers, quiet group titles. Every label rewritten to the guideline (list shown to the owner first).
  All settings stay, grouped more clearly, with search in Settings and defaults reviewed.
- **Tabs**: content switches (Downloads, the output page's Settings / Equalizer) become M3 tabs; mode choices
  (Music / Video / Both) stay a connected button group. Downloads: title "Downloads", primary tabs, a quiet summary
  row, Rules move to Settings.
- **Order and review**: the phone before the rest of TV 4; every PR shows before / after screenshots for the owner.
  Adaptive layouts (tablets, foldables) later, in their own milestone.
