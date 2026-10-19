# 0010. SDK levels: minSdk 31, targetSdk 37, compileSdk 37

- Status: Accepted
- Date: 2026-10-18

## Context

The app targets the owner's devices: Pixel 9 (phone) and Google TV Streamer (API 34). AGP 9.4 supports compileSdk up to 37 (Android 17, stable). A lower minSdk means more compatibility branches; Android 12 (API 31) brings dynamic color and the splash screen API that Material 3 Expressive relies on.

## Decision

- `compileSdk = 37`, `targetSdk = 37` (the highest AGP 9.4 allows).
- `minSdk = 31` (Android 12): covers both owner devices with room to spare, no pre-12 code paths.
- The values live in `gradle/libs.versions.toml` (`compileSdk`, `targetSdk`, `minSdk`) and are applied by the convention plugins.

## Consequences

- Devices older than Android 12 cannot install the app.
- Raising targetSdk/compileSdk happens together with an AGP upgrade, in its own issue.
