# 0051. App mode: Music, Video or Both, one app-wide setting chosen in onboarding, Settings and Home

- Status: Accepted (owner decision, 2026-11-12)
- Date: 2026-11-12

## Context

Mobile 3 · Video adds movies and series next to the music. The owner wants to use the app as a music app, as a
video app, or as both, and to decide that once in onboarding and change it later: Music must stay exactly today's
app, Video must show no music surface, Both places video in the Library tiles, Home sections, Search groups and
optional navigation entries (plan `docs/plans/mobile3-video.md`, section 6). Accounts are one server each
(ADR 0036); the Subsonic provider has no video (`ServerCapabilities.video = false`, ADR 0043). Backup (#150)
already carries every preference.

Options: a per-account mode (an account is a server, but the phone is used one way); a mode derived from the
server's libraries (cannot hide video on a mixed server); or one app-wide setting.

## Decision

- `AppMode { Music, Video, Both }` is **one app-wide preference** in the settings DataStore (`app_mode`; missing =
  `Music`, so existing installs change nothing). `AppModeRepository` in `:core:data` exposes it as a flow;
  ViewModels read it into their `UiState`, never a screen-level preference; the shell reads it for the navigation
  entries. It is in the backup like every preference.
- **Chosen in onboarding** after the first sign-in (three cards; `Both` preselected when the server has video
  libraries; the step is skipped as `Music` when `capabilities.video` is false), **changed** in Settings ->
  Interface -> App mode and with a segmented toggle at the top of Home. A Subsonic account behaves as `Music`
  whatever the setting, without changing it.
- **What each mode shows.** `Music`: today's app, no video tile, section, group, entry, setting or sync step.
  `Video`: Library hub with Movies and Series tiles only, Home with video sections only, Search with video groups
  only, Downloads with video collections only (downloads of the other kind are kept and counted, not listed),
  navigation entries Home / Library / Search / Downloads by default plus optional Movies / Series, no Now Playing
  overlay, a mini player only while a video plays, the saved music queue kept but not restored at start, music-only
  settings pages hidden (Decoding and transcoding, Transitions, Bluetooth lyrics, Now playing screens, music page
  settings); Output settings, Equalizer, Audio focus and Headset buttons stay (they apply to video audio). `Both`:
  music plus video as placed in the plan.
- **Sync per mode:** the full sync runs the music steps when `mode.music` and the video steps when `mode.video &&
  capabilities.video`; switching the mode on starts a sync for a kind never synced on the active account. Tables
  of the other kind are kept, never deleted by a switch.
- `MainNavEntry.allowed(mode)`, `HomeSections`, `LibraryShortcut` and the Downloads grouping filter in pure
  functions; the Navigation settings page drops disallowed entries and resets to the mode's defaults.

## Consequences

- Every screen that lists media gates on the mode through its `UiState`; a missed gate is a bug caught by the
  per-mode tests of V2 (Home, Library, Search, Downloads, Settings hub in each mode).
- Switching modes costs no restart; switching to a kind never synced costs one sync.
- A single phone with two accounts of different kinds (a music Navidrome and a video Jellyfin) switches mode by
  hand; the setting is not tied to the account.
- Music-only features keep working in `Both` exactly as in `Music`; the music regression checklist runs in both.
