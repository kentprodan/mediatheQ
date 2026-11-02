package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SlowStartsTest {
    private val strobe = NowPlayingItem(MediaId(ServerId("s"), "t1"), "Strobe", "deadmau5")
    private val ghosts = NowPlayingItem(MediaId(ServerId("s"), "t2"), "Ghosts", "deadmau5")
    private val state = MutableStateFlow(PlayerUiModel())

    private fun TestScope.slowStarts(): List<NowPlayingItem> {
        val seen = mutableListOf<NowPlayingItem>()
        backgroundScope.launch { state.slowStarts().collect { seen += it } }
        runCurrent()
        return seen
    }

    // Lets the collector see each state: a StateFlow keeps only the latest one.
    private fun TestScope.current(
        item: NowPlayingItem,
        entry: String,
        buffering: Boolean = false,
        ready: Boolean = false,
    ) {
        state.value = PlayerUiModel(item = item, currentEntryId = entry, isBuffering = buffering, isReady = ready)
        runCurrent()
    }

    private fun TestScope.wait(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    @Test
    fun `a start buffering past three seconds is slow, once`() =
        runTest {
            val seen = slowStarts()

            current(strobe, "e1", buffering = true)
            wait(2.9.seconds)
            assertThat(seen).isEmpty()
            wait(0.2.seconds)
            assertThat(seen).containsExactly(strobe)

            wait(10.seconds)
            assertThat(seen).containsExactly(strobe)
        }

    @Test
    fun `a start ready within three seconds is never slow`() =
        runTest {
            val seen = slowStarts()

            current(strobe, "e1", buffering = true)
            wait(1.seconds)
            current(strobe, "e1", ready = true)
            wait(10.seconds)

            assertThat(seen).isEmpty()
        }

    @Test
    fun `rebuffering in the middle of a track is not a slow start`() =
        runTest {
            val seen = slowStarts()

            current(strobe, "e1", buffering = true)
            current(strobe, "e1", ready = true)
            current(strobe, "e1", buffering = true)
            wait(10.seconds)

            assertThat(seen).isEmpty()
        }

    @Test
    fun `each queue entry gets its own start`() =
        runTest {
            val seen = slowStarts()

            current(strobe, "e1", buffering = true)
            wait(1.seconds)
            current(ghosts, "e2", buffering = true)
            wait(3.seconds)

            assertThat(seen).containsExactly(ghosts)
        }

    @Test
    fun `waiting before buffering, while nothing loads, does not count`() =
        runTest {
            val seen = slowStarts()

            current(strobe, "e1")
            wait(10.seconds)

            assertThat(seen).isEmpty()
        }
}
