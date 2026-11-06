package io.github.kentprodan.mediatheq.core.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class QueueRulesTest {
    @Test
    fun `previous rewinds past three seconds only when rewind first is on`() {
        assertThat(previousAction(5.seconds, 2, 5, rewindFirst = true, wrap = false)).isEqualTo(PreviousAction.Rewind)
        assertThat(previousAction(2.seconds, 2, 5, rewindFirst = true, wrap = false)).isEqualTo(PreviousAction.To(1))
        assertThat(previousAction(5.seconds, 2, 5, rewindFirst = false, wrap = false)).isEqualTo(PreviousAction.To(1))
    }

    @Test
    fun `previous on the first entry goes to the last when circular, else rewinds`() {
        assertThat(previousAction(0.seconds, 0, 5, rewindFirst = true, wrap = true)).isEqualTo(PreviousAction.To(4))
        assertThat(previousAction(0.seconds, 0, 5, rewindFirst = true, wrap = false)).isEqualTo(PreviousAction.Rewind)
        assertThat(previousAction(0.seconds, 0, 1, rewindFirst = true, wrap = true)).isEqualTo(PreviousAction.Rewind)
    }

    @Test
    fun `next on the last entry goes to the first when circular, else nowhere`() {
        assertThat(nextPlace(2, 5, wrap = false)).isEqualTo(3)
        assertThat(nextPlace(4, 5, wrap = true)).isEqualTo(0)
        assertThat(nextPlace(4, 5, wrap = false)).isNull()
        assertThat(nextPlace(0, 1, wrap = true)).isNull()
    }

    @Test
    fun `the Play next run counts only the waiting Play next entries right after the current one`() {
        val playedNext = setOf(3, 4, 7)
        // Play order 0 1 2 3 4 5 6 7, current 2: 3 and 4 wait, 7 is further on.
        assertThat(playedNextRun((0..7).toList(), 2, playedNext::contains)).isEqualTo(2)
        assertThat(playedNextRun((0..7).toList(), 5, playedNext::contains)).isEqualTo(0)
        // Shuffled play order: after current 1 come 7 (play next), then 0.
        assertThat(playedNextRun(listOf(5, 1, 7, 0, 3), 1, playedNext::contains)).isEqualTo(1)
    }

    private val now = Instant.parse("2026-11-06T12:00:00Z")

    private fun playedAnHourAgo(entry: Int) = if (entry < 10) now - 1.hours else null

    @Test
    fun `weights fall for recent plays and rise for favorites and ratings`() {
        assertThat(ShuffleWeight().weight(now)).isEqualTo(1.0)
        assertThat(ShuffleWeight(lastPlayedAt = now - 1.hours).weight(now)).isWithin(0.01).of(0.2)
        assertThat(ShuffleWeight(lastPlayedAt = now - 15.days).weight(now)).isWithin(0.01).of(0.6)
        assertThat(ShuffleWeight(lastPlayedAt = now - 60.days).weight(now)).isEqualTo(1.0)
        assertThat(ShuffleWeight(isFavorite = true).weight(now)).isEqualTo(1.5)
        assertThat(ShuffleWeight(halfStars = 10).weight(now)).isWithin(0.001).of(1.25)
        assertThat(ShuffleWeight(halfStars = 1).weight(now)).isWithin(0.001).of(0.75)
    }

    @Test
    fun `a weighted order holds every entry once, the playing one first`() {
        val order = weightedShuffle(List(20) { 1.0 + it % 3 }, first = 7, Random(42))

        assertThat(order.first()).isEqualTo(7)
        assertThat(order).containsExactlyElementsIn(0 until 20)
    }

    @Test
    fun `recently played entries come later on average, and orders vary`() {
        // Entries 0-9 played an hour ago, 10-19 never.
        val weights = (0 until 20).map { ShuffleWeight(lastPlayedAt = playedAnHourAgo(it)).weight(now) }
        val random = Random(7)
        val orders = List(200) { weightedShuffle(weights, first = null, random) }

        fun averagePlace(entries: IntRange) = orders.map { o -> entries.map(o::indexOf).average() }.average()
        assertThat(averagePlace(0..9)).isGreaterThan(averagePlace(10..19) + 3)
        assertThat(orders.distinct().size).isGreaterThan(190)
    }
}
