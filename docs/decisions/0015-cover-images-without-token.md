# 0015. Cover images load without the access token

- Status: Accepted
- Date: 2026-10-19

## Context

The jellyfin skill said images must carry the `Authorization` header. Image loading (Coil) runs outside our request code, so sending the header would mean exposing the token synchronously to the image pipeline, and the token would travel with every one of thousands of cover requests. The Jellyfin 12.2.0 OpenAPI declares no security on `GET /Items/{itemId}/Images/{imageType}`, and the owner's server returns covers without authentication.

## Decision

- Covers are requested without a token: `/Items/{itemId}/Images/Primary?tag=<tag>&fillWidth=<w>&quality=90`.
- They still go through the app's OkHttp client (`OkHttpNetworkFetcherFactory` with the injected client, Coil's service loader off), so the local-network cleartext rule (ADR 0013) applies to every image.
- Widths are rounded up to 128/256/512/1024 so grid sizes and list rows share cached files.

## Consequences

- If a future Jellyfin version protects images, covers fail visibly (placeholder stays) and this decision is revisited: add an OkHttp interceptor for image hosts that sets the header.
- Update 2026-10-28 (#154): covers of downloaded albums are also kept as files on the device (one 1024 px image per album, fetched once through `LibrarySource.image`, still without a token) and preferred over the server URL, so they show offline.
- The image URL is built by the provider (`LibrarySource.imageUrl`) and resolved in `:core:data` (`ArtworkUrlResolver`); screens only pass `Artwork`.
