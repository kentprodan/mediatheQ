package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.ServerInfo

/** Result of checking a server address the user typed. */
sealed interface ServerCheck {
    /** A server we can log in to, at the normalized [baseUrl]. */
    data class Supported(
        val baseUrl: String,
        val info: ServerInfo,
    ) : ServerCheck

    /** A Jellyfin server older than 12. */
    data class Unsupported(
        val info: ServerInfo,
    ) : ServerCheck

    /** Not a URL we can use. */
    data object InvalidAddress : ServerCheck

    /** `http://` to an address outside the local network (ADR 0013). */
    data object CleartextNotAllowed : ServerCheck

    /** [MqError.Network]: unreachable; [MqError.Unknown]: not a Jellyfin server; others as usual. */
    data class Failed(
        val error: MqError,
    ) : ServerCheck
}
