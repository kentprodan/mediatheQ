package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OffloadGuardTest {
    private var time = 0L
    private var pos = 0L
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    private val broken = mutableListOf<Boolean>()
    private val guard =
        OffloadGuard(
            now = { time },
            schedule = { delay, block ->
                val entry = (time + delay) to block
                scheduled += entry
                ({ scheduled.remove(entry) })
            },
            position = { pos },
            onBroken = { broken += it },
        )

    private fun advance(ms: Long) {
        time += ms
        scheduled.filter { it.first <= time }.forEach {
            scheduled.remove(it)
            it.second()
        }
    }

    @Test
    fun `an offloaded track frozen 1,5 s after play is a stall, remembered for the build (#628)`() {
        guard.onAudioTrackOpened(offload = true)
        guard.onIsPlayingChanged(true)
        advance(1_500)

        assertThat(broken).containsExactly(true)
    }

    @Test
    fun `an offloaded track that moves is fine, and a PCM track is never judged (#628)`() {
        guard.onAudioTrackOpened(offload = true)
        guard.onIsPlayingChanged(true)
        pos = 1_400
        advance(1_500)
        guard.onAudioTrackOpened(offload = false)
        guard.onIsPlayingChanged(false)
        guard.onIsPlayingChanged(true)
        advance(2_000)

        assertThat(broken).isEmpty()
    }

    @Test
    fun `buffering right after an offloaded play is a HAL reset, for this session only (#628)`() {
        guard.onAudioTrackOpened(offload = true)
        guard.onIsPlayingChanged(true)
        pos = 300
        advance(300)
        guard.onBuffering()

        assertThat(broken).containsExactly(false)
    }

    @Test
    fun `buffering after a seek or a transition, or later on, is expected (#628)`() {
        guard.onAudioTrackOpened(offload = true)
        guard.onExpectedBuffering()
        guard.onIsPlayingChanged(true)
        advance(300)
        guard.onBuffering()
        pos = 2_000
        advance(1_500)
        guard.onBuffering()

        assertThat(broken).isEmpty()
    }

    @Test
    fun `once broken it says so only once (#628)`() {
        guard.onAudioTrackOpened(offload = true)
        guard.onIsPlayingChanged(true)
        advance(100)
        guard.onBuffering()
        guard.onIsPlayingChanged(true)
        advance(1_600)

        assertThat(broken).containsExactly(false)
    }
}
