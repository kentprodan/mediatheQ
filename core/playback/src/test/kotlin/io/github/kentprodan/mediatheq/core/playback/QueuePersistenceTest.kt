package io.github.kentprodan.mediatheq.core.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.QueuePlace
import io.github.kentprodan.mediatheq.core.data.QueueRepository
import io.github.kentprodan.mediatheq.core.data.SavedEntry
import io.github.kentprodan.mediatheq.core.data.SavedQueue
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.RepeatMode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeQueueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class QueuePersistenceTest {
    private val player: ExoPlayer =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(FakeMediaSourceFactory())
            .build()
    private val repository = FakeQueueRepository()

    @After
    fun release() = player.release()

    private fun track(n: Int) =
        Track(MediaId(ServerId("s"), "t$n"), "Track $n", emptyList(), 3.minutes, artwork = Artwork("al", "tag"))

    private fun item(n: Int): MediaItem = track(n).toMediaItem(null).withTrackUri()!!

    private fun saved(
        place: QueuePlace,
        shuffleOrder: List<Int> = listOf(0, 1, 2),
    ) = SavedQueue((1..3).map { SavedEntry("e$it", track(it)) }, shuffleOrder, place)

    private fun TestScope.start(): QueuePersistence =
        QueuePersistence(player, repository, backgroundScope, artworkUrl = { "https://art/${it.itemId}" })
            .also { it.start() }

    /**
     * Lets the player finish what it was asked (its own thread answers on the main looper at its own pace) and deliver
     * the events, then lets [time] pass.
     */
    private fun TestScope.wait(time: Duration) {
        advance(player).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
        advanceTimeBy(time)
    }

    private fun titles() = player.toQueueItems().map { it.title }

    @Test
    fun `the saved queue comes back paused at its track and position, not prepared`() =
        runTest {
            repository.saved = saved(QueuePlace(1, 42.seconds, shuffle = false))

            start()
            runCurrent()

            assertThat(titles()).containsExactly("Track 1", "Track 2", "Track 3").inOrder()
            assertThat(player.currentMediaItemIndex).isEqualTo(1)
            assertThat(player.currentPosition.milliseconds).isEqualTo(42.seconds)
            assertThat(player.playWhenReady).isFalse()
            // Idle: no stream opened, nothing reported to the server until play.
            assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)
        }

    @Test
    fun `restored entries keep their ids, resolve through the track scheme and carry the cover`() =
        runTest {
            repository.saved = saved(QueuePlace(0, Duration.ZERO, shuffle = false))

            start()
            runCurrent()

            val item = player.getMediaItemAt(0)
            assertThat(item.entryId()).isEqualTo("e1")
            assertThat(item.localConfiguration?.uri?.trackId()).isEqualTo(MediaId(ServerId("s"), "t1"))
            assertThat(item.mediaMetadata.artworkUri.toString()).isEqualTo("https://art/al")
        }

    @Test
    fun `shuffle and its order come back`() =
        runTest {
            repository.saved = saved(QueuePlace(0, Duration.ZERO, shuffle = true), shuffleOrder = listOf(2, 0, 1))

            start()
            runCurrent()

            assertThat(player.shuffleModeEnabled).isTrue()
            assertThat(titles()).containsExactly("Track 3", "Track 1", "Track 2").inOrder()
        }

    @Test
    fun `the repeat mode comes back`() =
        runTest {
            repository.saved = saved(QueuePlace(0, Duration.ZERO, shuffle = false, repeatMode = RepeatMode.One))

            start()
            runCurrent()

            assertThat(player.repeatMode).isEqualTo(Player.REPEAT_MODE_ONE)
        }

    @Test
    fun `a new repeat mode saves the place`() =
        runTest {
            start()
            runCurrent()
            player.playQueue(listOf(item(1), item(2)), 0, shuffle = false)
            player.pause()
            wait(2.seconds)

            player.repeatMode = Player.REPEAT_MODE_ALL
            wait(2.seconds)

            assertThat(repository.place?.repeatMode).isEqualTo(RepeatMode.All)
            assertThat(repository.queueWrites).isEqualTo(1)
        }

    @Test
    fun `a queue started before the restore finished is kept`() =
        runTest {
            val restoreGate = CompletableDeferred<Unit>()
            val slowRepository =
                object : QueueRepository by repository {
                    override suspend fun restore(): SavedQueue? {
                        restoreGate.await()
                        return saved(QueuePlace(0, Duration.ZERO, shuffle = false))
                    }
                }
            QueuePersistence(player, slowRepository, backgroundScope, artworkUrl = { null }).start()
            runCurrent()

            player.playQueue(listOf(item(9)), 0, shuffle = false)
            restoreGate.complete(Unit)
            runCurrent()

            assertThat(titles()).containsExactly("Track 9")
        }

    @Test
    fun `a restored queue is saved again in full, so later places match its entries`() =
        runTest {
            // As restore() returns it after a sync dropped an entry: indices are into the entries left.
            repository.saved = saved(QueuePlace(1, 5.seconds, shuffle = false))

            start()
            runCurrent()
            wait(2.seconds)

            assertThat(repository.queueWrites).isEqualTo(1)
            assertThat(repository.entries.map { it.entryId }).containsExactly("e1", "e2", "e3").inOrder()
            assertThat(repository.place?.index).isEqualTo(1)
        }

    @Test
    fun `a failed restore still saves what is played next`() =
        runTest {
            val failing =
                object : QueueRepository by repository {
                    override suspend fun restore(): SavedQueue? = error("database broken")
                }
            QueuePersistence(player, failing, backgroundScope, artworkUrl = { null }).start()
            runCurrent()

            player.playQueue(listOf(item(1)), 0, shuffle = false)
            wait(2.seconds)

            assertThat(repository.entries.map { it.trackId.itemId }).containsExactly("t1")
        }

    @Test
    fun `a queue started before the restore finished is saved`() =
        runTest {
            val restoreGate = CompletableDeferred<Unit>()
            val slowRepository =
                object : QueueRepository by repository {
                    override suspend fun restore(): SavedQueue? {
                        restoreGate.await()
                        return null
                    }
                }
            QueuePersistence(player, slowRepository, backgroundScope, artworkUrl = { null }).start()
            runCurrent()

            player.playQueue(listOf(item(9)), 0, shuffle = false)
            restoreGate.complete(Unit)
            wait(2.seconds)

            assertThat(repository.entries.map { it.trackId.itemId }).containsExactly("t9")
        }

    @Test
    fun `preparing tracks does not rewrite the queue, only queue changes do`() =
        runTest {
            start()
            runCurrent()
            player.playQueue(listOf(item(1), item(2)), 0, shuffle = false)
            advance(player).untilState(Player.STATE_READY)
            wait(2.seconds)
            val writes = repository.queueWrites

            player.seekToNextMediaItem()
            advance(player).untilState(Player.STATE_READY)
            wait(2.seconds)

            assertThat(repository.queueWrites).isEqualTo(writes)
            assertThat(repository.place?.index).isEqualTo(1)
        }

    @Test
    fun `nothing saved leaves the player empty`() =
        runTest {
            start()
            runCurrent()

            assertThat(player.mediaItemCount).isEqualTo(0)
        }

    @Test
    fun `a new queue is saved after a short pause, with its entries, shuffle order and place`() =
        runTest {
            start()
            runCurrent()

            player.playQueue(listOf(item(1), item(2)), 1, shuffle = false)
            wait(500.milliseconds)
            assertThat(repository.queueWrites).isEqualTo(0)
            wait(2.seconds)

            assertThat(repository.queueWrites).isEqualTo(1)
            assertThat(repository.entries.map { it.trackId.itemId }).containsExactly("t1", "t2").inOrder()
            assertThat(repository.entries.map { it.entryId }).isEqualTo(player.toQueueItems().map { it.entryId })
            assertThat(repository.shuffleOrder).hasSize(2)
            assertThat(repository.place?.index).isEqualTo(1)
        }

    @Test
    fun `pauses and seeks save only the place, not the entries again`() =
        runTest {
            start()
            runCurrent()
            player.playQueue(listOf(item(1), item(2)), 0, shuffle = false)
            wait(2.seconds)

            player.seekTo(1, 5_000) // fake media is ~10 s long
            player.pause()
            wait(2.seconds)

            assertThat(repository.queueWrites).isEqualTo(1)
            assertThat(repository.place).isEqualTo(QueuePlace(1, 5.seconds, shuffle = false))
        }

    @Test
    fun `while playing the place is saved every ten seconds`() =
        runTest {
            start()
            runCurrent()
            player.playQueue(listOf(item(1)), 0, shuffle = false)
            advance(player).untilState(Player.STATE_READY)
            wait(2.seconds)
            repository.place = null

            wait(10.seconds)

            assertThat(repository.place?.index).isEqualTo(0)
        }

    @Test
    fun `an emptied queue is saved empty`() =
        runTest {
            start()
            runCurrent()
            player.playQueue(listOf(item(1)), 0, shuffle = false)
            wait(2.seconds)

            assertThat(repository.entries).hasSize(1)

            player.clearMediaItems()
            wait(2.seconds)

            assertThat(repository.entries).isEmpty()
        }

    @Test
    fun `save now writes at once, e g when the service stops`() =
        runTest {
            val persistence = start()
            runCurrent()
            player.playQueue(listOf(item(1)), 0, shuffle = false)

            persistence.saveNow()
            runCurrent()

            assertThat(repository.entries.map { it.trackId.itemId }).containsExactly("t1")
        }
}
