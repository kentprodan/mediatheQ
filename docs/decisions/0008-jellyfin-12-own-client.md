# 0008. Jellyfin 12+ only, own thin HTTP client

- Status: Accepted
- Date: 2026-10-18

## Context

Jellyfin 12.0 removed legacy authorization methods and the `/emby/` and `/mediabrowser/` routes, regenerated its OpenAPI document, and changed `GetItems` semantics and error handling (invalid filters return 400). The owner's server runs 12.x (12.1 current). Generated SDKs bring a large API surface and lag behind server releases.

## Decision

- Support Jellyfin server **12.x and newer** only.
- Own thin client in `:provider:jellyfin` (OkHttp + kotlinx.serialization) with only the endpoints we use; official SDKs are reference only.
- Auth via `Authorization: MediaBrowser ...` header; `ApiKey` query parameter only where headers are impossible.
- A copy of the server's OpenAPI document is kept per server version for reference.
- Known server behaviors are listed as R1, R2, ... in the `jellyfin` skill, each with a regression test.

## Consequences

- Users on Jellyfin 10.x cannot use the app.
- Every new endpoint needs a DTO, a mapper and a contract test, which is more work up front but keeps the provider small and fully tested.
