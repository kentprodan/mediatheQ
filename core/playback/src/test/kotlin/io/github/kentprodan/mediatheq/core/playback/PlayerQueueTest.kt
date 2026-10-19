package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.FakeShuffleOrder
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
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
class PlayerQueueTest {
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()

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
    fun release() = player.release()

    private fun currentTitle() =
        player.currentMediaItem
            ?.mediaMetadata
            ?.title
            .toString()

    @Test
    fun `play starts the queue at the chosen track and plays`() {
        player.playQueue(items, startIndex = 2, shuffle = false)

        assertThat(player.mediaItemCount).isEqualTo(5)
        assertThat(currentTitle()).isEqualTo("Track 3")
        assertThat(player.shuffleModeEnabled).isFalse()
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `next and previous move through the queue in order`() {
        player.playQueue(items, startIndex = 0, shuffle = false)
        advance(player).untilPendingCommandsAreFullyHandled()

        player.seekToNextMediaItem()
        assertThat(currentTitle()).isEqualTo("Track 2")
        player.seekToNextMediaItem()
        player.seekToPreviousMediaItem()
        assertThat(currentTitle()).isEqualTo("Track 2")
    }

    @Test
    fun `shuffle starts at the first track of the shuffle order, not always at track 1`() {
        player.playQueue(items, startIndex = 3, shuffle = true)

        assertThat(player.shuffleModeEnabled).isTrue()
        assertThat(player.currentMediaItemIndex).isEqualTo(player.currentTimeline.getFirstWindowIndex(true))
    }

    @Test
    fun `a new queue replaces the old one`() {
        player.playQueue(items, startIndex = 0, shuffle = true)

        player.playQueue(items.take(2), startIndex = 1, shuffle = false)

        assertThat(player.mediaItemCount).isEqualTo(2)
        assertThat(currentTitle()).isEqualTo("Track 2")
        assertThat(player.shuffleModeEnabled).isFalse()
    }

    @Test
    fun `queue items resolve through the track scheme`() {
        player.playQueue(items, startIndex = 0, shuffle = false)

        assertThat(
            player.currentMediaItem
                ?.localConfiguration
                ?.uri
                ?.trackId(),
        ).isEqualTo(MediaId(ServerId("s"), "t1"))
    }

    @Test
    fun `shuffle through a MediaController starts where the player's shuffle order starts, not at track 1`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Reverse order: the shuffled queue starts with the last track.
        player.setShuffleOrder(FakeShuffleOrder(0))
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

        controller.playQueue(items, startIndex = 1, shuffle = true)
        RobolectricUtil.runMainLooperUntil { player.mediaItemCount == items.size && player.playWhenReady }

        assertThat(player.shuffleModeEnabled).isTrue()
        assertThat(currentTitle()).isEqualTo("Track 5")
        controller.release()
        session.release()
    }
}
