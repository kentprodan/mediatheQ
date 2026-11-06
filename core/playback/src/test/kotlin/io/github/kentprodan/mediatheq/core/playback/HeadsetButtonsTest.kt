package io.github.kentprodan.mediatheq.core.playback

import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.HeadsetAction
import io.github.kentprodan.mediatheq.core.data.HeadsetPreferences
import io.github.kentprodan.mediatheq.core.data.NextButtonAction
import io.github.kentprodan.mediatheq.core.data.PreviousButtonAction
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class HeadsetButtonsTest {
    private val exoPlayer: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()
    private val player = QueuePlayer(exoPlayer)
    private var preferences = HeadsetPreferences()

    // The pending count ends when the test says so.
    private var pending: (() -> Unit)? = null
    private val buttons =
        HeadsetButtons(player, { preferences }) { _, block ->
            pending = block
            ({ pending = null })
        }

    @After
    fun release() = exoPlayer.release()

    private fun start() {
        player.setMediaItems(
            (1..4).map {
                Track(MediaId(ServerId("s"), "t$it"), "T$it", emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!!
            },
            1,
            5_000,
        )
        player.prepare()
        advance(exoPlayer).untilPendingCommandsAreFullyHandled()
    }

    private fun press(key: Int) {
        assertThat(buttons.onMediaButton(KeyEvent(KeyEvent.ACTION_DOWN, key))).isTrue()
        assertThat(buttons.onMediaButton(KeyEvent(KeyEvent.ACTION_UP, key))).isTrue()
    }

    private fun windowEnds() {
        pending?.invoke()
        pending = null
    }

    @Test
    fun `one click plays and pauses, two go next, three go previous`() {
        start()
        press(KeyEvent.KEYCODE_HEADSETHOOK)
        windowEnds()
        assertThat(player.playWhenReady).isTrue()

        press(KeyEvent.KEYCODE_HEADSETHOOK)
        press(KeyEvent.KEYCODE_HEADSETHOOK)
        windowEnds()
        assertThat(player.currentMediaItemIndex).isEqualTo(2)

        repeat(3) { press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        assertThat(player.currentMediaItemIndex).isEqualTo(1)
    }

    @Test
    fun `a Bluetooth pause key pauses, it never toggles back on`() {
        start()
        player.play()

        press(KeyEvent.KEYCODE_MEDIA_PAUSE)
        windowEnds()
        assertThat(player.playWhenReady).isFalse()

        press(KeyEvent.KEYCODE_MEDIA_PAUSE)
        windowEnds()
        assertThat(player.playWhenReady).isFalse()
    }

    @Test
    fun `the next and previous buttons do what they are remapped to`() {
        start()
        preferences =
            preferences.copy(nextButton = NextButtonAction.SeekForward, previousButton = PreviousButtonAction.Next)

        press(KeyEvent.KEYCODE_MEDIA_NEXT)
        assertThat(player.currentPosition).isAtLeast(9_000)
        assertThat(player.currentMediaItemIndex).isEqualTo(1)

        press(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        assertThat(player.currentMediaItemIndex).isEqualTo(2)

        preferences = preferences.copy(nextButton = NextButtonAction.Nothing)
        press(KeyEvent.KEYCODE_MEDIA_NEXT)
        assertThat(player.currentMediaItemIndex).isEqualTo(2)
    }

    @Test
    fun `a click set to nothing does nothing, other keys are left to Media3`() {
        start()
        preferences = preferences.copy(singleClick = HeadsetAction.Nothing)

        press(KeyEvent.KEYCODE_HEADSETHOOK)
        windowEnds()

        assertThat(player.playWhenReady).isFalse()
        assertThat(buttons.onMediaButton(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))).isFalse()
    }
}
