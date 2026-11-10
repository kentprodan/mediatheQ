package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import io.github.kentprodan.mediatheq.core.data.PlaybackBehavior
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class QueuePlayerTest {
    private val exoPlayer: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()
    private var behavior = PlaybackBehavior()
    private var resuming = false
    private var uptime = 0L
    private var stopPauses = false
    private val player =
        QueuePlayer(exoPlayer, { behavior }, { resuming }, Random(1), uptime = { uptime }, stopPauses = { stopPauses })

    private val items: List<MediaItem> =
        (1..5).map {
            Track(
                MediaId(ServerId("s"), "t$it"),
                "Track $it",
                emptyList(),
                3.minutes,
            ).toMediaItem(null).withTrackUri()!!
        }

    @After
    fun release() = exoPlayer.release()

    private fun title(index: Int = player.currentMediaItemIndex) =
        player
            .getMediaItemAt(index)
            .mediaMetadata.title
            .toString()

    private fun start(at: Int = 0) {
        player.setMediaItems(items, at, 0)
        player.prepare()
        advance(exoPlayer).untilPendingCommandsAreFullyHandled()
    }

    @Test
    fun `previous rewinds a track played past three seconds, or goes back when rewind first is off`() {
        start(at = 2)
        player.seekTo(5_000)
        player.seekToPrevious()
        assertThat(title()).isEqualTo("Track 3")
        assertThat(player.currentPosition).isLessThan(1_000)

        behavior = behavior.copy(rewindFirst = false)
        player.seekTo(5_000)
        player.seekToPrevious()
        assertThat(title()).isEqualTo("Track 2")
    }

    @Test
    fun `circular navigation wraps at both ends, off it stops there`() {
        start(at = 4)
        player.seekToNext()
        assertThat(title()).isEqualTo("Track 1")
        player.seekToPrevious()
        assertThat(title()).isEqualTo("Track 5")

        behavior = behavior.copy(circular = false)
        player.seekToNext()
        assertThat(title()).isEqualTo("Track 5")
        assertThat(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)).isFalse()
    }

    @Test
    fun `with circular navigation a MediaController can skip next on the last track`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
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
        start(at = 4)
        val future = MediaController.Builder(context, session.token).buildAsync()
        RobolectricUtil.runMainLooperUntil { future.isDone }
        val controller = future.get()

        assertThat(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)).isTrue()
        controller.seekToNext()
        RobolectricUtil.runMainLooperUntil { player.currentMediaItemIndex == 0 }

        controller.release()
        session.release()
    }

    @Test
    fun `a skip while paused starts playing, unless paused skips stay paused`() {
        start()
        player.seekToNext()
        assertThat(player.playWhenReady).isTrue()

        player.pause()
        behavior = behavior.copy(keepPausedOnSkip = true)
        player.seekToNext()
        assertThat(player.playWhenReady).isFalse()
        assertThat(title()).isEqualTo("Track 3")
    }

    @Test
    fun `a manual skip on repeat one switches to repeat all, unless that is off`() {
        start()
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.seekToNext()
        assertThat(player.repeatMode).isEqualTo(Player.REPEAT_MODE_ALL)
        assertThat(title()).isEqualTo("Track 2")

        behavior = behavior.copy(stopRepeatOneOnSkip = false)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.seekToNext()
        assertThat(player.repeatMode).isEqualTo(Player.REPEAT_MODE_ONE)
        assertThat(title()).isEqualTo("Track 3")
    }

    private fun extra(n: Int) =
        Track(MediaId(ServerId("s"), "n$n"), "Next $n", emptyList(), 3.minutes)
            .toMediaItem(null)
            .withTrackUri()!!
            .markedPlayedNext()

    @Test
    fun `Play next goes right after the current track, or after earlier Play next entries when preserved`() {
        start()
        player.addMediaItems(1, listOf(extra(1)))
        player.addMediaItems(1, listOf(extra(2)))
        assertThat((1..2).map(::title)).containsExactly("Next 2", "Next 1").inOrder()

        player.clearMediaItems()
        behavior = behavior.copy(preservePlayNextOrder = true)
        start()
        player.addMediaItems(1, listOf(extra(1)))
        player.addMediaItems(1, listOf(extra(2)))
        assertThat((1..3).map(::title)).containsExactly("Next 1", "Next 2", "Track 2").inOrder()
    }

    @Test
    fun `preserved Play next order holds while shuffled too`() {
        behavior = behavior.copy(preservePlayNextOrder = true)
        start()
        player.shuffleModeEnabled = true
        player.addMediaItems(player.currentMediaItemIndex + 1, listOf(extra(1)))
        player.addMediaItems(player.currentMediaItemIndex + 1, listOf(extra(2)))

        val order = player.playOrder()
        val afterCurrent = order.drop(order.indexOf(player.currentMediaItemIndex) + 1).take(2).map(::title)
        assertThat(afterCurrent).containsExactly("Next 1", "Next 2").inOrder()
    }

    @Test
    fun `a new queue resets repeat and shuffle, unless the player state is retained`() {
        start()
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = true
        start(at = 1)
        assertThat(player.repeatMode).isEqualTo(Player.REPEAT_MODE_OFF)
        assertThat(player.shuffleModeEnabled).isFalse()

        behavior = behavior.copy(retainPlayerState = true)
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = true
        start(at = 1)
        assertThat(player.repeatMode).isEqualTo(Player.REPEAT_MODE_ALL)
        assertThat(player.shuffleModeEnabled).isTrue()
        assertThat(player.playOrder().first()).isEqualTo(1)
    }

    @Test
    fun `the system resuming the saved queue keeps its repeat mode`() {
        start()
        player.repeatMode = Player.REPEAT_MODE_ALL
        resuming = true

        start(at = 3)

        assertThat(player.repeatMode).isEqualTo(Player.REPEAT_MODE_ALL)
    }

    @Test
    fun `shuffle turned on mid-queue starts with the playing track, uniform when weighted shuffle is off`() {
        behavior = behavior.copy(weightedShuffle = false)
        start(at = 2)
        player.shuffleModeEnabled = true

        assertThat(player.playOrder()).isEqualTo(shuffleFrom(2, items.size, Random(1)))
    }

    @Test
    fun `weighted shuffle orders every track once, the playing one first`() {
        start(at = 2)
        player.shuffleModeEnabled = true

        assertThat(player.playOrder().first()).isEqualTo(2)
        assertThat(player.playOrder()).containsExactlyElementsIn(0 until items.size)
    }

    @Test
    fun `Previous with rewind first off still restarts the first track, the media item variant does nothing there`() {
        behavior = behavior.copy(rewindFirst = false, circular = false)
        start()
        player.seekTo(5_000)

        player.seekToPreviousMediaItem()
        assertThat(player.currentPosition).isAtLeast(5_000)

        player.seekToPrevious()
        assertThat(title()).isEqualTo("Track 1")
        assertThat(player.currentPosition).isLessThan(1_000)
    }

    @Test
    fun `a controller's new queue without a start index turns shuffle off, Shuffle's own queue stays shuffled`() {
        start()
        player.shuffleModeEnabled = true
        uptime += 60_000

        player.setMediaItems(items) // Android Auto playing a playlist
        assertThat(player.shuffleModeEnabled).isFalse()

        player.shuffleModeEnabled = true // Shuffle: the mode, then its queue
        player.setMediaItems(items, true)
        assertThat(player.shuffleModeEnabled).isTrue()
    }

    private fun connect(): Pair<MediaSession, MediaController> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
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
        return session to future.get()
    }

    @Test
    fun `a MediaController's stop pauses and keeps the queue ready when asked, else it stops`() {
        start()
        val (session, controller) = connect()
        controller.play()
        RobolectricUtil.runMainLooperUntil { exoPlayer.playWhenReady }

        stopPauses = true
        controller.stop()
        RobolectricUtil.runMainLooperUntil { !exoPlayer.playWhenReady }
        assertThat(exoPlayer.playbackState).isNotEqualTo(Player.STATE_IDLE)
        assertThat(exoPlayer.mediaItemCount).isEqualTo(5)

        stopPauses = false
        controller.stop()
        RobolectricUtil.runMainLooperUntil { exoPlayer.playbackState == Player.STATE_IDLE }
        assertThat(exoPlayer.mediaItemCount).isEqualTo(5)
        controller.release()
        session.release()
    }

    @Test
    fun `through a MediaController, Previous rewinds, a paused skip plays, and repeat one turns to repeat all`() {
        start(at = 2)
        val (session, controller) = connect()
        player.seekTo(5_000)
        RobolectricUtil.runMainLooperUntil { controller.currentPosition >= 5_000 }

        controller.seekToPrevious()
        RobolectricUtil.runMainLooperUntil { player.currentPosition < 1_000 }
        assertThat(title()).isEqualTo("Track 3")

        player.pause()
        player.repeatMode = Player.REPEAT_MODE_ONE
        RobolectricUtil.runMainLooperUntil {
            controller.repeatMode == Player.REPEAT_MODE_ONE &&
                !controller.playWhenReady
        }
        controller.seekToNext()
        RobolectricUtil.runMainLooperUntil { player.currentMediaItemIndex == 3 }
        assertThat(player.playWhenReady).isTrue()
        assertThat(player.repeatMode).isEqualTo(Player.REPEAT_MODE_ALL)

        controller.release()
        session.release()
    }
}
