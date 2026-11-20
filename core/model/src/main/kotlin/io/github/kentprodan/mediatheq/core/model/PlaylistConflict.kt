package io.github.kentprodan.mediatheq.core.model

import kotlin.time.Instant

/**
 * A playlist edited offline that changed on the server meanwhile (#417): the owner chooses [PlaylistResolution].
 * [server] is what the server changed and [phone] what waits on the phone, both against the version the phone knew
 * when it went offline ([since], the first waiting edit).
 */
data class PlaylistConflict(
    val playlistId: MediaId,
    val name: String,
    val since: Instant?,
    val server: PlaylistChange,
    val phone: PlaylistChange,
)

/** How one version of a playlist differs from another: tracks added and removed, order, name. */
data class PlaylistChange(
    val added: Int = 0,
    val removed: Int = 0,
    val reordered: Boolean = false,
    val renamedTo: String? = null,
)

/** The owner's answer to a [PlaylistConflict]. */
enum class PlaylistResolution {
    /** The phone's edits go on top of the server's version. */
    Merge,

    /** The phone's version wins. */
    Replace,

    /** The phone's edits are dropped. */
    Skip,
}
