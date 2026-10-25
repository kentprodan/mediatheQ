package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import io.github.kentprodan.mediatheq.core.model.ServerId
import org.junit.Test

class StreamingCacheTest {
    private val itemId = MediaId(ServerId("server-1"), "item:with:colons")

    private fun stream(
        method: PlayMethod,
        session: String = "session-1",
    ) = PlaybackStream(itemId, "http://nas/x?playSessionId=$session", method, "src", session, canSeek = true)

    @Test
    fun `a direct play is cached under the track's original key, whatever the session or url`() {
        assertThat(streamCacheKey(stream(PlayMethod.DirectPlay, "a")))
            .isEqualTo(streamCacheKey(stream(PlayMethod.DirectPlay, "b")))
        assertThat(streamCacheKey(stream(PlayMethod.DirectPlay))).isEqualTo("$itemId:original")
    }

    @Test
    fun `a converted or remuxed stream gets a key of its own session, never the original's`() {
        assertThat(streamCacheKey(stream(PlayMethod.Transcode, "a"))).isEqualTo("transcode:a")
        assertThat(streamCacheKey(stream(PlayMethod.DirectStream, "b"))).isEqualTo("transcode:b")
    }

    @Test
    fun `keys never carry the stream url`() {
        PlayMethod.entries.forEach { assertThat(streamCacheKey(stream(it))).doesNotContain("http") }
    }
}
