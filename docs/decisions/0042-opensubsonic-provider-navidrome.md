# 0042. Second provider: OpenSubsonic for Navidrome, own thin client

- Status: Proposed
- Date: 2026-11-11

## Context

ADR 0025 orders a second media server (`Servers 1 · Navidrome`) after the TV milestone. #147 added several saved
accounts with one active and a provider picker that lists only providers that exist. Navidrome speaks the Subsonic
API 1.16.1 plus OpenSubsonic extensions; it also has an experimental, off-by-default Jellyfin API. The owner's rule
for Jellyfin (ADR 0008) is an own thin client with recorded fixtures and a regression list, no SDK dependency.

## Decision

- The second provider is `:provider:subsonic`, an implementation of `:core:server` over the **OpenSubsonic API**
  (Subsonic 1.16.1 request shapes, OpenSubsonic fields and extensions discovered with `getOpenSubsonicExtensions`).
- Target: **Navidrome 0.64.2 or newer** (the latest stable at the time of the decision). Other Subsonic servers may
  work but are not tested; a server without `openSubsonic: true` in `ping` is reported as unsupported.
- Own thin client (OkHttp + kotlinx.serialization), `f=json` only, DTOs that never leave the module, pure mappers,
  contract tests against fixtures recorded from a pinned Navidrome Docker image with generated CC0 audio.
- Known server behaviors are listed as R1, R2, ... in a new `subsonic` skill, each with a regression test, as the
  `jellyfin` skill does.
- Navidrome's Jellyfin API is not used: it is experimental and would tie the app to one server's emulation.

## Consequences

- One more module, one more skill, one more fixture tree; CI runs them with the existing tasks.
- Everything Jellyfin-only (Quick Connect, WebSocket live events, PlaybackInfo, Atmos spatial info, 10-step ratings)
  is hidden behind `ServerCapabilities` (ADR 0043) instead of being emulated.
- Spec and server versions are recorded in the skill and re-checked only when the owner upgrades the server.
