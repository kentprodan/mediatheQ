# 0044. Subsonic server identity: a generated `ServerId` kept with the account

- Status: Accepted
- Date: 2026-11-11

## Context

Every `MediaId` carries a `ServerId`; on Jellyfin it is the server's own id from `/System/Info/Public`, stable across
addresses, so "Edit address" (LAN vs Tailscale) keeps the account and its library (#147) and the same server and user
signed in again replace the token instead of adding an account. The Subsonic API and Navidrome expose no server id:
`ping` returns `type` and `serverVersion` only.

## Decision

- At the first successful sign-in to a Subsonic server the app generates `ServerId("sub-<uuid>")` and stores it with
  the account. It never changes for that account; editing the address keeps it, so every `MediaId`, download key
  and database row stays valid.
- Duplicate detection at sign-in uses the provider kind, the normalized host (lower-case host name, port, path, no
  scheme) and the user name: a match reuses the saved account and its `ServerId` and replaces the secret; no match is
  a new account with a new id, even when it is in fact the same server reached by another address.
- `ServerId` values of the two providers never collide: Jellyfin ids are 32 hex characters, Subsonic ids start with
  `sub-`.

## Consequences

- The same Navidrome added through LAN and later through Tailscale as two sign-ins is two accounts with two
  libraries; the owner uses "Edit address" instead, as on Jellyfin.
- No server call is needed for identity, which also works for Subsonic servers that are not Navidrome.
- `AuthSession.serverId` is produced by the provider from the connection and the saved accounts, so `AuthSource.login`
  on Subsonic receives the saved `ServerId` to reuse, if any (a small `AuthSource` parameter with a default).
