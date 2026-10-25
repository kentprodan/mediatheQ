package io.github.kentprodan.mediatheq.core.server

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId

/** A track's lyrics from the server. */
interface LyricsSource {
    /** The lyrics of [trackId], `null` when the server has none. */
    suspend fun lyrics(
        server: SignedInServer,
        trackId: MediaId,
    ): MqResult<Lyrics?>
}
