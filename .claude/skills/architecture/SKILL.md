---
name: architecture
description: Architecture rules for mediatheQ - modules, layers, dependency rules, data flow, ViewModels, navigation, DI, playback boundaries, error handling. Use before creating a module, class, screen, repository or service, before adding a dependency between modules, and when unsure where code belongs.
---

# mediatheQ - Architecture

Goal: an app a non-programmer owner can keep extending with AI help for years without it rotting.
That means: small modules with one job, strict one-way dependencies, everything replaceable behind interfaces, everything testable without a device.

Base package placeholder below: `{base}` (decided in ADR 0001).

## 0. Stack (verified 2026-10-18)

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin, JDK 21 | |
| Build | AGP stable (9.4.x), Gradle version catalog, convention plugins in `build-logic` | No AGP previews |
| UI | Compose + Material 3 Expressive (`material3` 1.5.x) | see `expressive-design` |
| Navigation | **Navigation 3** (`androidx.navigation3`, stable 1.2.x) | back stack is plain state we own |
| DI | Hilt (KSP) | |
| Database | **Room 3** (`androidx.room3`, stable 3.0.x) | KSP only, coroutine-only, FTS5 support |
| Settings | DataStore | |
| Network | OkHttp + kotlinx.serialization | own thin client, see `jellyfin` skill |
| Images | Coil 3 | |
| Playback | Media3 (ExoPlayer, session, downloads) | |
| No | Firebase, analytics, crash SaaS, RxJava, LiveData, Gson, Moshi, KAPT, Room 2.x | |

Any change to this table = ADR in `docs/decisions/`.

## 1. Module map

```
:app                     phone app: Application, MainActivity, Hilt graph, NavDisplay host, nav shell
:build-logic             (included build) convention plugins

:core:model              pure Kotlin domain types. Depends on nothing.
:core:common             pure Kotlin: MqResult, MqError, dispatcher qualifiers, Clock, small utils
:core:navigation         NavKey types for every destination (tiny, no UI)
:core:network            OkHttp client factory, interceptors, Json config, connectivity monitor
:core:server             MediaServer provider INTERFACES + capabilities (no implementation)
:provider:jellyfin       Jellyfin implementation of :core:server (DTOs, mappers, auth, websocket)
:core:database           Room 3 database, entities, DAOs, FTS, migrations
:core:datastore          DataStore: settings, server accounts (token stored encrypted)
:core:data               repositories: combine server + database + datastore; offline-first
:core:playback           Media3 MediaLibraryService, player config, queue, session, PlaybackConnection
:core:download           Media3 DownloadManager wrapper, download index -> repository
:core:designsystem       MediatheqTheme + Mq* components (no domain types)
:core:ui                 domain-aware shared composables (AlbumCard, TrackRow) = designsystem + model
:core:testing            fakes, MainDispatcherRule, fixtures, test helpers

:feature:onboarding      server address, login, Quick Connect
:feature:home
:feature:library
:feature:search
:feature:downloads
:feature:album
:feature:artist
:feature:playlist
:feature:nowplaying      full player, queue, lyrics
:feature:settings
```

Later phases add (do not create early): `:provider:subsonic`, `:provider:plex`, `:app-tv` + `:feature-tv:*`, `:auto`, `:wear`.
A module is created in the issue that first needs it, never "for later".

## 2. Dependency rules (enforced, not suggested)

```
:app            -> everything (wiring only, no business logic)
:feature:*      -> :core:model, :core:common, :core:navigation, :core:data, :core:playback (controller API only),
                   :core:designsystem, :core:ui
:core:data      -> :core:model, :core:common, :core:server, :core:database, :core:datastore
:core:playback  -> :core:model, :core:common, :core:data
:core:download  -> :core:model, :core:common, :core:data
:provider:*     -> :core:model, :core:common, :core:server, :core:network
:core:ui        -> :core:model, :core:designsystem
:core:server    -> :core:model, :core:common
:core:navigation -> :core:model (keys carry typed ids, e.g. `AlbumKey(MediaId)`)
:core:database / :core:datastore / :core:network -> :core:model, :core:common
:core:model     -> nothing
```

Forbidden:
- `:feature:*` -> another `:feature:*` (navigate via NavKeys in `:core:navigation`).
- `:feature:*` -> `:provider:*`, `:core:database`, `:core:network` (go through `:core:data`).
- Anything -> `:app`.
- Android framework types in `:core:model`, `:core:common`, `:core:server`.
- Compose in `:core:data`, `:core:playback`, `:provider:*`.

Each convention plugin declares the allowed dependencies for its module type. A new edge in this graph needs an ADR.

## 3. Domain model (`:core:model`)

- Immutable `data class`es / `sealed interface`s. No nullable-everything DTO shapes - mapping happens in the provider.
- IDs are typed and include the server from day one: `MediaId(serverId: ServerId, itemId: String)`. Never pass raw strings across modules.
- Durations as `kotlin.time.Duration`, timestamps as `kotlin.time.Instant` (or `kotlinx-datetime`, per ADR). No `Long` millis in public APIs.
- Audio facts the app needs are first-class: `AudioFormat` (codec, sampleRate, bitDepth, channels, `isSpatial`/`SpatialFormat.DolbyAtmos`), `ReplayGain`.

## 4. Data layer (`:core:server`, `:provider:*`, `:core:data`)

### Provider interface

- `:core:server` defines `MediaServer` (and focused sub-interfaces: `LibrarySource`, `PlaybackSource`, `UserDataSink`, `PlaylistEditor`, `LyricsSource`, `LiveEvents`) plus `ServerCapabilities` (e.g. `supportsSyncedLyrics`, `supportsSpatialAudioInfo`, `maxIdsPerRequest`).
- Providers return domain types only. DTOs never leave `:provider:*`.
- Provider-specific quirks (chunking, auth, retries) live in the provider, documented in that provider's skill.

### Repositories (offline-first)

- UI reads from **Room** via `Flow`. The network only writes into Room. Library size (< 10k tracks) allows a full local copy.
- Sync = provider pages -> mapped -> upserted in a transaction. Live updates (WebSocket events): user data is written at once, library changes start a full sync (`LiveSync`, #18).
- User actions (favorite, rating, playlist edit): optimistic write to Room, then server call; on failure revert and surface an error.
- Repositories are interfaces in `:core:data` (`AlbumRepository`, ...) with one default implementation and one `Fake*` in `:core:testing`. The default implementation lives in `:core:data`, except when it needs a module `:core:data` cannot see: `DefaultDownloadRepository` lives in `:core:download` (Media3) and is bound there.
- Two modules that must share an object but may not depend on each other meet in the Hilt graph through a qualifier in `:core:common` (`@DownloadCache`, `@DownloadSource`), not through a new module edge.

### Results and errors

- One-shot suspend functions return `MqResult<T>` = `Success(value)` | `Failure(error: MqError)`.
- `MqError` is a closed set: `Network`, `Unauthorized`, `Forbidden`, `NotFound`, `Server(code)`, `Client(code, detail)`, `Storage`, `Unknown(cause)`.
- Flows emit data only; errors from background sync go to a `SyncStatus` flow, not thrown into UI flows.
- Never swallow exceptions silently. Never catch `CancellationException`.

## 5. UI layer (`:feature:*`)

Each screen = 4 files:

```
AlbumViewModel.kt      @HiltViewModel, exposes StateFlow<AlbumUiState>, fun onAction(AlbumAction)
AlbumUiState.kt        sealed interface: Loading | Content(...) | Empty | Error(MqError)
AlbumScreen.kt         AlbumRoute(viewModel) collects state  ->  AlbumScreen(state, onAction) stateless
AlbumEntry.kt          Navigation 3 entry registration for AlbumKey
```

- `UiState` built with `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)`.
- Collect with `collectAsStateWithLifecycle()`.
- Actions are a `sealed interface XAction`; one `onAction` entry point (easy to test, easy to log).
- One-off effects (snackbar, navigate after save) are modeled as state the UI consumes and acknowledges, not as hot channels.
- Stateless `XScreen` takes only `UiState` + lambdas -> previews and screenshot tests work without Hilt.
- No business logic in composables. Formatting helpers (durations, counts) live in `:core:ui`.

## 6. Navigation (Navigation 3)

- Every destination is a `@Serializable` `NavKey` in `:core:navigation` (e.g. `data class AlbumKey(val id: MediaId)`).
- `:app` owns the back stack(s): one back stack per bottom tab (Home, Library, Search, Downloads), switching tabs swaps stacks and preserves them.
- Each feature exposes an `EntryProviderScope` extension (`fun EntryProviderScope<NavKey>.albumEntry(navigator: Navigator)`) registered in `:app`.
- Features navigate through a small `Navigator` interface (`navigateTo(key)`, `back()`), never by touching the back stack list.
- ViewModels are scoped to their nav entry (use the Navigation 3 ViewModel entry decorator); arguments come from the key via Hilt assisted injection. Verify the exact artifact/decorator names against current docs when implementing.
- Now Playing is not a destination on the tab stacks; it is an overlay owned by the shell (mini player <-> full player).

## 7. Playback boundary (`:core:playback`)

- The player lives only in `MediaLibraryService` (needed later for Android Auto, Wear, Cast). UI never creates or touches `ExoPlayer`.
- UI talks to playback through `PlaybackConnection` (wraps an async `MediaController`) exposing:
  - `state: StateFlow<PlayerUiModel>` (current item, position, isPlaying, queue snapshot, output format incl. Atmos flag)
  - commands: `play(queue, startIndex)`, `pause()`, `seekTo()`, `skip()`, `setShuffle()`, ...
- Position updates are sampled (e.g. 4 Hz) inside `PlaybackConnection`, not polled by composables.
- Stream URL resolution, transcoding choice and Atmos preference are decided in `:core:playback` via `:core:data` + `PlaybackSource`, unit-tested without a device.
- Playback progress reporting to the server happens in the service, not the UI.

## 8. DI (Hilt)

- Bindings live in the module that owns the implementation (`@Module @InstallIn(SingletonComponent::class)` in `:core:data`, `:provider:jellyfin`, ...).
- Dispatchers injected via qualifiers `@IoDispatcher`, `@DefaultDispatcher`; app-wide work in `@ApplicationScope CoroutineScope`.
- No `object` singletons holding state. No service locators.

## 9. Threading and performance

- Main thread: UI only. DB and network on injected dispatchers.
- Lists use stable keys (`MediaId`) and immutable/stable models (Compose stability: prefer `ImmutableList` from kotlinx.collections.immutable or `@Immutable` wrappers).
- Images: always request sized images from the server (thumbnail sizes), never full-size covers in lists.
- Baseline profile for startup and library scroll (later issue, before first daily-use milestone).

## 10. Security and privacy

- Access tokens never in logs, crash output, screenshots, or test fixtures.
- Tokens stored encrypted with an Android Keystore AES-GCM key (ADR 0013), not in plain DataStore. App backup is off.
- Cleartext HTTP only to LAN/Tailscale IPs, enforced on every connection by `CleartextPolicyInterceptor` in `:core:network` (ADR 0013; network security config cannot express IP ranges); HTTPS otherwise. Every HTTP stack (OkHttp, Media3) must go through it.
- No telemetry. Logs stay on device.

## 11. Conventions

- Kotlin official code style, enforced by ktlint (Spotless) in CI.
- File = one public top-level type (plus its private helpers).
- Names: `XRepository` / `DefaultXRepository` / `FakeXRepository`; `XDao`, `XEntity`, `XDto`; `XViewModel`, `XUiState`, `XAction`, `XScreen`, `XRoute`.
- Public functions of `:core:*` modules get KDoc (one line is fine).
- Code, comments, commits, issues, ADRs in English.
- The old project on the Mac (`~/Documents/mediatheQ`) is **reference only**. Read it for lessons; never copy code.

## 12. When unsure where code goes

1. Is it a domain type? -> `:core:model`
2. Does it talk HTTP to a specific server? -> `:provider:<server>`
3. Does it persist or combine data for the UI? -> `:core:data` (+ `:core:database` / `:core:datastore`)
4. Does it control audio/video? -> `:core:playback`
5. Is it reusable UI without domain types? -> `:core:designsystem`; with domain types -> `:core:ui`
6. Is it one screen's logic or layout? -> that `:feature:*`
7. Still unsure -> ask with AskUserQuestion, recommended option first, before writing code.

## 13. Done checklist

- [ ] No new forbidden module edge; new edges have an ADR.
- [ ] No DTO or Android type leaked into `:core:model` / `:core:server`.
- [ ] New repository/provider interface has a `Fake*` in `:core:testing`.
- [ ] Screen follows the 4-file pattern; `XScreen` is stateless.
- [ ] Errors mapped to `MqError`; no swallowed exceptions.
- [ ] Tests per the `testing` skill.
