package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.run
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.FadeCurve
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class CrossfaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // One clock for both players, as on a device: neither runs ahead of the other.
    private val clock = FakeClock(true)

    private fun exoPlayer(): ExoPlayer =
        TestExoPlayerBuilder(context).setClock(clock).setMediaSourceFactory(FakeMediaSourceFactory()).build()

    // The fake items are 10 s long.
    private val first = exoPlayer()
    private val built = mutableListOf<ExoPlayer>()
    private val swap = SwapPlayer(first)
    private var now = 0L
    private var plan: Crossfader.Plan? =
        Crossfader.Plan(
            lengthMs = 2_000,
            startMs = 7_000,
            introSkipMs = 0,
            outCurve = FadeCurve.Linear,
            inCurve = FadeCurve.Linear,
        )
    private val running = mutableListOf<Boolean>()
    private val crossfader =
        Crossfader(
            swap,
            newPlayer = { exoPlayer().also(built::add) },
            enabled = { true },
            plan = { _, _, _ -> plan },
            // Never run: the tests take each step themselves.
            handler = Handler(idleLooper()),
            now = { now },
            onRunning = running::add,
        )
    private val heard = mutableListOf<String>()

    /** A looper on a thread that never loops: what is posted to it never runs. */
    private fun idleLooper(): Looper {
        var looper: Looper? = null
        Thread {
            Looper.prepare()
            looper = Looper.myLooper()
        }.apply {
            start()
            join()
        }
        return requireNotNull(looper)
    }

    @After
    fun release() {
        crossfader.release()
        swap.active.release()
        (built + first).forEach { it.release() }
    }

    private fun playTwo() {
        swap.player.addListener(
            object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    heard += "left ${oldPosition.mediaItemIndex} at ${oldPosition.positionMs} ($reason)"
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    heard += "now ${mediaItem?.mediaId} ($reason)"
                }
            },
        )
        crossfader.start()
        swap.player.setMediaItems(
            listOf(MediaItem.Builder().setMediaId("a").build(), MediaItem.Builder().setMediaId("b").build()),
        )
        swap.player.prepare()
        swap.player.play()
    }

    /** Plays the first item to [positionMs], prepares the second player and lets it get ready. */
    private fun reach(positionMs: Long) {
        run(first).untilPosition(0, positionMs)
        crossfader.step()
        built.firstOrNull()?.let { run(it).untilState(Player.STATE_READY) }
    }

    @Test
    fun `the next track prepares silently on a second player sharing the audio session`() {
        playTwo()
        reach(2_500)

        val next = built.single()
        assertThat(next.currentMediaItem?.mediaId).isEqualTo("b")
        assertThat(next.volume).isEqualTo(0f)
        assertThat(next.playWhenReady).isFalse()
        assertThat(next.audioSessionId).isEqualTo(first.audioSessionId)
        assertThat(swap.active).isSameInstanceAs(first)
    }

    @Test
    fun `at its start the swap player moves to the next track, heard as one automatic transition`() {
        playTwo()
        reach(2_500)
        run(first).untilPosition(0, 7_100)

        crossfader.step()

        assertThat(swap.active).isSameInstanceAs(built.single())
        assertThat(swap.player.isPlaying || swap.player.playWhenReady).isTrue()
        assertThat(heard)
            .containsExactly(
                // The queue itself, when it was set.
                "now a (${Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED})",
                "left 0 at 10000 (${Player.DISCONTINUITY_REASON_AUTO_TRANSITION})",
                "now b (${Player.MEDIA_ITEM_TRANSITION_REASON_AUTO})",
            ).inOrder()
        assertThat(running).containsExactly(true)
        assertThat(swap.gain).isEqualTo(0f)
    }

    @Test
    fun `the volumes follow the curves, then the tail stops and the gain is whole`() {
        playTwo()
        reach(2_500)
        run(first).untilPosition(0, 7_100)
        crossfader.step()

        now = 1_000
        crossfader.step()
        assertThat(swap.gain).isWithin(0.001f).of(0.5f)
        assertThat(first.volume).isWithin(0.001f).of(0.5f)

        now = 2_000
        crossfader.step()
        assertThat(swap.gain).isEqualTo(1f)
        assertThat(first.mediaItemCount).isEqualTo(0)
        assertThat(running).containsExactly(true, false).inOrder()
        assertThat(crossfader.running).isFalse()
    }

    @Test
    fun `a pause during the crossfade silences the tail at once`() {
        playTwo()
        reach(2_500)
        run(first).untilPosition(0, 7_100)
        crossfader.step()

        swap.player.pause()

        assertThat(crossfader.running).isFalse()
        assertThat(first.mediaItemCount).isEqualTo(0)
        assertThat(swap.gain).isEqualTo(1f)
    }

    @Test
    fun `without a plan nothing prepares and the transition stays plain`() {
        plan = null
        playTwo()
        reach(8_000)

        assertThat(built).isEmpty()
        assertThat(swap.active).isSameInstanceAs(first)
    }

    @Test
    fun `a seek before the start drops the prepared player`() {
        playTwo()
        reach(2_500)

        swap.player.seekTo(1_000)

        assertThat(built.single().mediaItemCount).isEqualTo(0)
        assertThat(swap.active).isSameInstanceAs(first)
    }
}
