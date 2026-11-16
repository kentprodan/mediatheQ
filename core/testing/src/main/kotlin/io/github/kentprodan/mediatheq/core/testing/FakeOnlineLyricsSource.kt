package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.server.OnlineLyricsSource
import kotlin.time.Duration

class FakeOnlineLyricsSource : OnlineLyricsSource {
    /** What every request answers; the titles asked for are in [asked]. */
    var answer: MqResult<Lyrics?> = MqResult.Success(null)
    val asked = mutableListOf<String>()

    override suspend fun lyrics(
        title: String,
        artist: String,
        album: String?,
        duration: Duration,
    ): MqResult<Lyrics?> {
        asked += "$title|$artist|$album"
        return answer
    }
}
