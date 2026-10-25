package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
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
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

    /** Every request that reaches the network, answered with a few bytes instead. */
    private val requests = mutableListOf<Request>()
    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                requests += chain.request()
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ByteArray(10).toResponseBody())
                    .build()
            }.build()
    private val cache =
        SimpleCache(
            Files.createTempDirectory("downloads").toFile(),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext()),
        )
    private val factory = streamDataSourceFactory(client, resolver, downloads, cache, network::isOnline)

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
    fun `a track still downloading streams from the server, never from its partial copy`() {
        download(DownloadState.Downloading(0.5f))

        val read = DataSourceUtil.readToEnd(factory.createDataSource().apply { open(DataSpec(trackUri(itemId))) })

        assertThat(read).hasLength(10)
        assertThat(repository.streamRequests).hasSize(1)
        assertThat(requests.single().url.toString()).isEqualTo("http://nas/${itemId.itemId}")
    }

    @Test
    fun `a download reads the original file of its key, with the auth header`() {
        val source = downloadDataSourceFactory(client, resolver).createDataSource()

        source.open(
            DataSpec
                .Builder()
                .setUri("mediatheq://download")
                .setKey(downloadCacheKey(itemId))
                .build(),
        )

        assertThat(requests.single().url.toString()).isEqualTo("http://nas/${itemId.itemId}")
        assertThat(requests.single().header("Authorization")).isEqualTo("fake")
    }

    @Test
    fun `a download without a track key fails`() {
        val source = downloadDataSourceFactory(client, resolver).createDataSource()

        assertThrows(
            StreamUnavailableException::class.java,
        ) { source.open(DataSpec(Uri.parse("mediatheq://download"))) }
        assertThat(requests).isEmpty()
    }

    @Test
    fun `offline a track that is not downloaded fails without asking the server`() {
        network.online.value = false

        assertThrows(OfflineException::class.java) {
            factory.createDataSource().open(DataSpec(trackUri(itemId)))
        }
        assertThat(repository.streamRequests).isEmpty()
        assertThat(requests).isEmpty()
    }

    /** What `:core:download` leaves behind: the file under the track's key, and the track in [state]. */
    private fun download(state: DownloadState = DownloadState.Completed) {
        val upstream = FakeDataSource(FakeDataSet().setData(trackUri(itemId), bytes))
        val spec =
            DataSpec
                .Builder()
                .setUri(trackUri(itemId))
                .setKey(downloadCacheKey(itemId))
                .build()
        CacheWriter(CacheDataSource(cache, upstream), spec, null, null).cache()
        downloads.downloads.value =
            listOf(TrackDownload(Track(itemId, "Track", emptyList(), 3.minutes), state))
    }
}
