package io.github.kentprodan.mediatheq.core.server

/**
 * What the server's provider can do (ADR 0043). Only [video] for now (#270); #249 brings the rest and one set per
 * provider kind.
 */
data class ServerCapabilities(
    /** Movies, series and episodes: the video sync steps and the Library's video tiles. */
    val video: Boolean,
)
