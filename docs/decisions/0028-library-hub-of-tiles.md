# 0028. The Library is a hub of tiles

- Status: Accepted
- Date: 2026-11-05
- Supersedes: 0014

## Context

The Library tab switched its lists (Albums, Artists, Playlists, Songs) with a filter bar in two styles, chips or
connected buttons, chosen by the user (ADR 0014). More lists are coming (Years now, Genres #130, Record labels #131), and
a bar of seven filters no longer fits a phone. The owner chose a hub of tiles instead (2026-10-27), with the tiles, their
order and their look chosen in Settings -> Page settings -> Library page.

## Decision

- The Library tab's root (`LibraryKey`) is a hub of tiles (`LibraryShortcut`). A tile pushes its list on the Library
  tab's back stack (`LibraryListKey`), which has Back; a year of the Years list pushes `YearKey`.
- Every Library list is a screen of its own with one fixed `LibraryFilter`. The lists that are navigation entries of
  their own (#122) keep their keys and have no Back.
- The filter bar, its two styles and their setting are removed, with `MqFilterBar`; the stored style is dropped the next
  time the Library page settings are saved. The album grid's columns stay where they were (the list's view menu).
- Years are grouped in memory from the Library's albums (already only downloads while offline, ADR 0021), like the
  lists' sorting; no new database query.
- Settings -> Library page: tiles (Organize dialog, at least one), Grid or List, columns 2-4 and 3-6 in landscape (capped
  by the font scale so labels never break), spacing, Restore defaults.

## Consequences

- One more tap to reach a list from the Library tab; the lists that matter most can still be navigation entries (#122).
- New lists (Genres, Record labels) add a `LibraryShortcut` and a `LibraryFilter`, and appear in the Organize dialog.
- With the Library tab out of the navigation, the hub and Years cannot be reached; a Years navigation entry would be
  its own issue.
