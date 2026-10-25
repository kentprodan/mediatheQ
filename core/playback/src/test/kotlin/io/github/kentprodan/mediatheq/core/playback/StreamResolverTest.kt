package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: android.net.Uri in the track scheme helpers.
@RunWith(RobolectricTestRunner::class)
class StreamResolverTest {
    private val repository = FakePlaybackRepository()
    private val network = FakeNetworkMonitor()
    private val resolver = StreamResolver(repository, { setOf("audio/flac") }, network)
    private val itemId = MediaId(ServerId("server-1"), "item:with:colons")

    @Test
    fun `one stream per track while the network kind stays the same`() =
        runTest {
            val first = resolver.resolve(itemId)

            assertThat(resolver.resolve(itemId)).isEqualTo(first)
            assertThat(repository.streamRequests).hasSize(1)
            assertThat(resolver.current(itemId)).isEqualTo((first as MqResult.Success).value)
        }

    @Test
    fun `a fresh load after the network became metered asks for the reduced stream`() =
        runTest {
            resolver.resolve(itemId)
            network.metered = true

            resolver.resolve(itemId)

            assertThat(
                repository.streamRequests.map { it.second.maxStreamingBitrate },
            ).containsExactly(null, 256_000).inOrder()
        }

    @Test
    fun `reading on mid-file keeps the stream even if the network changed (never switch format mid-track)`() =
        runTest {
            val first = resolver.resolve(itemId)
            network.metered = true

            assertThat(resolver.resolve(itemId, continuing = true)).isEqualTo(first)
            assertThat(repository.streamRequests).hasSize(1)
        }

    @Test
    fun `a failure is not remembered`() =
        runTest {
            repository.failure = MqResult.Failure(MqError.Unauthorized)

            assertThat(resolver.resolve(itemId)).isEqualTo(MqResult.Failure(MqError.Unauthorized))
            assertThat(resolver.current(itemId)).isNull()
        }

    @Test
    fun `reading on after the playing download was removed asks for the original, also on a metered network`() =
        runTest {
            resolver.playLocal(itemId)
            network.metered = true

            resolver.resolve(itemId, continuing = true)

            assertThat(
                repository.streamRequests
                    .single()
                    .second.maxStreamingBitrate,
            ).isNull()
        }

    @Test
    fun `a download asks for the original even on a metered network, and is not remembered for the player`() =
        runTest {
            network.metered = true

            val result = resolver.downloadStream(itemId)

            assertThat(result).isInstanceOf(MqResult.Success::class.java)
            assertThat(
                repository.streamRequests
                    .single()
                    .second.maxStreamingBitrate,
            ).isNull()
            assertThat(resolver.current(itemId)).isNull()
        }

    @Test
    fun `a download that the server would convert fails (no converted downloads)`() =
        runTest {
            repository.playMethod = PlayMethod.Transcode

            assertThat(resolver.downloadStream(itemId)).isInstanceOf(MqResult.Failure::class.java)
        }

    @Test
    fun `a track playing from its download reports as a direct play of its own session`() {
        resolver.playLocal(itemId)

        val stream = resolver.current(itemId)!!
        assertThat(stream.playMethod).isEqualTo(PlayMethod.DirectPlay)
        assertThat(stream.mediaSourceId).isEqualTo(itemId.itemId)
        assertThat(stream.playSessionId).isNotEmpty()
        assertThat(repository.streamRequests).isEmpty()
    }

    @Test
    fun `after the download is gone the track asks the server again`() =
        runTest {
            resolver.playLocal(itemId)

            resolver.resolve(itemId)

            assertThat(repository.streamRequests).hasSize(1)
            assertThat(resolver.current(itemId)?.url).isEqualTo("http://nas/${itemId.itemId}")
        }

    @Test
    fun `a prefetch never replaces the stream the player knows, even after the network changed`() =
        runTest {
            val playing = (resolver.resolve(itemId) as MqResult.Success).value
            network.metered = true

            assertThat(resolver.prefetchStream(itemId)).isEqualTo(MqResult.Success(playing))
            assertThat(resolver.current(itemId)).isEqualTo(playing)
            assertThat(repository.streamRequests).hasSize(1)
        }

    @Test
    fun `a prefetched stream is the one the player then uses`() =
        runTest {
            val prefetched = (resolver.prefetchStream(itemId) as MqResult.Success).value

            assertThat(resolver.resolve(itemId)).isEqualTo(MqResult.Success(prefetched))
            assertThat(repository.streamRequests).hasSize(1)
        }

    @Test
    fun `track uris round-trip ids with colons, other uris are not tracks`() {
        assertThat(trackUri(itemId).trackId()).isEqualTo(itemId)
        assertThat(Uri.parse("http://nas/Audio/x/stream").trackId()).isNull()
    }
}
