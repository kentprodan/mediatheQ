package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.HeadsetAction
import io.github.kentprodan.mediatheq.core.data.HeadsetPreferences
import org.junit.Test

class ClickCounterTest {
    /** A fake clock: [advance] runs what is due. */
    private var now = 0L
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()

    private fun schedule(
        delay: Long,
        block: () -> Unit,
    ): () -> Unit {
        val entry = (now + delay) to block
        scheduled += entry
        return { scheduled.remove(entry) }
    }

    private fun advance(millis: Long) {
        now += millis
        scheduled.filter { it.first <= now }.forEach {
            scheduled.remove(it)
            it.second()
        }
    }

    private val counts = mutableListOf<Int>()
    private var preferences = HeadsetPreferences()
    private val counter =
        ClickCounter(
            window = { if (preferences.slowClicks) SLOW_CLICK_WINDOW_MS else CLICK_WINDOW_MS },
            schedule = ::schedule,
            final = { preferences.isFinal(it) },
            onClicks = { counts += it },
        )

    @Test
    fun `clicks inside the window count together, a pause ends the count`() {
        counter.click()
        advance(349)
        assertThat(counts).isEmpty()
        advance(1)
        assertThat(counts).containsExactly(1)

        counter.click()
        advance(200)
        counter.click()
        advance(350)
        assertThat(counts).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `the third click acts at once, and a fourth starts over`() {
        repeat(3) {
            counter.click()
            advance(100)
        }
        assertThat(counts).containsExactly(3)

        counter.click()
        advance(400)
        assertThat(counts).containsExactly(3, 1).inOrder()
    }

    @Test
    fun `slow click detection waits longer between clicks`() {
        preferences = preferences.copy(slowClicks = true)
        counter.click()
        advance(500)
        counter.click()
        advance(649)
        assertThat(counts).isEmpty()
        advance(1)
        assertThat(counts).containsExactly(2)
    }

    @Test
    fun `a single click acts at once when double and triple clicks do nothing`() {
        preferences = preferences.copy(doubleClick = HeadsetAction.Nothing, tripleClick = HeadsetAction.Nothing)

        counter.click()

        assertThat(counts).containsExactly(1)
    }

    @Test
    fun `each count maps to its action`() {
        val custom = HeadsetPreferences(HeadsetAction.Next, HeadsetAction.Previous, HeadsetAction.PlayPause)
        assertThat((1..3).map(custom::actionFor))
            .containsExactly(HeadsetAction.Next, HeadsetAction.Previous, HeadsetAction.PlayPause)
            .inOrder()
        assertThat(HeadsetPreferences().isFinal(3)).isTrue()
        assertThat(HeadsetPreferences().isFinal(1)).isFalse()
    }
}
