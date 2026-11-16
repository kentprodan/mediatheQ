package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.SimilarRepository
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track

class FakeSimilarRepository : SimilarRepository {
    /** What [instantMix] answers; the seeds asked for are in [mixSeeds]. */
    var mixAnswer: MqResult<List<Track>> = MqResult.Success(emptyList())
    val mixSeeds = mutableListOf<MediaId>()

    /** Runs while the request is "on the network", e.g. to change what plays meanwhile. */
    var beforeAnswer: () -> Unit = {}

    override suspend fun instantMix(seed: MediaId): MqResult<List<Track>> {
        mixSeeds += seed
        beforeAnswer()
        return mixAnswer
    }
}
