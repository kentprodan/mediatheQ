package io.github.kentprodan.mediatheq.core.model

/** Someone in a movie or series (#427): an actor with their [role], or the crew ([kind] Director, Writer, ...). */
data class Person(
    val id: MediaId,
    val name: String,
    /** The character for an actor; `null` for the crew. */
    val role: String? = null,
    /** Jellyfin's person type: Actor, Director, Writer, Producer, ... */
    val kind: String = "Actor",
    val artwork: Artwork? = null,
)
