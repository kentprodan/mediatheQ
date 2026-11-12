package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.testing.FakeAppLog
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackLogListenerTest {
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext<Context>())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()
    private val log = FakeAppLog()
    private val listener = PlaybackLogListener(player, log) { "DirectPlay · Original" }

    private fun item(
        id: String,
        title: String,
    ) = MediaItem
        .Builder()
        .setMediaId(id)
        .setUri("https://nas/$id")
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()

    @After
    fun release() = player.release()

    @Test
    fun `a track, its stream, a pause, the end of the queue and a stop are logged`() {
        player.addListener(listener)
        player.setMediaItems(listOf(item("t1", "Moșoi")))
        player.prepare()
        player.play()
        advance(player).untilState(Player.STATE_READY)
        advance(player).untilPendingCommandsAreFullyHandled()
        player.pause()
        advance(player).untilPendingCommandsAreFullyHandled()
        player.play()
        advance(player).untilState(Player.STATE_ENDED)
        player.stop()
        advance(player).untilState(Player.STATE_IDLE)

        assertThat(log.lines)
            .containsAtLeast(
                "I Playback: Track \"Moșoi\" [t1] (new queue)",
                "I Playback: Playing \"Moșoi\" [t1] · DirectPlay · Original",
                "I Playback: Paused",
                "I Playback: Queue ended",
                "I Playback: Stopped",
            ).inOrder()
    }

    @Test
    fun `an error is logged with its causes`() {
        player.setMediaItems(listOf(item("t1", "Moșoi")))

        listener.onPlayerError(
            PlaybackException(
                "Source error",
                SocketTimeoutException("timeout"),
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            ),
        )

        assertThat(log.lines.single())
            .isEqualTo(
                "W Playback: Error ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT on \"Moșoi\" [t1] | " +
                    "PlaybackException: Source error <- SocketTimeoutException: timeout",
            )
    }
}
