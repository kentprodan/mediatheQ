package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.data.TrackDownload
import io.github.kentprodan.mediatheq.core.model.DownloadState
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.testing.FakeAudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackRepository
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
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
    private val audioPreferences = FakeAudioPreferencesRepository()
    private val resolver = StreamResolver(repository, { setOf("audio/flac") }, network, audioPreferences)
    private val downloads = FakeDownloadRepository()

    /** Every request that reaches the network, answered with 10 bytes instead (without a length when [chunked]). */
    private val requests = mutableListOf<Request>()
    private var chunked = false
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
                    .apply { if (!chunked) header("Content-Length", "10") }
                    .body(
                        if (chunked) {
                            Buffer().write(ByteArray(10)).asResponseBody(null, -1)
                        } else {
                            ByteArray(10).toResponseBody()
                        },
                    ).build()
            }.build()
    private val database = StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext())
    private val cache = SimpleCache(Files.createTempDirectory("downloads").toFile(), NoOpCacheEvictor(), database)
    private val streamingCache =
        SimpleCache(
            Files.createTempDirectory("media").toFile(),
            ResizableLruCacheEvictor(gibibytes(2)),
            database,
        )
    private val factory =
        streamDataSourceFactory(client, resolver, downloads, cache, streamingCache, network::isOnline)

    @After
    fun release() {
        cache.release()
        streamingCache.release()
    }

    /** Reads the track to the end the way the player loads it. */
    private fun play(): ByteArray {
        val spec =
            DataSpec
                .Builder()
                .setUri(trackUri(itemId))
                .setFlags(DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN or DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                .build()
        val source = factory.createDataSource()
        return try {
            DataSourceUtil.readToEnd(source.apply { open(spec) })
        } finally {
            source.close()
        }
    }

    @Test
    fun `replaying a streamed original needs no network, also offline`() {
        play()
        network.online.value = false

        assertThat(play()).hasLength(10)
        assertThat(requests).hasSize(1)
        assertThat(repository.streamRequests).hasSize(1)
        // Reported as a direct play of its own session, not the server stream of the first play.
        assertThat(resolver.current(itemId)?.url).isEmpty()
    }

    @Test
    fun `a converted stream is never kept in the cache`() {
        repository.playMethod = PlayMethod.Transcode
        chunked = true

        play()

        assertThat(streamingCache.cacheSpace).isEqualTo(0)
    }

    @Test
    fun `a partial copy of a file changed on the server is dropped, never mixed with the new one`() {
        play()
        // The cache knows 20 bytes but holds 10: it was cut, and the server now says the file has 10.
        streamingCache.applyContentMetadataMutations(
            originalCacheKey(itemId),
            ContentMetadataMutations().also { ContentMetadataMutations.setContentLength(it, 20) },
        )
        repository.sizeBytes = 10

        assertThat(play()).hasLength(10)
        assertThat(requests).hasSize(2)
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
    fun `a replay after the network turned metered reuses the stream the item started with`() {
        play()
        network.metered = true
        repository.playMethod = PlayMethod.Transcode
        repository.segmented = true

        play()

        assertThat(repository.streamRequests).hasSize(1)
    }

    @Test
    fun `a playlist never reaches the file source, it fails clearly instead`() {
        repository.playMethod = PlayMethod.Transcode
        repository.segmented = true

        assertThrows(StreamUnavailableException::class.java) { play() }
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
