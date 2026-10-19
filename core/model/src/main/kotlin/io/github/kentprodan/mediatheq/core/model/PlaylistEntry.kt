package io.github.kentprodan.mediatheq.core.model

/** One position in a playlist; [entryId] identifies the position on the server (needed to remove it). */
data class PlaylistEntry(
    val entryId: String,
    val trackId: MediaId,
)
