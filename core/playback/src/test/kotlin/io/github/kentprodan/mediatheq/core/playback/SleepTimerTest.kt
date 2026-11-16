package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SleepTimerTest {
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext<Context>())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()

    // 2026-11-16 22:00 UTC.
    private var now = Instant.parse("2026-11-16T22:00:00Z")
    private val alarms = mutableListOf<Pair<Instant, () -> Unit>>()
    private val delays = mutableListOf<kotlin.time.Duration>()
    private var stored: String? = null
    private val timer = SleepTimer({ stored }, { stored = it }, now = { now }, zone = { ZoneId.of("UTC") })
    private var detach: (() -> Unit)? = null

    @After
    fun release() {
        detach?.invoke()
        player.release()
    }

    private fun attach() {
        detach =
            timer.attach(player, pauseAtEnd = { player.pauseAtEndOfMediaItems = it }) { delay, block ->
                delays += delay.milliseconds
                val alarm = (now + delay.milliseconds) to block
                alarms += alarm
                ({ alarms.remove(alarm) })
            }
    }

    private fun pass(minutes: Int) {
        now += minutes.minutes
        alarms.filter { it.first <= now }.forEach {
            alarms.remove(it)
            it.second()
        }
    }

    private fun play(count: Int) {
        player.setMediaItems(
            (1..count).map {
                Track(MediaId(ServerId("s"), "t$it"), "T$it", emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!!
            },
        )
        player.prepare()
        player.play()
        advance(player).untilState(Player.STATE_READY)
    }

    @Test
    fun `after two tracks it pauses at the end of the second`() {
        attach()
        play(3)
        timer.start(SleepTimerChoice.Tracks(2))
        assertThat(player.pauseAtEndOfMediaItems).isFalse()

        advance(player).untilPlayWhenReadyIs(false)

        assertThat(player.currentMediaItemIndex).isEqualTo(1)
        assertThat(timer.state.value).isNull()
        assertThat(player.pauseAtEndOfMediaItems).isFalse()
    }

    @Test
    fun `skipping past the last counted track pauses at once`() {
        attach()
        play(3)
        timer.start(SleepTimerChoice.Tracks(1))
        assertThat(player.pauseAtEndOfMediaItems).isTrue()

        player.seekToNextMediaItem()

        assertThat(player.playWhenReady).isFalse()
        assertThat(timer.state.value).isNull()
    }

    @Test
    fun `a minutes timer pauses when it runs out`() {
        attach()
        play(1)
        timer.start(SleepTimerChoice.Minutes(30))
        assertThat(timer.state.value?.endsAt).isEqualTo(now + 30.minutes)

        pass(29)
        assertThat(player.playWhenReady).isTrue()
        pass(1)

        assertThat(player.playWhenReady).isFalse()
        assertThat(timer.state.value).isNull()
    }

    @Test
    fun `a timer that ran out with no player attached is dropped without pausing`() {
        timer.start(SleepTimerChoice.Minutes(5))
        now += 6.minutes
        play(1)

        attach()

        assertThat(timer.state.value).isNull()
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `cancel clears the alarm and the pause at the end`() {
        attach()
        play(2)
        timer.start(SleepTimerChoice.Tracks(1))
        timer.cancel()
        assertThat(player.pauseAtEndOfMediaItems).isFalse()

        timer.start(SleepTimerChoice.Minutes(10))
        timer.cancel()

        assertThat(alarms).isEmpty()
        assertThat(timer.state.value).isNull()
    }

    @Test
    fun `the last choice is remembered`() {
        timer.start(SleepTimerChoice.At(23, 15))

        assertThat(stored).isEqualTo("at:23:15")
        val again = SleepTimer({ stored }, { stored = it })
        assertThat(again.last.value).isEqualTo(SleepTimerChoice.At(23, 15))
    }

    @Test
    fun `choices round-trip and anything else reads as none`() {
        listOf(SleepTimerChoice.Tracks(3), SleepTimerChoice.Minutes(45), SleepTimerChoice.At(0, 5)).forEach {
            assertThat(decodeSleepTimerChoice(encodeSleepTimerChoice(it))).isEqualTo(it)
        }
        listOf("", "tracks", "tracks:0", "minutes:61", "at:24:00", "at:7", "later:1").forEach {
            assertThat(decodeSleepTimerChoice(it)).isNull()
        }
    }

    @Test
    fun `a time is today when still ahead, else tomorrow`() {
        val utc = ZoneId.of("UTC")
        assertThat(nextOccurrence(now, SleepTimerChoice.At(23, 30), utc)).isEqualTo(now + 1.5.hours)
        assertThat(nextOccurrence(now, SleepTimerChoice.At(22, 0), utc)).isEqualTo(now + 24.hours)
        assertThat(nextOccurrence(now, SleepTimerChoice.At(6, 0), utc)).isEqualTo(now + 8.hours)
    }

    @Test
    fun `playing again measures the time left from the clock, not from a delay that slept`() {
        attach()
        play(1)
        player.pause()
        timer.start(SleepTimerChoice.Minutes(30))
        assertThat(delays.last()).isEqualTo(30.minutes)

        // The phone slept 20 minutes while paused: a handler delay would still wait 30.
        now += 20.minutes
        player.play()
        advance(player).untilPendingCommandsAreFullyHandled()

        assertThat(delays.last()).isEqualTo(10.minutes)
    }

    @Test
    fun `a new queue is not a track played`() {
        attach()
        play(3)
        timer.start(SleepTimerChoice.Tracks(2))

        play(2)

        assertThat(timer.state.value?.tracksLeft).isEqualTo(2)
    }

    @Test
    fun `the queue running out first ends a track timer`() {
        attach()
        play(1)
        timer.start(SleepTimerChoice.Tracks(5))

        advance(player).untilState(Player.STATE_ENDED)

        assertThat(timer.state.value).isNull()
    }

    @Test
    fun `the service stopping ends the timer`() {
        attach()
        timer.start(SleepTimerChoice.Minutes(15))

        detach!!.invoke()
        detach = null

        assertThat(timer.state.value).isNull()
        assertThat(alarms).isEmpty()
    }
}
