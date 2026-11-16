package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Lyrics
import kotlin.time.Duration

/** Lyrics from a public service, for tracks the server has none for (#381). Only names leave the device. */
interface OnlineLyricsSource {
    /** The lyrics of [title] by [artist] (from [album], [duration] long); `null` when the service has none. */
    suspend fun lyrics(
        title: String,
        artist: String,
        album: String?,
        duration: Duration,
    ): MqResult<Lyrics?>
}
