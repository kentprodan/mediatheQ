# 0026. App fonts are downloadable Google Fonts

- Status: Accepted
- Date: 2026-10-28

## Context

Settings -> Theme, font and colors (#121) lets the user pick the app font: Inter, Lato, Nunito, Roboto, Roboto Slab,
Lexend Deca, Bitter / Rubik (headings / body), Bebas Neue and Castoro Titling (headings only). The `expressive-design`
skill said "System font. No custom font files". Bundling ten font families would add several MB to the APK for a
choice most users never change.

## Decision

- Fonts are downloadable Google Fonts (`androidx.compose.ui:ui-text-google-fonts`, version from the Compose BOM), with
  the Play services font provider (`com.google.android.gms.fonts`) and its certificates in `:core:designsystem`
  `res/values/font_certs.xml` (from the Android compose-samples).
- Requested weights: 400, 500 and 700; a font without one gets the nearest. The system caches downloaded fonts.
- Until a font is fetched (first use, offline, no Play services) the system font is used, with no error.
- Display fonts (Bebas Neue, Castoro Titling) apply to display, headline and title styles only; body and labels stay
  the system font. Bitter / Rubik: Bitter for headings, Rubik for the rest.
- The type scale is built in one place, `mediatheqTypography(font)`; screens never set a font.

## Consequences

- No font files in the APK; the first use of a font needs a network.
- Screenshot tests cannot load downloadable fonts (layoutlib): they show the system font.
- A device without Play services always shows the system font.
