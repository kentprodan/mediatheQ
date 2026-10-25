package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.LyricsRepository
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import kotlinx.coroutines.CompletableDeferred

/**
 * [LyricsRepository] for ViewModel tests: answers from [lyrics] (missing = none), or [failure] when set. [gate], when
 * set, holds every call until it completes. Records asked ids in [calls].
 */
class FakeLyricsRepository : LyricsRepository {
    val lyrics = mutableMapOf<MediaId, Lyrics>()
    var failure: MqError? = null
    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<MediaId>()

    override suspend fun lyrics(trackId: MediaId): MqResult<Lyrics?> {
        calls += trackId
        gate?.await()
        failure?.let { return MqResult.Failure(it) }
        return MqResult.Success(lyrics[trackId])
    }
}
