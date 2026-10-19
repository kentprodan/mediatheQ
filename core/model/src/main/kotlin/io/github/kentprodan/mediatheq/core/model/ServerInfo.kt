package io.github.kentprodan.mediatheq.core.model

/** Public facts about a media server, known before logging in. */
data class ServerInfo(
    val id: ServerId,
    val name: String,
    val version: String,
) {
    /** Major version from [version] ("12.2.0" -> 12), or `null` when it cannot be read. */
    val majorVersion: Int?
        get() = version.substringBefore('.').toIntOrNull()

    /** Jellyfin 12 or newer (ADR 0008). */
    val isSupported: Boolean
        get() = (majorVersion ?: 0) >= MIN_SUPPORTED_MAJOR_VERSION

    companion object {
        const val MIN_SUPPORTED_MAJOR_VERSION = 12
    }
}
