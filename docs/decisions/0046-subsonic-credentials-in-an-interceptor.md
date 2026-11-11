# 0046. Subsonic credentials: password encrypted like a token, added by an interceptor, URLs stay clean

- Status: Accepted
- Date: 2026-11-11

## Context

The Subsonic API has no header authentication: every request carries `u` and either `p` (plain or `enc:`, "testing
purposes only") or `t = md5(password + s)` with a fresh salt `s`. The OpenSubsonic `apiKeyAuthentication` extension
replaces them with `apiKey`, but it merged into Navidrome on 2026-09-28, after the latest stable 0.64.2. Token auth
needs the password for every request. ADR 0013 stores the Jellyfin token AES-GCM encrypted in the Keystore; ADR 0015
keeps secrets out of image URLs so caches and logs never hold them; the media3 skill forbids secrets in stream URLs
and cache keys. `getCoverArt` and `stream` are authenticated endpoints.

## Decision

- The **password** is the account's secret: `StoredAccount.accessToken` holds it, encrypted as today (ADR 0013). The
  app never sends `p`; it sends `t` + `s` with a new random salt (16 hex characters) per request.
- `SubsonicAuthInterceptor` is the only code that writes credentials. It is registered into the shared OkHttp client
  through a `Set<Interceptor>` multibinding added to `:core:network`'s `NetworkModule` (empty until a provider contributes; no module edge) and acts only on
  requests to `/rest/*` on the host of the signed-in Subsonic account. It appends `u`, `t`, `s`, `v=1.16.1`,
  `c=mediatheQ`, `f=json`: as a **POST form body** when the server lists `formPost`, else as query parameters.
- URLs produced by the provider (`imageUrl`, `PlaybackStream.url`) and handed to Coil, Media3 or the cache carry
  **no** credentials; the interceptor adds them at request time. Coil cache keys, Media3 cache keys and logs stay
  clean. Logging on this provider records method, path and status only, never a query string.
- API keys: when a signed-in server lists `apiKeyAuthentication`, the login screen offers an "API key" field; the key
  is stored in the same field and sent as `apiKey` without `u`. Optional issue, started only after a stable Navidrome
  ships the extension.
- Subsonic error 40 -> `MqError.Unauthorized` (sign out), 41/42 -> `Unauthorized` with the server's `helpUrl`
  message, 43/44 -> `Client`.

## Consequences

- ADR 0013 is amended: the encrypted secret may be a password. A leaked device database still exposes nothing
  readable; the Keystore key never leaves the device.
- ADR 0015's goal (no secret in image URLs or caches) holds; its mechanism gains "or authenticated by an
  interceptor at request time".
- Servers without `formPost` get credentials in query strings over the wire, the protocol's only option; HTTPS or a
  local network is required anyway (ADR 0013).
- Contract tests assert the exact form body / query of every request shape and that no URL returned by the provider
  contains `t`, `s`, `p`, `u` or `apiKey`.
