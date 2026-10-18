# 0012. Iris color scheme: Vibrant variant, spec 2025

- Status: Accepted
- Date: 2026-10-18

## Context

The `expressive-design` skill defines the Iris palette by seed `#5B45D6` (light) with a dark primary of about `#A391FF`, generated with Material Theme Builder, "scheme variant per ADR". Theme Builder and the platform use Material Color Utilities (MCU). Generating every variant from the seed with MCU 0.4.0 gives these primaries:

| Variant | Light primary | Dark primary |
|---|---|---|
| TonalSpot (Theme Builder default), spec 2025 | `#5F598B` | `#C8C1EE` |
| Fidelity / Content | `#4326BE` | `#C7BFFF` |
| Expressive, spec 2025 | `#5E53A8` | `#D0C8FF` |
| **Vibrant, spec 2025** | **`#573CDF`** | **`#AEA3FF`** |

TonalSpot desaturates the seed into a greyish violet; Vibrant keeps the intended Iris color in both modes.

## Decision

- Iris = MCU `SchemeVibrant`, spec version 2025, platform phone, contrast 0, seed `#5B45D6`.
- All color roles are generated together and stored in `:core:designsystem` `theme/Color.kt`; single roles are never hand-edited.
- To regenerate: in an empty folder `npm i @material/material-color-utilities@0.4.0 esbuild`, bundle a script that builds `new SchemeVibrant(Hct.fromInt(argbFromHex('#5B45D6')), isDark, 0, '2025', 'phone')` and prints every `MaterialDynamicColors` role, then replace both schemes in `Color.kt`.

## Consequences

- Colors are more saturated than the Theme Builder default; screenshot references show the exact result.
- Dynamic color (Material You) stays optional and off by default.
