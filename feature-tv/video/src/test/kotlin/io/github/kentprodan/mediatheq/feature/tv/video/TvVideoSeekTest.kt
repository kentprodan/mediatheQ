package io.github.kentprodan.mediatheq.feature.tv.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.resumeStart
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TvVideoSeekTest {
    @Test
    fun `a press moves 10 s back or 30 s forward, inside the video`() {
        assertThat(
            videoSeekTarget(5.minutes, 100.minutes, forward = true, repeat = 0),
        ).isEqualTo(5.minutes + 30.seconds)
        assertThat(
            videoSeekTarget(5.minutes, 100.minutes, forward = false, repeat = 0),
        ).isEqualTo(5.minutes - 10.seconds)
        assertThat(videoSeekTarget(5.seconds, 100.minutes, forward = false, repeat = 0)).isEqualTo(Duration.ZERO)
        assertThat(
            videoSeekTarget(99.minutes + 50.seconds, 100.minutes, forward = true, repeat = 0),
        ).isEqualTo(100.minutes)
    }

    @Test
    fun `held, the step grows every 3 repeats up to a ceiling set by the runtime`() {
        // 20 min episode: at most x2.
        assertThat(videoSeekTarget(Duration.ZERO, 20.minutes, true, repeat = 30)).isEqualTo(60.seconds)
        // 2 h film: x2 after 3 repeats, at most x6.
        assertThat(videoSeekTarget(Duration.ZERO, 120.minutes, true, repeat = 3)).isEqualTo(60.seconds)
        assertThat(videoSeekTarget(Duration.ZERO, 120.minutes, true, repeat = 60)).isEqualTo(180.seconds)
        // 3 h film: up to x10.
        assertThat(videoSeekTarget(Duration.ZERO, 180.minutes, true, repeat = 60)).isEqualTo(300.seconds)
    }

    @Test
    fun `resume starts 10 s early, never before the start`() {
        assertThat(resumeStart(30.minutes)).isEqualTo(30.minutes - 10.seconds)
        assertThat(resumeStart(4.seconds)).isEqualTo(Duration.ZERO)
    }
}
