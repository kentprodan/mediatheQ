# 0002. Phase order: music phone, TV Atmos music, then video

- Status: Superseded by 0025
- Date: 2026-10-18

## Context

The goal is a daily-use replacement for Symfonium first, then the Atmos chain on TV (Google TV Streamer -> TV -> eARC -> LG DSN11RG), which is the main reason for choosing Android. Video is valuable but larger and can wait.

## Decision

| Phase | Scope | Done means |
|---|---|---|
| F0 Foundation | project setup, CI, design system, navigation shell, server login | app builds in CI, logs in to Jellyfin, all design components have screenshot tests |
| F1 Music on phone | Home, Library, Album/Artist/Playlist, player, queue, Search, Downloads | used daily on Pixel 9 instead of Symfonium |
| F2 Music on TV with Atmos | Compose for TV app, music browsing and player, E-AC-3 JOC / TrueHD passthrough | Atmos music arrives untouched at the LG DSN11RG |
| F3 Video | movies, series, subtitles, resume, DV/HDR, frame-rate matching (phone + TV) | a full episode plays correctly on phone and TV |
| F4 Navidrome / Subsonic | second provider | the same tests pass on both providers |
| F5 Auto / Cast / Wear | Android Auto, Cast, Wear OS | - |
| F6 Plex + extras | Plex provider, lyrics extras, recap, party queue, ... | - |

- One GitHub milestone per phase. Issues are vertical, user-visible slices sized for 1-3 sessions.

## Consequences

- No video code (subtitles, DV, mpv) before F3. The model and provider interfaces still keep video in mind (item types, media streams) so F3 does not force a redesign.
- TV work in F2 is music-only, which keeps the first TV milestone small.
