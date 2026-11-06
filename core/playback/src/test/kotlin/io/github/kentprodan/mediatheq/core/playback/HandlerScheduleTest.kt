package io.github.kentprodan.mediatheq.core.playback

import android.os.Handler
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HandlerScheduleTest {
    private val schedule = handlerSchedule(Handler(Looper.getMainLooper()))

    @Test
    fun `cancel removes the posted block`() {
        var runs = 0
        val cancel = schedule(CLICK_WINDOW_MS) { runs++ }

        cancel()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(CLICK_WINDOW_MS))

        assertThat(runs).isEqualTo(0)
    }

    @Test
    fun `the block runs after the delay`() {
        var runs = 0
        schedule(CLICK_WINDOW_MS) { runs++ }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(CLICK_WINDOW_MS))

        assertThat(runs).isEqualTo(1)
    }
}
