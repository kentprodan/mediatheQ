# 0053. Several addresses per server, chosen by the HTTP client

- Status: Accepted
- Date: 2026-11-16

## Context

The owner reaches the same Jellyfin server on the home LAN and, away from home, over Tailscale (#382). One address per
account means either slow streaming at home (always Tailscale) or editing the address on every trip.

## Decision

- An account keeps its address plus other addresses of the same server (`alternate_urls@<id>` in the accounts file,
  apart from `save`, so a sign-in or an address edit keeps them). Settings -> Manage media providers -> Edit address has
  an "Other addresses" field, one per line, http(s) only, not checked against the server.
- `AddressFailover` (`:core:network`), an application interceptor on the app's one OkHttp client, sends every request
  for any address of the active account to the active one: API, streams (Media3 uses the same client), covers (Coil)
  and the socket follow without knowing. Cache keys keep the URLs the app built, so caches survive a switch.
- The addresses are probed together (4 s, any HTTP answer counts, provider-neutral) when they or the network change
  (online or metered: Wi-Fi to mobile data counts), and while a fallback is active, to come back to the preferred one
  (the account's address, first): after 30 s, then twice as long each time up to 15 minutes, so a day away costs little
  battery. The current address stays until a probe decides.
- A request that cannot connect tries the other addresses at once: any I/O failure for GET and HEAD, only a connection
  that never opened (refused, unknown host, no route, connect timeout) for other methods, so nothing is sent twice. The cleartext rule (ADR 0013) still applies to every
  address.
- `ServerAddressSource` lives in `:core:common`: `:core:data` implements it from `AccountStore`, `:core:network` reads
  it, no new module edge.

## Consequences

- The other addresses are not in a settings backup yet (they are account data, like the address itself).
- Until the first probe after start, requests go to the preferred address; a connection failure moves them at once.
