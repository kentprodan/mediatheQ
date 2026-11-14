package io.github.kentprodan.mediatheq.core.playback

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.data.StreamingQuality
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.ResolvedTracks
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.FakeAudioPreferencesRepository
import io.github.kentprodan.mediatheq.core.testing.FakeNetworkMonitor
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

// Robolectric: android.net.Uri in the track scheme helpers.
@RunWith(RobolectricTestRunner::class)
class StreamResolverTest {
    private val repository = FakePlaybackRepository()
    private val network = FakeNetworkMonitor()
    private val audioPreferences = FakeAudioPreferencesRepository()
    private val resolver = StreamResolver(repository, { setOf("audio/flac") }, network, audioPreferences)
    private val itemId = MediaId(ServerId("server-1"), "item:with:colons")

    @Test
    fun `one stream per track while the quality stays the same`() =
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
    fun `a download asks for the original, or one converted Opus file when the device cannot play it`() =
        runTest {
            resolver.downloadStream(itemId)

            assertThat(
                repository.streamRequests
                    .single()
                    .second.transcoding,
            ).isEqualTo(DeviceProfileBuilder.OPUS_256_FILE)
        }

    @Test
    fun `a download asks at the Download quality of now and knows its bitrate`() =
        runTest {
            audioPreferences.setDownloadQuality(StreamingQuality.Kbps192)
            repository.playMethod = PlayMethod.Transcode

            val result = resolver.downloadStream(itemId) as MqResult.Success

            val profile = repository.streamRequests.single().second
            assertThat(profile.directPlayCodecs).isEmpty()
            assertThat(profile.transcoding.bitrate).isEqualTo(192_000)
            assertThat(result.value.convertedKbps).isEqualTo(192)
        }

    @Test
    fun `a download the server converts to one file is accepted`() =
        runTest {
            repository.playMethod = PlayMethod.Transcode

            val result = resolver.downloadStream(itemId)

            assertThat((result as MqResult.Success).value.stream.playMethod).isEqualTo(PlayMethod.Transcode)
            assertThat(result.value.convertedKbps).isEqualTo(256)
        }

    @Test
    fun `a download the server answers with a playlist fails, a download is one file`() =
        runTest {
            repository.playMethod = PlayMethod.Transcode
            repository.segmented = true

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
    fun `a converted download still reports as a direct play, but is known as converted`() {
        resolver.playLocal(itemId, convertedKbps = 256)

        assertThat(resolver.current(itemId)?.playMethod).isEqualTo(PlayMethod.DirectPlay)
        assertThat(resolver.isConvertedDownload(itemId)).isTrue()
        assertThat(repository.streamRequests).isEmpty()
    }

    @Test
    fun `an original download or a cached original is not converted`() {
        resolver.playLocal(itemId)

        assertThat(resolver.isConvertedDownload(itemId)).isFalse()
    }

    @Test
    fun `a converted download replacing a local original is known as converted, in the same play session`() {
        resolver.playLocal(itemId)
        val session = resolver.current(itemId)!!.playSessionId

        resolver.playLocal(itemId, convertedKbps = 256)

        assertThat(resolver.isConvertedDownload(itemId)).isTrue()
        assertThat(resolver.current(itemId)!!.playSessionId).isEqualTo(session)
        assertThat(resolver.current(itemId)!!.playMethod).isEqualTo(PlayMethod.DirectPlay)
    }

    @Test
    fun `a cached original replacing a converted download is no longer converted, in the same play session`() {
        resolver.playLocal(itemId, convertedKbps = 256)
        val session = resolver.current(itemId)!!.playSessionId

        resolver.playLocal(itemId)

        assertThat(resolver.isConvertedDownload(itemId)).isFalse()
        assertThat(resolver.current(itemId)!!.playSessionId).isEqualTo(session)
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

    @Test
    fun `a new quality setting gives tracks resolved before a new stream at the next load`() =
        runTest {
            resolver.resolve(itemId)
            audioPreferences.setStreamingQuality(metered = false, StreamingQuality.Kbps128)

            resolver.resolve(itemId)

            assertThat(repository.streamRequests.map { it.second.maxStreamingBitrate })
                .containsExactly(null, 128_000)
                .inOrder()
        }

    @Test
    fun `each network kind uses its own quality`() =
        runTest {
            audioPreferences.setStreamingQuality(metered = true, StreamingQuality.Kbps128)
            network.metered = true

            resolver.resolve(itemId)

            assertThat(
                repository.streamRequests
                    .single()
                    .second.maxStreamingBitrate,
            ).isEqualTo(128_000)
        }

    @Test
    fun `downloads ask for the original whatever the streaming quality`() =
        runTest {
            audioPreferences.setStreamingQuality(metered = false, StreamingQuality.Kbps128)

            resolver.downloadStream(itemId)

            assertThat(
                repository.streamRequests
                    .single()
                    .second.maxStreamingBitrate,
            ).isNull()
        }

    // Dolby Atmos over HDMI (#242, ADR 0039 rule 4).

    @Test
    fun `atmosRequest asks for the original only for an Atmos file the route passes through`() {
        val cap = StreamingQuality.Kbps256
        assertThat(atmosRequest(JOC, setOf(JOC, TRUEHD), cap)).isEqualTo(StreamingQuality.Original)
        assertThat(atmosRequest(JOC, emptySet(), cap)).isEqualTo(cap)
        assertThat(atmosRequest(null, setOf(JOC, TRUEHD), cap)).isEqualTo(cap)
        assertThat(atmosRequest(TRUEHD, setOf(JOC), cap)).isEqualTo(cap)
        assertThat(atmosRequest(JOC, setOf(JOC), StreamingQuality.Original)).isEqualTo(StreamingQuality.Original)
    }

    @Test
    fun `an Atmos track the route passes through is asked for as the original despite the cap`() =
        runTest {
            network.metered = true
            resolver.passthrough = setOf(JOC)

            resolver.resolve(itemId, atmosMime = JOC)

            val profile = repository.streamRequests.single().second
            assertThat(profile.maxStreamingBitrate).isNull()
            assertThat(profile.directPlayCodecs).contains("eac3")
        }

    @Test
    fun `an Atmos track without passthrough follows the cap`() =
        runTest {
            network.metered = true

            resolver.resolve(itemId, atmosMime = JOC)

            val profile = repository.streamRequests.single().second
            assertThat(profile.maxStreamingBitrate).isEqualTo(256_000)
            assertThat(profile.directPlayCodecs).doesNotContain("eac3")
        }

    @Test
    fun `a stereo track follows the cap also on a passthrough route`() =
        runTest {
            network.metered = true
            resolver.passthrough = setOf(JOC, TRUEHD)

            resolver.resolve(itemId)

            assertThat(
                repository.streamRequests
                    .single()
                    .second.maxStreamingBitrate,
            ).isEqualTo(256_000)
        }

    @Test
    fun `a TrueHD Atmos track on a route that passes only JOC follows the cap`() =
        runTest {
            network.metered = true
            resolver.passthrough = setOf(JOC)

            resolver.resolve(itemId, atmosMime = TRUEHD)

            val profile = repository.streamRequests.single().second
            assertThat(profile.maxStreamingBitrate).isEqualTo(256_000)
            assertThat(profile.directPlayCodecs).doesNotContain("truehd")
        }

    @Test
    fun `another route asks again at the next load, even at the same quality`() =
        runTest {
            resolver.passthrough = setOf(TRUEHD)
            resolver.resolve(itemId, atmosMime = TRUEHD)

            resolver.passthrough = emptySet()
            resolver.resolve(itemId, atmosMime = TRUEHD)

            assertThat(repository.streamRequests.map { "truehd" in it.second.directPlayCodecs })
                .containsExactly(true, false)
                .inOrder()
        }

    @Test
    fun `a prefetched Atmos track under the cap asks for the original, and the player reuses it`() =
        runTest {
            network.metered = true
            resolver.passthrough = setOf(JOC)

            resolver.prefetchStream(itemId, atmosMime = JOC)
            resolver.resolve(itemId, atmosMime = JOC)

            assertThat(
                repository.streamRequests
                    .single()
                    .second.maxStreamingBitrate,
            ).isNull()
            assertThat(resolver.quality(itemId)).isEqualTo(StreamingQuality.Original)
        }

    @Test
    fun `a cached TrueHD original plays only where the route passes it through or decodes it`() {
        assertThat(resolver.playsCachedOriginal(itemId)).isTrue()

        // Plain 7.1 TrueHD too: the profile declares truehd for any file while the route passes it through.
        resolver.rememberFile(itemId, TRUEHD)
        resolver.passthrough = setOf(TRUEHD)
        assertThat(resolver.playsCachedOriginal(itemId)).isTrue()

        resolver.passthrough = emptySet()
        assertThat(resolver.playsCachedOriginal(itemId)).isFalse()

        val decoding = StreamResolver(repository, { setOf(TRUEHD) }, network, audioPreferences)
        decoding.rememberFile(itemId, TRUEHD)
        assertThat(decoding.playsCachedOriginal(itemId)).isTrue()
    }

    @Test
    fun `the file source asking without the Atmos type still gets the original on a passthrough route`() =
        runTest {
            network.metered = true
            resolver.passthrough = setOf(JOC)
            resolver.rememberAtmos(itemId, JOC)

            resolver.resolve(itemId, continuing = true)

            assertThat(
                repository.streamRequests
                    .single()
                    .second.maxStreamingBitrate,
            ).isNull()
        }

    @Test
    fun `instant transcoding keeps the original for an Atmos track passing through`() {
        resolver.rememberAtmos(itemId, JOC)
        resolver.passthrough = setOf(JOC)

        assertThat(resolver.requestQuality(itemId, StreamingQuality.Kbps256)).isEqualTo(StreamingQuality.Original)

        resolver.passthrough = emptySet()
        assertThat(resolver.requestQuality(itemId, StreamingQuality.Kbps256)).isEqualTo(StreamingQuality.Kbps256)
    }

    @Test
    fun `a video asks for the video profile from its start, and its stream is known for reports`() =
        runTest {
            val video =
                StreamResolver(
                    repository,
                    { setOf("audio/flac") },
                    network,
                    audioPreferences,
                    videoDecoders = { setOf("video/avc") },
                    trackChooser = { ResolvedTracks(audioIndex = 2, subtitleIndex = 5) },
                )

            val stream = (video.resolveVideo(itemId, 90.seconds) as MqResult.Success).value

            val profile = repository.streamRequests.single().second
            assertThat(profile.videoCodecs).containsExactly("h264")
            assertThat(profile.maxHeight).isNull()
            assertThat(repository.streamChoices.single().startPosition).isEqualTo(90.seconds)
            // The chosen tracks go with the request (#274).
            assertThat(repository.streamChoices.single().audioStreamIndex).isEqualTo(2)
            assertThat(repository.streamChoices.single().subtitleStreamIndex).isEqualTo(5)
            assertThat(video.current(itemId)).isEqualTo(stream)
            // No music quality: the instant transcoder leaves a video alone.
            assertThat(video.quality(itemId)).isNull()
        }

    @Test
    fun `a video on a metered network asks for 480p`() =
        runTest {
            network.metered = true

            resolver.resolveVideo(itemId)

            val profile = repository.streamRequests.single().second
            assertThat(profile.maxHeight).isEqualTo(480)
            assertThat(profile.maxStreamingBitrate).isEqualTo(1_500_000)
        }
}

private const val JOC = "audio/eac3-joc"
private const val TRUEHD = "audio/true-hd"
