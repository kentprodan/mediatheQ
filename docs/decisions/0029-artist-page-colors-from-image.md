# 0029. The artist page takes its colors from the artist image

- Status: Accepted
- Date: 2026-11-05

## Context

ADR 0017 builds a full color scheme from the album cover and applies it only inside the player. Issue #133 asks for the artist page to take its colors from the artist image too, with a setting (Off, From artist image). The owner chose the whole page, not only its header (2026-11-05).

## Decision

- The artist page uses the same pipeline as the player (ADR 0017): `rememberCoverColors` -> `CoverColors` -> `MqCoverTheme` around the page. It covers the top bar, header, sections, and the "Show all" top tracks screen.
- **Seed:** the artist's primary image, else its backdrop. Without either, the page stays in the app colors.
- **Scheme:** `SchemeContent`, or `SchemeVibrant` when the player option is Cover vibrant. "Ignore background and surfaces" and black mode apply as in the player (`imageColorScheme`). The page setting turns the colors on or off on its own, even when the player's cover colors are off.
- **Header gradient:** "Image color" uses the image's `primaryContainer` whatever the dynamic colors setting.
- **Default:** From artist image (the issue's table).

## Consequences

- A second place uses an image-derived theme. ADR 0017's "player subtree only" now reads "player subtree and the artist page".
- Each artist page computes its colors once per image (the cache is per page). That costs one 128 px decode on opening.
- Other pages (album, playlist) keep the app colors until an issue asks for more.
