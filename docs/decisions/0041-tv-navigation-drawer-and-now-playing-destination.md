# 0041. TV navigation: one Navigation 3 back stack, a drawer, Now Playing as a destination

- Status: Accepted
- Date: 2026-11-11

## Context

The phone app (ADR 0005, architecture skill section 6) keeps one back stack per bottom tab and shows Now Playing as an
overlay owned by the shell (mini player <-> full player). A TV has no bottom bar and no gesture: the D-pad moves focus,
Back pops, and Google's TV guidance uses a side navigation drawer (`NavigationDrawer` in tv-material 1.x). A floating
mini player steals focus on a 10-foot UI and has no place to be "dragged" open.

## Decision

- **One back stack** in `:app-tv`, Navigation 3 (`NavDisplay`, the same `NavKey`s from `:core:navigation`, the same
  `Navigator` interface). No per-tab stacks: the drawer entries (Home, Library, Search, Now playing, Settings) replace
  the top of the stack; detail pages (album, artist, playlist, genre) push.
- **Drawer:** tv-material `NavigationDrawer` on the left, collapsed to icons until focused; its footer shows a compact
  now-playing strip (cover, title, play state) when something is queued; selecting the strip or the "Now playing"
  entry opens the full screen. Back from a top-level entry other than Home goes to Home; Back on Home leaves the app.
- **Now Playing is a destination** (`NowPlayingKey`, already a `NavKey`), not an overlay. Starting playback from an
  album or a row does not open it by itself (a setting can come later). The remote's play/pause keys reach the session
  through the platform media button path as on the phone.
- **Focus rules:** every list and grid wraps in `focusRestorer()`; each screen requests initial focus once; focus
  never lands on a non-interactive element; the drawer keeps the last selected entry.
- The TV app registers the same `EntryProviderScope` extensions pattern (`fun EntryProviderScope<NavKey>.tvAlbumEntry
  (navigator)`), one per TV feature.

## Consequences

- The Now Playing card on the launcher (session activity = `TvActivity`) opens the app at its last screen; the drawer
  strip is one D-pad move away. If daily use shows the full player should open on play, that is a setting in a later
  issue.
- `:core:navigation` keys are shared by both apps; a key a TV screen does not implement is simply not registered on
  TV (the TV `Navigator` ignores it with a log line, never a crash).

## Amendment (2026-11-13, #235)

- `focusRestorer()` alone does not bring focus back after a detail page: the screen below leaves the composition and
  its restorer forgets the child. `FocusMemory` (`:core:designsystem-tv`) keeps the focused item's key in the screen's
  saved state; the screen calls `restore(first)` once when it opens.
- The strip shows the play state, the title and (with the drawer open) the artist; the cover came with Now Playing
  (#240). Image loading for the TV app came with Home (#236): the same `ArtworkInterceptor` (now in `:core:data`).
- `NowPlayingKey` is new in `:core:navigation`; the phone keeps its overlay.
- A key with no TV screen is pushed like any detail page and shows a placeholder; the shell logs it once when it
  shows (not the `Navigator`). The drawer sends focus entering it to the selected entry (`focusProperties.onEnter`).
- Back on Home is not handled by the shell (Navigation 3 has nothing to pop), so the activity's default Back leaves.

## Amendment (2026-11-13, #240)

- The launcher's Now Playing card opens the full player, as #240 asks (this replaces "opens the app at its last
  screen" above): the playback service's session activity carries `EXTRA_OPEN_PLAYER` (`:core:playback`), `TvActivity`
  is single task and pushes `NowPlayingKey` on it; the phone ignores the extra.
