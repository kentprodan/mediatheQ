package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeSimilarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class RadioStarterTest {
    private val similar = FakeSimilarRepository()
    private val playback = FakePlaybackConnection()
    private val radio = RadioStarter(similar, playback, CoroutineScope(Dispatchers.Unconfined))
    private val seed = MediaId(ServerId("s"), "t1")

    @Test
    fun `the mix replaces the queue`() {
        val track = Track(seed, "Strobe", emptyList(), 10.minutes)
        similar.mixAnswer = MqResult.Success(listOf(track))

        radio.start(seed)

        assertThat(similar.mixSeeds).containsExactly(seed)
        assertThat(playback.plays.single().queue).containsExactly(track)
        assertThat(radio.error.value).isNull()
    }

    @Test
    fun `a failure waits for a message and nothing plays`() {
        similar.mixAnswer = MqResult.Failure(MqError.Network)

        radio.start(seed)

        assertThat(radio.error.value).isEqualTo(MqError.Network)
        assertThat(playback.plays).isEmpty()
        radio.dismissError()
        assertThat(radio.error.value).isNull()
    }

    @Test
    fun `a mix with nothing in the library is not found`() {
        radio.start(seed)

        assertThat(radio.error.value).isEqualTo(MqError.NotFound)
        assertThat(playback.plays).isEmpty()
    }
}
