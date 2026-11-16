package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track

/** Music like something the user picked, as the server suggests it (#379). */
interface SimilarRepository {
    /**
     * A radio from [seed] (a track, album, artist, genre or playlist): the server's mix, kept to the tracks the
     * library has (only downloads in offline mode, ADR 0021); a track seed comes first. Fails without the server.
     */
    suspend fun instantMix(seed: MediaId): MqResult<List<Track>>
}
