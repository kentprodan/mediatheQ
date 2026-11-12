# 0048. Video library in the account database: `series`, `season`, `video`, `video_stream`, synced with the music

- Status: Accepted (owner, 2026-11-12)
- Date: 2026-11-12

## Context

The app is offline-first: screens read Room, the sync writes it (architecture skill section 4), one database per
account (ADR 0036), offline mode filters lists in the repositories (ADR 0021), search is one FTS table. Video adds
movies, series, seasons and episodes with their audio and subtitle streams, resume positions and watched state.
Jellyfin pages every kind through `GET /Items?includeItemTypes=...&recursive=true` like music, keeps video resume
positions on the server (`UserData.PlaybackPositionTicks`, unlike music, R20), and offers `/UserItems/Resume` and
`/Shows/NextUp` for the Home rows.

Options: the same pattern (new tables, same pager and staging swap, same offline parameters, same FTS); one generic
`item` table for every kind (every query grows a `kind` and nullable columns for all kinds); or no local copy with
server reads per screen (breaks offline lists, search and the account-switch rule).

## Decision

- New tables in the account database: `series`, `season`, `video` (movies **and** episodes, with `kind`,
  `series_id`, `season_id`, `season_number`, `episode_number`), `video_stream` (audio and subtitle streams per
  video; the one video stream folds into the `video` row as format columns), each with a `_staging` twin; schema
  11 -> 12 by `AutoMigration` (new tables only) with a migration test and the exported schema.
- The full sync runs the video steps after the music steps with the same pager (500 per page, `fields=
  MediaSources,Overview,Genres,Studios,DateCreated,SortName,Taglines`, `enableImageTypes=Primary,Backdrop,Thumb`),
  into staging, one swap; skipped when `ServerCapabilities.video` is false (Subsonic). The Sync manager shows the
  steps.
- Resume positions and watched state are server data: synced with the items, updated by `UserDataChanged` socket
  messages, written by the playback reports, and refreshed from `/UserItems/Resume` and `/Shows/NextUp` when Home
  opens. No app-side resume table for video.
- Chapters, trickplay information and media segments are not stored; the player reads them per item from the server
  and keeps them for the session (none offline).
- Genres of movies and series are stored as names in a text column in Mobile 3; a video genre page is later.
- Every `VideoDao` list query takes the `all` / `downloaded` parameters of ADR 0021, so offline mode lists only
  downloaded videos. Movies, series and episodes get FTS rows.

## Consequences

- Library size matters as for music: the plan assumes under 10,000 video items; a bigger series library would sync
  episodes per series on demand (a new ADR).
- One `video` table means one DAO, one offline filter and one FTS kind for movies and episodes; the few episode-only
  columns are nullable for movies.
- The Subsonic provider returns `MqError.Client(0, "unsupported")` for the video pages and is never asked, since the
  capability gates the steps and the Library tiles.
- `:app-tv` gets the tables and the sync for free; TV 2 · Video adds the screens.
