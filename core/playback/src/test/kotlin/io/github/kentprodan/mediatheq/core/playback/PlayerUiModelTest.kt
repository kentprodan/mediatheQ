package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Artwork
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
class PlayerUiModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val player: ExoPlayer =
        TestExoPlayerBuilder(context).setMediaSourceFactory(FakeMediaSourceFactory()).build()

    private val artwork = Artwork("album-1", "tag-1", "LEHV6nWB2yk8")

    private fun track(id: String) =
        Track(
            id = MediaId(ServerId("s"), id),
            title = "Title $id",
            artists = listOf(Artist(MediaId(ServerId("s"), "a"), "Artist")),
            duration = 3.minutes,
            albumTitle = "Album",
            artwork = artwork,
        )

    private fun items(vararg ids: String): List<MediaItem> = ids.map { track(it).toMediaItem(null).withTrackUri()!! }

    @After
    fun release() = player.release()

    @Test
    fun `nothing queued is an empty model`() {
        assertThat(player.toPlayerUiModel()).isEqualTo(PlayerUiModel())
    }

    @Test
    fun `the current entry carries title, artist, album and cover`() {
        player.setMediaItems(items("t1", "t2"))

        val model = player.toPlayerUiModel()

        assertThat(model.item)
            .isEqualTo(NowPlayingItem(MediaId(ServerId("s"), "t1"), "Title t1", "Artist", artwork))
        assertThat(model.hasNext).isTrue()
        assertThat(model.hasPrevious).isTrue()
    }

    @Test
    fun `before the stream is loaded the duration comes from the track`() {
        player.setMediaItems(items("t1"))

        assertThat(player.toPlayerUiModel().duration).isEqualTo(3.minutes)
    }

    @Test
    fun `playing shows pause, and an ended queue shows play again`() {
        player.setMediaItems(items("t1"))
        player.prepare()
        player.play()
        advance(player).untilState(Player.STATE_READY)

        assertThat(player.toPlayerUiModel().isPlaying).isTrue()

        advance(player).untilState(Player.STATE_ENDED)

        assertThat(player.toPlayerUiModel().isPlaying).isFalse()
    }

    @Test
    fun `seekable follows the stream`() {
        val unseekable =
            FakeMediaSource(
                FakeTimeline(
                    FakeTimeline.TimelineWindowDefinition
                        .Builder()
                        .setSeekable(false)
                        .build(),
                ),
            )
        player.setMediaSource(unseekable)
        player.prepare()
        advance(player).untilState(Player.STATE_READY)
        // A raw media source has no track id; the flag alone is under test here.
        assertThat(player.isCurrentMediaItemSeekable).isFalse()

        player.setMediaItems(items("t1"))
        player.prepare()
        advance(player).untilState(Player.STATE_READY)

        assertThat(player.toPlayerUiModel().isSeekable).isTrue()
    }

    @Test
    fun `the cover survives the trip from a controller through the session`() {
        val session =
            MediaSession
                .Builder(context, player)
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onAddMediaItems(
                            mediaSession: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            mediaItems: MutableList<MediaItem>,
                        ) = Futures.immediateFuture(mediaItems.map { it.withTrackUri()!! }.toMutableList())
                    },
                ).build()
        val future = MediaController.Builder(context, session.token).buildAsync()
        RobolectricUtil.runMainLooperUntil { future.isDone }
        val controller = future.get()

        controller.setMediaItems(listOf(track("t1").toMediaItem(null)))
        RobolectricUtil.runMainLooperUntil { player.mediaItemCount == 1 }

        assertThat(player.toPlayerUiModel().item?.artwork).isEqualTo(artwork)
        assertThat(controller.toPlayerUiModel().item?.artwork).isEqualTo(artwork)
        controller.release()
        session.release()
    }

    @Test
    fun `the audio output reaches controllers through the session extras, and its changes too`() {
        val bitPerfect =
            AudioOutputInfo(bitPerfectSampleRateHz = 96_000, usbDacConnected = true, atmosConverted = true)
        val session = MediaSession.Builder(context, player).setSessionExtras(AudioOutputInfo().toBundle()).build()
        var heard: AudioOutputInfo? = null
        val future =
            MediaController
                .Builder(context, session.token)
                .setListener(
                    object : MediaController.Listener {
                        override fun onExtrasChanged(
                            controller: MediaController,
                            extras: android.os.Bundle,
                        ) {
                            heard = extras.toAudioOutputInfo()
                        }
                    },
                ).buildAsync()
        RobolectricUtil.runMainLooperUntil { future.isDone }
        val controller = future.get()
        assertThat(controller.sessionExtras.toAudioOutputInfo()).isEqualTo(AudioOutputInfo())

        session.sessionExtras = bitPerfect.toBundle()
        RobolectricUtil.runMainLooperUntil { heard != null }

        assertThat(heard).isEqualTo(bitPerfect)
        assertThat(heard!!.equalizerBlocked).isTrue()
        assertThat(controller.toPlayerUiModel(heard!!).output).isEqualTo(bitPerfect)
        controller.release()
        session.release()
    }

    @Test
    fun `missing extras mean a plain output where bit-perfect is possible`() {
        assertThat(android.os.Bundle().toAudioOutputInfo()).isEqualTo(AudioOutputInfo())
        assertThat(AudioOutputInfo().equalizerBlocked).isFalse()
    }
}
