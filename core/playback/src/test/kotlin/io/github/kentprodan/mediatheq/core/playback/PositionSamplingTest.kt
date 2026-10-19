package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.StubPlayer
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@UnstableApi
class PositionSamplingTest {
    /** A player whose position and playing state the test sets; events are fired by hand. */
    private class ScriptedPlayer : StubPlayer() {
        val listeners = mutableListOf<Player.Listener>()
        var position = 0L
        var playing = false

        override fun addListener(listener: Player.Listener) {
            listeners += listener
        }

        override fun removeListener(listener: Player.Listener) {
            listeners -= listener
        }

        override fun getCurrentPosition() = position

        // BasePlayer derives isPlaying from these three.
        override fun getPlaybackState() = Player.STATE_READY

        override fun getPlayWhenReady() = playing

        override fun getPlaybackSuppressionReason() = Player.PLAYBACK_SUPPRESSION_REASON_NONE

        fun fireEvent() =
            listeners.forEach {
                it.onEvents(
                    this,
                    Player.Events(
                        androidx.media3.common.FlagSet
                            .Builder()
                            .build(),
                    ),
                )
            }
    }

    private val player = ScriptedPlayer()

    @Test
    fun `while playing the position is sampled every tick`() =
        runTest {
            player.playing = true
            player.positions(250.milliseconds).test {
                assertThat(awaitItem()).isEqualTo(0.seconds)
                player.position = 250
                advanceTimeBy(251)
                assertThat(awaitItem()).isEqualTo(250.milliseconds)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `while paused it emits once and then stays quiet`() =
        runTest {
            player.position = 10_000
            player.positions(250.milliseconds).test {
                assertThat(awaitItem()).isEqualTo(10.seconds)
                advanceTimeBy(5_000)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a seek while paused shows the new position at once`() =
        runTest {
            player.positions(250.milliseconds).test {
                assertThat(awaitItem()).isEqualTo(0.seconds)
                player.position = 42_000
                player.fireEvent()
                assertThat(awaitItem()).isEqualTo(42.seconds)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
