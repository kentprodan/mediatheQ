package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
class PlayIntentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val player: ExoPlayer =
        TestExoPlayerBuilder(context).setMediaSourceFactory(FakeMediaSourceFactory()).build()

    @After
    fun release() = player.release()

    @Test
    fun `music is meant to play while buffering, not while paused, idle or ended`() {
        assertThat(player.wantsToPlay()).isFalse()

        player.setMediaItem(Track(MediaId(ServerId("s"), "t1"), "Track", emptyList(), 3.minutes).toMediaItem(null))
        player.prepare()
        player.play()
        // Buffering (isPlaying is false here) must not count as stopped: live updates would drop at every stall.
        assertThat(player.playbackState).isEqualTo(Player.STATE_BUFFERING)
        assertThat(player.isPlaying).isFalse()
        assertThat(player.wantsToPlay()).isTrue()

        player.pause()
        assertThat(player.wantsToPlay()).isFalse()

        player.play()
        TestPlayerRunHelper.run(player).untilState(Player.STATE_ENDED)
        assertThat(player.wantsToPlay()).isFalse()
    }
}
