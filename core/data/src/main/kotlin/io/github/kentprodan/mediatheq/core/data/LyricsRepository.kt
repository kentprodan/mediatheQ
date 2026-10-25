package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId

/** Lyrics of a track, read from the server each time (not stored). */
interface LyricsRepository {
    /** The lyrics of [trackId], `null` when it has none. A 401 signs out. */
    suspend fun lyrics(trackId: MediaId): MqResult<Lyrics?>
}
