# 0014. Library filters come in two styles, chosen by the user

- Status: Accepted
- Date: 2026-10-19

## Context

The expressive-design skill asked to pick one control for the Library filters (Albums / Artists / Playlists / Songs): a `FilterChip` row or a connected button group. The owner wants both, with a switch.

## Decision

- `MqFilterBar` in `:core:designsystem` draws either style: a horizontally scrolling `FilterChip` row (check mark on the selected chip) or a row of connected `ToggleButton`s (`ButtonGroupDefaults.connected*ButtonShapes`) that share the width.
- The user switches in the Library top-bar menu; the choice is stored with the other Library preferences.
- Chips are the default because they fit at `fontScale` 2.0; connected buttons shorten long labels at large font sizes.

## Consequences

- Two layouts to keep in screenshot tests (`MqFilterBarScreenshot`, Library screen with each style).
- Other screens that need a single-choice filter reuse `MqFilterBar` and the same preference.
