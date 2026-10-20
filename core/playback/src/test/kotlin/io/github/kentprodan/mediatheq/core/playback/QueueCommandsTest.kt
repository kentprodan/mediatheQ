package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.FakeShuffleOrder
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
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
class QueueCommandsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val exoPlayer: ExoPlayer =
        TestExoPlayerBuilder(context).setMediaSourceFactory(FakeMediaSourceFactory()).build()
    private val player = QueuePlayer(exoPlayer)

    @After
    fun release() = player.release()

    private fun item(n: Int): MediaItem =
        Track(MediaId(ServerId("s"), "t$n"), "Track $n", emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!!

    private fun items(vararg n: Int) = n.map(::item)

    private fun titles() = player.toQueueItems().map { it.title.removePrefix("Track ") }

    private fun currentTitle() =
        player.currentMediaItem
            ?.mediaMetadata
            ?.title
            .toString()
            .removePrefix("Track ")

    private fun entry(n: Int) = player.toQueueItems().first { it.title == "Track $n" }.entryId

    @Test
    fun `play next queues right after the current track`() {
        player.playQueue(items(1, 2, 3), startIndex = 1, shuffle = false)

        player.playNext(items(8, 9))

        assertThat(titles()).containsExactly("1", "2", "8", "9", "3").inOrder()
        assertThat(currentTitle()).isEqualTo("2")
    }

    @Test
    fun `add to queue appends at the end`() {
        player.playQueue(items(1, 2), startIndex = 0, shuffle = false)

        player.addToQueue(items(9))

        assertThat(titles()).containsExactly("1", "2", "9").inOrder()
    }

    @Test
    fun `play next and add to queue play at once when nothing is queued`() {
        player.playNext(items(1, 2))
        assertThat(titles()).containsExactly("1", "2").inOrder()
        assertThat(player.playWhenReady).isTrue()

        player.clearMediaItems()
        player.addToQueue(items(3))
        assertThat(titles()).containsExactly("3")
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `move puts the entry at the new place and the current track keeps playing`() {
        player.playQueue(items(1, 2, 3, 4), startIndex = 1, shuffle = false)

        player.moveEntry(entry(4), 0)

        assertThat(titles()).containsExactly("4", "1", "2", "3").inOrder()
        assertThat(currentTitle()).isEqualTo("2")
    }

    @Test
    fun `remove drops the entry, and removing the playing one goes on with the next`() {
        player.playQueue(items(1, 2, 3), startIndex = 1, shuffle = false)

        player.removeEntry(entry(1))
        assertThat(titles()).containsExactly("2", "3").inOrder()

        player.removeEntry(entry(2))
        assertThat(currentTitle()).isEqualTo("3")
    }

    @Test
    fun `removing the last entry empties the player`() {
        player.playQueue(items(1), startIndex = 0, shuffle = false)

        player.removeEntry(entry(1))

        assertThat(player.mediaItemCount).isEqualTo(0)
        assertThat(player.toPlayerUiModel().item).isNull()
    }

    @Test
    fun `skip to plays the chosen entry`() {
        player.playQueue(items(1, 2, 3), startIndex = 0, shuffle = false)
        player.pause()

        player.skipToEntry(entry(3))

        assertThat(currentTitle()).isEqualTo("3")
        assertThat(player.playWhenReady).isTrue()
    }

    @Test
    fun `the same track twice is two entries, and commands touch only the one named`() {
        player.playQueue(items(1, 1), startIndex = 0, shuffle = false)
        val queue = player.toQueueItems()
        assertThat(queue.map { it.entryId }.toSet()).hasSize(2)

        player.removeEntry(queue[1].entryId)

        assertThat(player.toQueueItems().single().entryId).isEqualTo(queue[0].entryId)
    }

    @Test
    fun `commands for an entry that is gone do nothing`() {
        player.playQueue(items(1, 2), startIndex = 0, shuffle = false)

        player.moveEntry("gone", 0)
        player.removeEntry("gone")
        player.skipToEntry("gone")

        assertThat(titles()).containsExactly("1", "2").inOrder()
        assertThat(currentTitle()).isEqualTo("1")
    }

    @Test
    fun `the current entry is marked in the player state`() {
        player.playQueue(items(1, 2), startIndex = 1, shuffle = false)

        assertThat(player.toPlayerUiModel().currentEntryId).isEqualTo(entry(2))
    }

    @Test
    fun `while shuffled the queue shows the shuffled order`() {
        exoPlayer.setShuffleOrder(FakeShuffleOrder(0)) // reversed
        player.setMediaItems(items(1, 2, 3))
        player.shuffleModeEnabled = true

        assertThat(titles()).containsExactly("3", "2", "1").inOrder()
        assertThat(player.toPlayerUiModel().shuffle).isTrue()
    }

    @Test
    fun `while shuffled, play next plays right after the current track and add to queue plays last`() {
        player.setMediaItems(items(1, 2, 3, 4, 5))
        player.shuffleModeEnabled = true
        player.prepare()
        val before = titles()
        val current = currentTitle()

        player.playNext(items(8))
        player.addToQueue(items(9))

        val after = titles()
        assertThat(after[after.indexOf(current) + 1]).isEqualTo("8")
        assertThat(after.last()).isEqualTo("9")
        assertThat(after - listOf("8", "9")).containsExactlyElementsIn(before).inOrder()
    }

    @Test
    fun `inserting into a shuffle order keeps everything else in place`() {
        // Queue 0..3 shuffled as 2, 0, 3, 1; two entries inserted at queue index 1 (after current 0) shift 1..3 by 2.
        val order = listOf(4, 0, 5, 3, 1, 2)

        assertThat(shuffleWithInserted(order, 1..2, current = 0, appended = false))
            .containsExactly(4, 0, 1, 2, 5, 3)
            .inOrder()
        assertThat(shuffleWithInserted(listOf(2, 0, 4, 1, 3), 4..4, current = 0, appended = true))
            .containsExactly(2, 0, 1, 3, 4)
            .inOrder()
    }

    @Test
    fun `a controller sees the entries, the current one and the shuffled order`() {
        exoPlayer.setShuffleOrder(FakeShuffleOrder(0))
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

        controller.setMediaItems(items(1, 2, 3))
        controller.shuffleModeEnabled = true
        RobolectricUtil.runMainLooperUntil { player.mediaItemCount == 3 && player.shuffleModeEnabled }
        RobolectricUtil.runMainLooperUntil { controller.toQueueItems() == player.toQueueItems() }

        assertThat(
            controller.toQueueItems().map { it.title },
        ).containsExactly("Track 3", "Track 2", "Track 1").inOrder()
        assertThat(controller.toPlayerUiModel().currentEntryId).isEqualTo(player.toPlayerUiModel().currentEntryId)
        controller.removeEntry(controller.toQueueItems().first().entryId)
        RobolectricUtil.runMainLooperUntil { player.mediaItemCount == 2 }
        assertThat(titles()).containsExactly("2", "1").inOrder()
        controller.release()
        session.release()
    }
}
