package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.NetworkMonitor
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: android.net.Uri in the track scheme helpers.
@RunWith(RobolectricTestRunner::class)
class StreamResolverTest {
    private val repository = FakePlaybackRepository()
    private var metered = false
    private val resolver =
        StreamResolver(
            repository,
            { setOf("audio/flac") },
            object : NetworkMonitor {
                override fun isMetered() = metered
            },
        )
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
            metered = true

            resolver.resolve(itemId)

            assertThat(
                repository.streamRequests.map { it.second.maxStreamingBitrate },
            ).containsExactly(null, 256_000).inOrder()
        }

    @Test
    fun `reading on mid-file keeps the stream even if the network changed (never switch format mid-track)`() =
        runTest {
            val first = resolver.resolve(itemId)
            metered = true

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
    fun `track uris round-trip ids with colons, other uris are not tracks`() {
        assertThat(trackUri(itemId).trackId()).isEqualTo(itemId)
        assertThat(Uri.parse("http://nas/Audio/x/stream").trackId()).isNull()
    }
}
