package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackRepository
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class StreamDataSourceTest {
    private val itemId = MediaId(ServerId("server-1"), "t1")
    private val bytes = ByteArray(1_000) { it.toByte() }
    private val repository = FakePlaybackRepository()
    private val network = FakeNetworkMonitor()
    private val resolver = StreamResolver(repository, { setOf("audio/flac") }, network)
    private val downloads = FakeDownloadRepository()
    private val cache =
        SimpleCache(
            Files.createTempDirectory("downloads").toFile(),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext()),
        )
    private val factory = streamDataSourceFactory(OkHttpClient(), resolver, downloads, cache)

    @After
    fun release() {
        cache.release()
    }

    @Test
    fun `a downloaded track plays from the device without the server, also offline`() {
        download()
        network.online.value = false

        val read = DataSourceUtil.readToEnd(factory.createDataSource().apply { open(DataSpec(trackUri(itemId))) })

        assertThat(read).isEqualTo(bytes)
        assertThat(repository.streamRequests).isEmpty()
        assertThat(resolver.current(itemId)?.playSessionId).isNotEmpty()
    }

    @Test
    fun `offline a track that is not downloaded fails without asking the server`() {
        network.online.value = false

        assertThrows(StreamUnavailableException::class.java) {
            factory.createDataSource().open(DataSpec(trackUri(itemId)))
        }
        assertThat(repository.streamRequests).isEmpty()
    }

    /** What `:core:download` leaves behind: the whole file under the track's key, and the track completed. */
    private fun download() {
        val upstream = FakeDataSource(FakeDataSet().setData(trackUri(itemId), bytes))
        val spec =
            DataSpec
                .Builder()
                .setUri(trackUri(itemId))
                .setKey(downloadCacheKey(itemId))
                .build()
        CacheWriter(CacheDataSource(cache, upstream), spec, null, null).cache()
        downloads.downloads.value =
            listOf(TrackDownload(Track(itemId, "Track", emptyList(), 3.minutes), DownloadState.Completed))
    }
}
