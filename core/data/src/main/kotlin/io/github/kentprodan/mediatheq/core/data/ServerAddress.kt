package io.github.kentprodan.mediatheq.core.data

import java.net.URI
import java.net.URISyntaxException

/**
 * Normalizes what the user typed into a base URL: `nas:8096` -> `http://nas:8096`, trailing slash removed, base path
 * (`/jellyfin`) kept. `null` when it is not an http(s) address with a host.
 */
internal fun normalizeServerAddress(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val uri =
        try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return null
        }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host ?: return null
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val path = uri.rawPath.orEmpty().trimEnd('/')
    return "$scheme://$host$port$path"
}
