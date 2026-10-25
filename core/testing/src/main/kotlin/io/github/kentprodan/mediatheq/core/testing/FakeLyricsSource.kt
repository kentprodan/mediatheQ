package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.server.LyricsSource
import io.github.kentprodan.mediatheq.core.server.SignedInServer

/** [LyricsSource] answering from [lyrics] (missing = none); [failure] makes every call fail. Counts [calls]. */
class FakeLyricsSource : LyricsSource {
    val lyrics = mutableMapOf<MediaId, Lyrics>()
    var failure: MqError? = null
    var calls = 0

    override suspend fun lyrics(
        server: SignedInServer,
        trackId: MediaId,
    ): MqResult<Lyrics?> {
        calls++
        failure?.let { return MqResult.Failure(it) }
        return MqResult.Success(lyrics[trackId])
    }
}
