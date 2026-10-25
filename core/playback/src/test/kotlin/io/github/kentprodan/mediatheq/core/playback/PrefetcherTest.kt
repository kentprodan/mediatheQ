package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class PrefetcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val player: ExoPlayer =
        TestExoPlayerBuilder(context).setMediaSourceFactory(FakeMediaSourceFactory()).build()
    private val repository = FakePlaybackRepository()
    private val network = FakeNetworkMonitor()
    private val resolver = StreamResolver(repository, { setOf("audio/flac") }, network)
    private val downloads = FakeDownloadRepository()
    private val cache =
        SimpleCache(
            Files.createTempDirectory("media").toFile(),
            LeastRecentlyUsedCacheEvictor(STREAMING_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )

    /** The server: every track's original is 100 bytes at the fake stream URL. */
    private val server = FakeDataSet().apply { (1..4).forEach { setRandomData("http://nas/t$it", 100) } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @After
    fun release() {
        scope.cancel()
        player.release()
        cache.release()
    }

    private fun id(n: Int) = MediaId(ServerId("s"), "t$n")

    private fun items(vararg n: Int) =
        n.map { Track(id(it), "Track $it", emptyList(), 3.minutes).toMediaItem(null).withTrackUri()!! }

    private fun prefetcher(upstream: DataSource.Factory = DataSource.Factory { FakeDataSource(server) }) =
        Prefetcher(player, scope, Dispatchers.IO, resolver, downloads, cache, upstream, network).also { it.start() }

    private fun cached() = (1..4).filter { cache.isFullyCached(originalCacheKey(id(it))) }

    private fun requested() = repository.streamRequests.map { it.first.itemId }

    /** Plays [items] from the first one and waits until [done]. */
    private fun play(
        vararg n: Int,
        done: () -> Boolean,
    ) {
        player.setMediaItems(items(*n))
        player.prepare()
        player.play()
        TestPlayerRunHelper.run(player).untilPendingCommandsAreFullyHandled()
        RobolectricUtil.runMainLooperUntil(done)
    }

    @Test
    fun `on Wi-Fi the next two tracks are cached while the first plays`() {
        prefetcher()

        play(1, 2, 3, 4) { cached().size == 2 }

        assertThat(cached()).containsExactly(2, 3)
        assertThat(requested()).containsExactly("t2", "t3").inOrder()
    }

    @Test
    fun `on a metered network only the next track is cached`() {
        network.metered = true
        prefetcher()

        play(1, 2, 3, 4) { cached().isNotEmpty() }

        assertThat(cached()).containsExactly(2)
    }

    @Test
    fun `the next tracks follow the shuffled play order`() {
        prefetcher()
        player.setMediaItems(items(1, 2, 3, 4))
        player.setShuffleOrder(DefaultShuffleOrder(intArrayOf(0, 3, 1, 2), 0))
        player.shuffleModeEnabled = true
        player.prepare()
        player.play()
        TestPlayerRunHelper.run(player).untilPendingCommandsAreFullyHandled()
        RobolectricUtil.runMainLooperUntil { cached().size == 2 }

        assertThat(requested()).containsExactly("t4", "t2").inOrder()
    }

    @Test
    fun `a downloaded or already cached track is skipped without asking the server`() {
        downloads.downloads.value =
            listOf(TrackDownload(Track(id(2), "Track 2", emptyList(), 3.minutes), DownloadState.Completed))
        prefetcher()

        play(1, 2, 3, 4) { cached().isNotEmpty() }

        assertThat(cached()).containsExactly(3)
        assertThat(requested()).containsExactly("t3")
    }

    @Test
    fun `converted streams are not prefetched`() {
        repository.playMethod = PlayMethod.Transcode
        prefetcher()

        play(1, 2, 3) { requested().size == 2 }

        assertThat(cache.cacheSpace).isEqualTo(0)
    }

    @Test
    fun `nothing is fetched for a queue that is not playing (e g restored after a restart)`() {
        prefetcher()

        player.setMediaItems(items(1, 2, 3))
        TestPlayerRunHelper.run(player).untilPendingCommandsAreFullyHandled()

        assertThat(requested()).isEmpty()
    }

    @Test
    fun `nothing is fetched offline`() {
        network.online.value = false
        prefetcher()

        player.setMediaItems(items(1, 2, 3))
        player.prepare()
        player.play()
        TestPlayerRunHelper.run(player).untilPendingCommandsAreFullyHandled()

        assertThat(requested()).isEmpty()
    }

    @Test
    fun `the player opening a track stops its prefetch`() {
        val opened = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        // A server that never answers until the prefetch is stopped.
        val stalled =
            DataSource.Factory {
                object : BaseDataSource(true) {
                    override fun open(dataSpec: DataSpec): Long {
                        opened.countDown()
                        try {
                            Thread.sleep(60_000)
                        } catch (e: InterruptedException) {
                            interrupted.countDown()
                            throw java.io.InterruptedIOException()
                        }
                        return 0
                    }

                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ) = 0

                    override fun getUri(): Uri? = null

                    override fun close() = Unit
                }
            }
        val prefetcher = prefetcher(stalled)
        player.setMediaItems(items(1, 2, 3))
        player.prepare()
        player.play()
        TestPlayerRunHelper.run(player).untilPendingCommandsAreFullyHandled()
        assertThat(opened.await(5, TimeUnit.SECONDS)).isTrue()

        prefetcher.cancel(id(2))

        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue()
    }
}
