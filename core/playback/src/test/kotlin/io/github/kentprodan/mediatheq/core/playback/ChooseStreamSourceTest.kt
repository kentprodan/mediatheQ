package io.github.kentprodan.mediatheq.core.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files
import kotlin.time.Duration.Companion.minutes

// Robolectric: the streaming cache index lives in Media3's SQLite database.
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class ChooseStreamSourceTest {
    private val itemId = MediaId(ServerId("s"), "t1")
    private val repository = FakePlaybackRepository()
    private val network = FakeNetworkMonitor()
    private val resolver =
        StreamResolver(repository, { setOf("audio/flac") }, network, FakeAudioPreferencesRepository())
    private val downloads = FakeDownloadRepository()
    private val cache =
        SimpleCache(
            Files.createTempDirectory("media").toFile(),
            ResizableLruCacheEvictor(),
            StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext()),
        )

    @After
    fun release() = cache.release()

    private suspend fun choose() = chooseStreamSource(itemId, resolver, downloads, cache, network::isOnline)

    @Test
    fun `an HLS transcode plays as HLS, with the auth header and the stream url`() =
        runTest {
            repository.playMethod = PlayMethod.Transcode
            repository.segmented = true

            assertThat(choose())
                .isEqualTo(StreamSource.Hls(resolver.current(itemId)!!.url, mapOf("Authorization" to "fake")))
            // Reports find this stream, with its play session (PlaybackReporter asks the resolver).
            assertThat(resolver.current(itemId)?.playSessionId).isEqualTo("session-t1")
            assertThat(resolver.current(itemId)?.canSeek).isTrue()
        }

    @Test
    fun `a file and a progressive transcode play through the item's own data source`() =
        runTest {
            assertThat(choose()).isEqualTo(StreamSource.Progressive)

            repository.playMethod = PlayMethod.Transcode
            network.metered = true
            assertThat(choose()).isEqualTo(StreamSource.Progressive)
        }

    @Test
    fun `a downloaded track never asks the server`() =
        runTest {
            downloads.downloads.value =
                listOf(TrackDownload(Track(itemId, "t", emptyList(), 3.minutes), DownloadState.Completed))

            assertThat(choose()).isEqualTo(StreamSource.Progressive)
            assertThat(repository.streamRequests).isEmpty()
        }

    @Test
    fun `offline nothing is asked, the data source fails as before`() =
        runTest {
            network.online.value = false

            assertThat(choose()).isEqualTo(StreamSource.Progressive)
            assertThat(repository.streamRequests).isEmpty()
        }

    @Test
    fun `a failed resolve leaves the retries to the data source`() =
        runTest {
            repository.failure = MqResult.Failure(MqError.Network)

            assertThat(choose()).isEqualTo(StreamSource.Progressive)
        }
}
