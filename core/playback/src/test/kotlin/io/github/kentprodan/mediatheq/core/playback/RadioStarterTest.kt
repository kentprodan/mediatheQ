package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeSimilarRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class RadioStarterTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val similar = FakeSimilarRepository()
    private val playback = FakePlaybackConnection()
    private val radio = RadioStarter(similar, playback, CoroutineScope(Dispatchers.Unconfined))
    private val seed = MediaId(ServerId("s"), "t1")
    private val seedTrack = Track(seed, "Strobe", emptyList(), 10.minutes)
    private val other = Track(MediaId(ServerId("s"), "t2"), "Ghosts", emptyList(), 5.minutes)

    @Test
    fun `the mix replaces the queue, the seed going on where it is`() {
        similar.mixAnswer = MqResult.Success(listOf(seedTrack, other))

        radio.start(seed, startPosition = 2.minutes)

        assertThat(similar.mixSeeds).containsExactly(seed)
        assertThat(playback.plays.single().queue).containsExactly(seedTrack, other).inOrder()
        assertThat(playback.plays.single().startPosition).isEqualTo(2.minutes)
        assertThat(radio.error.value).isNull()
    }

    @Test
    fun `a failure waits for a message and nothing plays`() {
        similar.mixAnswer = MqResult.Failure(MqError.Network)

        radio.start(seed)

        assertThat(radio.error.value).isEqualTo(RadioFailure.NoServer)
        assertThat(playback.plays).isEmpty()
        radio.dismissError()
        assertThat(radio.error.value).isNull()
    }

    @Test
    fun `a sign-out is not a second message`() {
        similar.mixAnswer = MqResult.Failure(MqError.Unauthorized)

        radio.start(seed)

        assertThat(radio.error.value).isNull()
    }

    @Test
    fun `a mix of only the seed, or of nothing, finds nothing similar`() {
        similar.mixAnswer = MqResult.Success(listOf(seedTrack))
        radio.start(seed)
        assertThat(radio.error.value).isEqualTo(RadioFailure.NothingSimilar)

        similar.mixAnswer = MqResult.Success(emptyList())
        radio.start(seed)
        assertThat(radio.error.value).isEqualTo(RadioFailure.NothingSimilar)
        assertThat(playback.plays).isEmpty()
    }

    @Test
    fun `a mix arriving after the user played something else is dropped`() {
        similar.beforeAnswer = {
            playback.state.value = PlayerUiModel(item = NowPlayingItem(other.id, "Ghosts", "", null))
        }
        similar.mixAnswer = MqResult.Success(listOf(seedTrack, other))

        radio.start(seed)

        assertThat(playback.plays).isEmpty()
        assertThat(radio.error.value).isNull()
    }
}
