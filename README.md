# mediatheQ

An Android client for self-hosted media servers, starting with music from Jellyfin 12+.
Material 3 Expressive on phone first, then Dolby Atmos music on Google TV, then video
(see [ADR 0002](docs/decisions/0002-phase-order.md)).

## Build

Requirements:

- Android Studio (stable) with the Android SDK. Platform 37 is downloaded automatically if missing.
- Any JDK 17+ to run Gradle (Android Studio's bundled JBR works). The project compiles with JDK 21,
  which Gradle downloads automatically (toolchain + foojay).

In Android Studio: open the folder and run the `app` configuration.

From a terminal (when `JAVA_HOME` and `ANDROID_HOME` are not set yet, point them to Android Studio's
JBR and your Android SDK, or create `local.properties` with `sdk.dir=...`):

```
./gradlew assembleDebug          # macOS / Linux
.\gradlew.bat assembleDebug      # Windows
./gradlew testDebugUnitTest      # unit tests
```

## Project docs

- Architecture decisions: [`docs/decisions`](docs/decisions/README.md)
- Project rules (Claude Code skills): [`.claude/skills`](.claude/skills)

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
