package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class SwapPlayerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun exoPlayer(): ExoPlayer =
        TestExoPlayerBuilder(context).setMediaSourceFactory(FakeMediaSourceFactory()).build()

    private val a = exoPlayer()
    private val b = exoPlayer()
    private val swap = SwapPlayer(a)
    private val heard = mutableListOf<String>()
    private val listener =
        object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                heard += "repeat $repeatMode"
            }
        }

    @After
    fun release() {
        a.release()
        b.release()
    }

    @Test
    fun `calls reach the active player`() {
        swap.player.setMediaItems(listOf(MediaItem.fromUri("a"), MediaItem.fromUri("b")))

        assertThat(a.mediaItemCount).isEqualTo(2)
        assertThat(swap.player.mediaItemCount).isEqualTo(2)

        swap.swapTo(b)
        assertThat(swap.player.mediaItemCount).isEqualTo(0)
    }

    @Test
    fun `listeners hear only the active player, before and after a swap`() {
        swap.player.addListener(listener)

        a.repeatMode = Player.REPEAT_MODE_ALL
        swap.swapTo(b)
        a.repeatMode = Player.REPEAT_MODE_ONE
        b.repeatMode = Player.REPEAT_MODE_ONE

        assertThat(
            heard,
        ).containsExactly("repeat ${Player.REPEAT_MODE_ALL}", "repeat ${Player.REPEAT_MODE_ONE}").inOrder()
        assertThat(swap.listeners()).containsExactly(listener)
    }

    @Test
    fun `a removed listener hears nothing, also after a swap`() {
        swap.player.addListener(listener)
        swap.player.removeListener(listener)
        swap.swapTo(b)

        b.repeatMode = Player.REPEAT_MODE_ALL

        assertThat(heard).isEmpty()
        assertThat(swap.listeners()).isEmpty()
    }

    @Test
    fun `analytics listeners move too`() {
        var events = 0
        val analytics =
            object : AnalyticsListener {
                override fun onRepeatModeChanged(
                    eventTime: AnalyticsListener.EventTime,
                    repeatMode: Int,
                ) {
                    events++
                }
            }
        swap.player.addAnalyticsListener(analytics)
        swap.swapTo(b)

        a.repeatMode = Player.REPEAT_MODE_ALL
        b.repeatMode = Player.REPEAT_MODE_ALL

        assertThat(events).isEqualTo(1)
    }

    @Test
    fun `the volume is the target times the crossfade gain, and the new player takes it`() {
        swap.player.volume = 0.5f
        swap.gain = 0.4f

        assertThat(swap.player.volume).isEqualTo(0.5f)
        assertThat(a.volume).isWithin(0.0001f).of(0.2f)

        swap.gain = 1f
        swap.swapTo(b)
        assertThat(b.volume).isEqualTo(0.5f)
    }

    @Test
    fun `the swap player is itself, not the active player`() {
        assertThat(swap.player).isEqualTo(swap.player)
        assertThat(swap.player).isNotEqualTo(a)
        assertThat(swap.player.hashCode()).isEqualTo(System.identityHashCode(swap.player))
    }
}
