# 0052. R8 release builds and a daily build type

- Status: Accepted
- Date: 2026-11-16
- Amends: 0023

## Context

The code review of 2026-11-16 (#398) found release builds of both apps without R8. Compose relies on R8 to inline and
remove its debug paths; the apps were also installed as debug builds every day, which Compose runs several times
slower. There is no release signing key yet.

## Decision

- `release` of `:app` and `:app-tv` runs R8 with resource shrinking (`proguard-android-optimize.txt` plus the root
  `proguard-rules.pro`), set once in the application convention plugin. Names are kept (`-dontobfuscate`): the app's
  log (#169) and crashes must name real classes, and the code is open source anyway.
- A `daily` build type in both apps: release, signed with the debug key, with no id suffix. It installs over a debug
  build and keeps its sign-in, library and downloads. It is the build to use every day.
- `:app`'s `daily` ships the generated baseline profile of `release` (ADR 0023); `nonMinifiedRelease` stays without
  R8 so the profile is generated on untouched code.
- CI assembles `:app:assembleDaily` and `:app-tv:assembleDaily` (and `benchmarkRelease`, now minified), so an R8
  problem fails the PR.

## Consequences

- APK sizes on 2026-11-16: phone 70 MB (debug) -> 15 MB (daily), TV 61 MB -> 12 MB.
- A class reached only by reflection needs a keep rule in `proguard-rules.pro`; none is needed today.
- `daily` is not debuggable: a debugger or layout inspector needs the debug build.
