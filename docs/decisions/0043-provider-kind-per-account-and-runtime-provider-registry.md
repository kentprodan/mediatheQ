# 0043. Provider kind per account; providers in a Hilt map; `:core:data` forwards to the active one

- Status: Proposed
- Date: 2026-11-11

## Context

Today `:provider:jellyfin` binds `AuthSource`, `LibrarySource`, `PlaybackSource`, `UserDataSink`, `PlaylistEditor`,
`LyricsSource` and `LiveEvents` directly, and every repository in `:core:data` injects those interfaces. With two
providers the app must pick one per account. Each account has its own database and switching restarts the process
(ADR 0036), so exactly one account, and so one provider, is active per process; onboarding is the only time the
provider is chosen before an account exists.

## Decision

- `:core:model` gets `enum class ProviderKind { Jellyfin, Subsonic }`. `StoredAccount.provider` stores it
  (`provider@<id>` in DataStore; a missing value reads as `Jellyfin`, so no migration).
- `:core:server` gets `MediaServer` (the provider's `kind`, `capabilities` and its seven sources in one object) and
  `ServerCapabilities` (`quickConnect`, `liveEvents`, `syncedLyrics`, `ratingSteps`, `reportsProgress`,
  `spatialAudioInfo`, `seekableConversions`, `maxIdsPerRequest`). Each provider module binds its `MediaServer`
  `@IntoMap` under its `ProviderKind`.
- `:core:data` owns `ProviderRegistry` (the map) and `ActiveProvider`: the `MediaServer` of the active account,
  resolved once per process like the database; during onboarding, the kind the picker chose. `:core:data` binds the
  seven source interfaces to delegates that forward to `ActiveProvider`, so the repositories, `LiveSync`,
  `StreamResolver` and the download code keep their constructors. `SessionRepository.checkServer` / `login` take
  the kind and use the registry directly.
- Features read differences through `ServerCapabilities` exposed by `SessionRepository` (or the repository that
  needs them), never through `ProviderKind` checks in UI code, except the provider name and logo on the accounts list.

## Consequences

- No new module edge: `:app` already depends on everything; providers still see only `:core:server`,
  `:core:network`, `:core:model`, `:core:common`.
- Fakes: `FakeMediaServer` with configurable capabilities in `:core:testing`; the existing `Fake*Source`s stay.
- A third provider (Plex, Servers 2) is one more map entry and one more enum value.
- Behavior that depends on a capability (hidden Quick Connect, no live sync, 5-step ratings, no Atmos note) is
  tested once in `:core:data` / the feature with the fake, not per provider.
