# 0017. Player colors from the cover with material-color-utilities

- Status: Accepted
- Date: 2026-10-19

## Context

The `expressive-design` skill wants the mini player and Now Playing tinted by the current album cover. The pipeline is "Coil bitmap -> seed color -> full `ColorScheme` -> `MaterialTheme(colorScheme = coverScheme)`", and the exact libraries were left to an ADR. Compose Material 3 has no public API that builds a `ColorScheme` from a seed. The Iris theme (ADR 0012) was generated with Google's material-color-utilities (MCU).

Options checked on 2026-10-19:

- `com.materialkolor:material-color-utilities` 5.0.1 is a pure Kotlin port of MCU: quantizers, `Score`, every `Scheme*` variant, spec 2021 and 2025. It depends only on kotlin-stdlib and poko annotations.
- `com.materialkolor:material-kolor` 5.0.1 adds Compose helpers on top of it, but pulls JetBrains Compose Multiplatform artifacts (`org.jetbrains.compose.material3` 1.12.0-alpha03 and others) next to our AndroidX Compose.
- `androidx.palette` 1.0.0 returns a few swatches, not a tonal scheme, so every Material role would have to be invented by hand.

## Decision

- Use `com.materialkolor:material-color-utilities`, in `:core:designsystem` only.
- **Seed:** a 128 px, software bitmap of the cover from Coil (the same 128 cache bucket as ADR 0015). Its pixels go through `QuantizerCelebi` (128 colors) and then `Score`, which is what Android does for wallpaper colors.
- **Scheme:** `SchemeContent`, spec 2025, platform phone, contrast 0. Content keeps the cover's own hue and chroma; Iris uses Vibrant (ADR 0012) because it starts from a brand seed. Every role is mapped from `MaterialDynamicColors`, light and dark together.
- **Cache:** an in-memory LRU of 32 entries, keyed by the cover (artwork item id + tag). It lives as long as the shell, so a track change on the same album and every recomposition reuse it.
- **Fallback:** Iris when there is no cover or extraction fails. A failure is not cached, so the next track tries again.
- **Changes** between schemes animate with the theme's effects spec: one progress value that blends every role.

## Consequences

- One small pure Kotlin dependency, unit-testable on the JVM.
- Bumps follow the materialkolor release notes. Spec 2025 must stay available.
- A cover-derived theme applies only inside the player subtree. The rest of the app stays Iris (or Material You).
