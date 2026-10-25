package io.github.kentprodan.mediatheq.core.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeMediaSourceFactory
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The player on an HLS transcode (R15), against the playlists recorded from Jellyfin 12.3.0: every request carries the
 * auth header and no URL carries the token; the track seeks, segment by segment.
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
class HlsStreamTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val itemId = MediaId(ServerId("s"), "bb3a3eca20f7fe27bfba701c68fc454b")
    private val master =
        "http://nas/audio/bb3a3eca-20f7-fe27-bfba-701c68fc454b/master.m3u8?DeviceId=test-device-id" +
            "&SegmentContainer=mp4&PlaySessionId=1649c76af3da475fba26af9f3cc04bac"

    /** The current auth header; a new login changes it. */
    private var auth = mapOf("Authorization" to "MediaBrowser Token=\"token-1\"")

    /** Every request; playlists and the init segment answer with the fixtures, media segments with 404. */
    private val requests = CopyOnWriteArrayList<Request>()
    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val path = request.url.encodedPath
                val body =
                    when {
                        path.endsWith("master.m3u8") -> fixture("master.m3u8").encodeToByteArray()

                        path.endsWith("main.m3u8") -> fixture("variant.m3u8").encodeToByteArray().also { onPlaylists() }

                        // The recorded init segment: the Opus track's setup, no audio.
                        path.endsWith("-1.mp4") -> javaClass.getResource("/hls/init.mp4")!!.readBytes()

                        else -> ByteArray(0)
                    }
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (body.isEmpty()) 404 else 200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }.build()

    private var choice: suspend () -> StreamSource = { StreamSource.Hls(master) }

    /** Runs when the media playlist is served, e.g. to sign in again between the playlist and its segments. */
    private var onPlaylists: () -> Unit = {}
    private val opened = mutableListOf<MediaId>()
    private val player: ExoPlayer =
        TestExoPlayerBuilder(context)
            .setMediaSourceFactory(
                StreamMediaSourceFactory(
                    FakeMediaSourceFactory(),
                    client,
                    choose = { choice() },
                    headers = { auth },
                    dispatcher = Dispatchers.Unconfined,
                    onOpen = { opened += it },
                ),
            ).build()

    @After
    fun release() = player.release()

    private fun fixture(name: String) = javaClass.getResource("/hls/$name")!!.readText()

    private fun play(startMs: Long = 0) {
        player.setMediaItem(
            MediaItem
                .Builder()
                .setMediaId(itemId.toString())
                .setUri(trackUri(itemId))
                .build(),
            startMs,
        )
        player.prepare()
    }

    private fun window() = player.currentTimeline.getWindow(0, Timeline.Window())

    @Test
    fun `the playlists load with the auth header and the track becomes seekable`() {
        play()

        RobolectricUtil.runMainLooperUntil { !player.currentTimeline.isEmpty && window().isSeekable }

        assertThat(
            requests.map { it.url.encodedPath.substringAfterLast('/') },
        ).containsAtLeast("master.m3u8", "main.m3u8")
        requests.forEach { assertThat(it.header("Authorization")).isEqualTo("MediaBrowser Token=\"token-1\"") }
        assertThat(window().durationMs).isEqualTo(9_000)
        assertThat(opened).containsExactly(itemId)
    }

    @Test
    fun `segments are asked with the auth header, and a start at 4 s asks for the segment it lands in`() {
        // Starting at a position is a seek; segments here answer 404, so the player stops after the first ones.
        play(startMs = 4_000)

        RobolectricUtil.runMainLooperUntil { requests.any { it.url.encodedPath.endsWith("hls1/main/1.mp4") } }
        val segments =
            requests
                .filter {
                    "/hls1/main/" in it.url.encodedPath
                }.map { it.url.encodedPath.substringAfterLast('/') }
        assertThat(segments).contains("-1.mp4")
        assertThat(segments).doesNotContain("0.mp4")
        requests.forEach { assertThat(it.header("Authorization")).isEqualTo("MediaBrowser Token=\"token-1\"") }
    }

    @Test
    fun `R1 R10 no request url carries the token`() {
        play(startMs = 4_000)

        RobolectricUtil.runMainLooperUntil { requests.any { "/hls1/main/" in it.url.encodedPath } }

        requests.forEach {
            assertThat(it.url.toString().lowercase()).doesNotContain("apikey")
            assertThat(it.url.toString().lowercase()).doesNotContain("api_key")
        }
    }

    @Test
    fun `the queue item stays the mediatheq item, never the resolved url`() {
        play()

        RobolectricUtil.runMainLooperUntil { !player.currentTimeline.isEmpty && window().isSeekable }

        assertThat(player.currentMediaItem?.localConfiguration?.uri).isEqualTo(trackUri(itemId))
    }

    @Test
    fun `a failed choice plays through the item's own source`() {
        choice = { error("server unreachable") }

        play()

        RobolectricUtil.runMainLooperUntil { !player.currentTimeline.isEmpty && !window().isPlaceholder }
        assertThat(requests).isEmpty()
        assertThat(opened).isEmpty()
    }

    @Test
    fun `R9 a new login meanwhile gives the segments the new token`() {
        onPlaylists = { auth = mapOf("Authorization" to "MediaBrowser Token=\"token-2\"") }

        play(startMs = 4_000)

        RobolectricUtil.runMainLooperUntil { requests.any { "/hls1/main/" in it.url.encodedPath } }
        requests
            .filter { "/hls1/main/" in it.url.encodedPath }
            .forEach { assertThat(it.header("Authorization")).isEqualTo("MediaBrowser Token=\"token-2\"") }
    }
}
