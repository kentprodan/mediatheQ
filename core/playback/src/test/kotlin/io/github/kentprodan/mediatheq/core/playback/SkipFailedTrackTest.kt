package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSourceEventListener
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.test.utils.FakeMediaPeriod
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.playback.PlaybackError.Reason
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class SkipFailedTrackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // Tracks (by number) that fail to load, and how.
    private val failures = mutableMapOf<Int, IOException>()
    private val player: ExoPlayer =
        TestExoPlayerBuilder(context).setMediaSourceFactory(FailingSourceFactory(failures)).build()
    private val errors = mutableListOf<PlaybackError>()
    private var recovers = false

    init {
        player.addListener(SkipFailedTrackListener(player, recover = { recovers }, onError = { errors += it }))
    }

    @After
    fun release() = player.release()

    private fun queue(vararg tracks: Int) {
        player.setMediaItems(tracks.map(::item))
        player.prepare()
        player.play()
    }

    private fun runUntil(condition: () -> Boolean) = RobolectricUtil.runMainLooperUntil(condition)

    @Test
    fun `offline, a track that is not downloaded is skipped when the one before ends, and the queue plays on`() {
        failures[2] = OfflineException()
        queue(1, 2, 3)

        runUntil { player.currentMediaItemIndex == 2 && player.isPlaying }

        assertThat(errors).containsExactly(PlaybackError("Track 2", Reason.NotDownloaded, skipped = true))
    }

    @Test
    fun `next onto a track that cannot load goes on to the one after`() {
        failures[2] = OfflineException()
        queue(1, 2, 3)
        runUntil { player.isPlaying }

        player.seekToNextMediaItem()

        runUntil { player.currentMediaItemIndex == 2 && player.isPlaying }
        assertThat(errors).containsExactly(PlaybackError("Track 2", Reason.NotDownloaded, skipped = true))
    }

    @Test
    fun `a track the server will not stream is skipped as failed`() {
        failures[1] = StreamUnavailableException(MqError.Forbidden)
        queue(1, 2)

        runUntil { player.currentMediaItemIndex == 1 && player.isPlaying }

        assertThat(errors).containsExactly(PlaybackError("Track 1", Reason.Failed, skipped = true))
    }

    @Test
    fun `an error playback recovers from is neither reported nor skipped`() {
        recovers = true
        failures[1] = IOException("refused by the output")
        queue(1, 2)

        runUntil { player.playerError != null }

        assertThat(player.currentMediaItemIndex).isEqualTo(0)
        assertThat(errors).isEmpty()
    }

    @Test
    fun `other errors stop playback where it is, without skipping`() {
        failures[1] = IOException("connection dropped")
        queue(1, 2)

        runUntil { player.playerError != null }

        assertThat(player.currentMediaItemIndex).isEqualTo(0)
        assertThat(errors).containsExactly(PlaybackError("Track 1", Reason.Failed, skipped = false))
    }

    @Test
    fun `a failing last track stops playback`() {
        failures[2] = OfflineException()
        queue(1, 2)

        runUntil { player.playerError != null }

        assertThat(errors).containsExactly(PlaybackError("Track 2", Reason.NotDownloaded, skipped = false))
    }

    @Test
    fun `on repeat all, a queue where nothing loads stops after one lap`() {
        (1..3).forEach { failures[it] = OfflineException() }
        player.repeatMode = Player.REPEAT_MODE_ALL
        queue(1, 2, 3)

        runUntil { errors.size == 3 && player.playerError != null }

        assertThat(errors.map { it.skipped }).containsExactly(true, true, false).inOrder()
    }

    @Test
    fun `a dropped connection stops where it is, also when it shows while asking for the stream`() {
        failures[1] = StreamUnavailableException(MqError.Network)
        queue(1, 2)

        runUntil { player.playerError != null }

        assertThat(player.currentMediaItemIndex).isEqualTo(0)
        assertThat(errors).containsExactly(PlaybackError("Track 1", Reason.Failed, skipped = false))
    }

    @Test
    fun `a new queue starts a new lap`() {
        listOf(1, 2, 4).forEach { failures[it] = OfflineException() }
        queue(4)
        runUntil { player.playerError != null }

        queue(1, 2, 3)

        runUntil { player.currentMediaItemIndex == 2 && player.isPlaying }
    }

    private fun item(n: Int): MediaItem =
        Track(MediaId(ServerId("s"), "t$n"), "Track $n", emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!!

    /** Fake sources; a track in [failures] never prepares and throws its error instead. */
    private class FailingSourceFactory(
        private val failures: Map<Int, IOException>,
        private val fakes: FakeMediaSourceFactory = FakeMediaSourceFactory(),
    ) : MediaSource.Factory by fakes {
        override fun createMediaSource(mediaItem: MediaItem): MediaSource =
            failures[mediaItem.mediaId.substringAfterLast(":t").toInt()]?.let { FailingSource(mediaItem, it) }
                ?: fakes.createMediaSource(mediaItem)
    }

    private class FailingSource(
        item: MediaItem,
        private val error: IOException,
    ) : FakeMediaSource(
            FakeTimeline(
                FakeTimeline.TimelineWindowDefinition
                    .Builder()
                    .setMediaItem(item)
                    .build(),
            ),
        ) {
        override fun createMediaPeriod(
            id: MediaSource.MediaPeriodId,
            trackGroupArray: TrackGroupArray,
            allocator: Allocator,
            mediaSourceEventDispatcher: MediaSourceEventListener.EventDispatcher,
            drmSessionManager: DrmSessionManager,
            drmEventDispatcher: DrmSessionEventListener.EventDispatcher,
            transferListener: TransferListener?,
        ): MediaPeriod =
            object : FakeMediaPeriod(
                trackGroupArray,
                allocator,
                TrackDataFactory.singleSampleWithTimeUs(0),
                mediaSourceEventDispatcher,
                drmSessionManager,
                drmEventDispatcher,
                // deferOnPrepared =
                true,
            ) {
                override fun maybeThrowPrepareError() = throw error
            }
    }
}
