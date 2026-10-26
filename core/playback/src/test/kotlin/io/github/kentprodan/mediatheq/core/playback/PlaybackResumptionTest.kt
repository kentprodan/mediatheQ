package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The system asks to play with nothing queued (media button or media card with the app closed, #43): the session's
 * playback resumption brings back the saved queue, the way the service wires it.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PlaybackResumptionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val player: ExoPlayer =
        TestExoPlayerBuilder(context).setMediaSourceFactory(FakeMediaSourceFactory()).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = FakeQueueRepository()

    // Holds the restore back, as a database read still running when the media button arrives would.
    private val restoreGate = CompletableDeferred<Unit>()
    private val slowRepository =
        object : QueueRepository by repository {
            override suspend fun restore(): SavedQueue? {
                restoreGate.await()
                return repository.restore()
            }
        }
    private val persistence =
        QueuePersistence(player, slowRepository, scope, artworkUrl = { "https://nas/art/${it.itemId}" })
    private var session: MediaSession? = null
    private var resumptions = 0

    @After
    fun release() {
        scope.cancel()
        session?.release()
        player.release()
    }

    private fun track(n: Int) =
        Track(MediaId(ServerId("s"), "t$n"), "Track $n", emptyList(), 3.minutes, artwork = Artwork("al$n", "tag"))

    private fun saved(place: QueuePlace = QueuePlace(1, 5.seconds, shuffle = false)) =
        SavedQueue((1..3).map { SavedEntry("e$it", track(it)) }, listOf(2, 0, 1), place)

    /** A controller on a session wired like the service's: resumption comes from [persistence]. */
    private fun connect(): MediaController {
        session =
            MediaSession
                .Builder(context, QueuePlayer(player))
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onPlaybackResumption(
                            mediaSession: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            isForPlayback: Boolean,
                        ) = persistence.resumptionFuture(isForPlayback) { null }.also { resumptions++ }
                    },
                ).build()
        val future = MediaController.Builder(context, session!!.token).buildAsync()
        RobolectricUtil.runMainLooperUntil { future.isDone }
        return future.get()
    }

    private fun titles() = player.toQueueItems().map { it.title }

    @Test
    fun `play with nothing queued resumes the saved queue at its entry and position`() {
        repository.saved = saved()
        persistence.start()
        val controller = connect()

        controller.play()
        // The play reaches the session while the restore still runs, so the session asks for the resumption.
        RobolectricUtil.runMainLooperUntil { resumptions == 1 }
        restoreGate.complete(Unit)

        RobolectricUtil.runMainLooperUntil { player.playWhenReady && player.mediaItemCount == 3 }
        assertThat(player.currentMediaItem?.entryId()).isEqualTo("e2")
        assertThat(player.currentPosition).isAtLeast(5_000)
        assertThat(player.playbackState).isNotEqualTo(Player.STATE_IDLE)
        controller.release()
    }

    @Test
    fun `a resumed shuffled queue keeps its order and repeat mode`() {
        repository.saved = saved(QueuePlace(1, 5.seconds, shuffle = true, repeatMode = RepeatMode.One))
        persistence.start()
        val controller = connect()

        controller.play()
        // The play reaches the session while the restore still runs, so the session asks for the resumption.
        RobolectricUtil.runMainLooperUntil { resumptions == 1 }
        restoreGate.complete(Unit)

        RobolectricUtil.runMainLooperUntil { player.playWhenReady && player.mediaItemCount == 3 }
        assertThat(player.shuffleModeEnabled).isTrue()
        assertThat(titles()).containsExactly("Track 3", "Track 1", "Track 2").inOrder()
        assertThat(player.repeatMode).isEqualTo(Player.REPEAT_MODE_ONE)
        controller.release()
    }

    @Test
    fun `nothing saved resumes nothing`() {
        persistence.start()
        val controller = connect()

        controller.play()
        // The play reaches the session while the restore still runs, so the session asks for the resumption.
        RobolectricUtil.runMainLooperUntil { resumptions == 1 }
        restoreGate.complete(Unit)
        RobolectricUtil.runMainLooperUntil { !controller.playWhenReady || player.mediaItemCount == 0 }

        assertThat(player.mediaItemCount).isEqualTo(0)
        assertThat(persistence.resumptionFuture(isForPlayback = true) { null }.get().mediaItems).isEmpty()
        controller.release()
    }

    @Test
    fun `the media card at boot gets only the current track, its cover as bytes, and the player stays idle`() {
        repository.saved = saved()
        persistence.start()
        restoreGate.complete(Unit)
        val requested = mutableListOf<Uri>()

        val future =
            persistence.resumptionFuture(isForPlayback = false) {
                requested += it
                byteArrayOf(1, 2, 3)
            }
        RobolectricUtil.runMainLooperUntil { future.isDone }

        val item = future.get().mediaItems.single()
        assertThat(item.entryId()).isEqualTo("e2")
        assertThat(item.mediaMetadata.artworkData).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(item.mediaMetadata.artworkUri).isNull()
        assertThat(requested).containsExactly(Uri.parse("https://nas/art/al2"))
        assertThat(player.playbackState).isEqualTo(Player.STATE_IDLE)
    }

    @Test
    fun `a cover the server does not give leaves the card without one`() {
        repository.saved = saved()
        persistence.start()
        restoreGate.complete(Unit)

        val future = persistence.resumptionFuture(isForPlayback = false) { null }
        RobolectricUtil.runMainLooperUntil { future.isDone }

        val card = future.get().mediaItems.single()
        assertThat(card.mediaMetadata.artworkData).isNull()
        assertThat(card.mediaMetadata.artworkUri).isEqualTo(Uri.parse("https://nas/art/al2"))
    }
}
