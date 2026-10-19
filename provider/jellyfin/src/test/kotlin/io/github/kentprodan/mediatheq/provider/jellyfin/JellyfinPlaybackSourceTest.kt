package io.github.kentprodan.mediatheq.provider.jellyfin

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.errorOrNull
import io.github.kentprodan.mediatheq.core.model.DeviceProfile
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.PlayMethod
import io.github.kentprodan.mediatheq.core.model.PlaybackReport
import io.github.kentprodan.mediatheq.core.model.PlaybackStream
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.TranscodingTarget
import io.github.kentprodan.mediatheq.core.network.MediatheqJson
import io.github.kentprodan.mediatheq.core.network.mediatheqOkHttpClient
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** Contract tests against `PlaybackInfo` answers recorded from Jellyfin 12.2.0 (token and ids replaced). */
class JellyfinPlaybackSourceTest {
    @get:Rule
    val serverRule = MockWebServerRule()
    private val mockServer get() = serverRule.server

    private val source =
        JellyfinPlaybackSource(
            mediatheqOkHttpClient(),
            ClientInfo("mediatheQ", "Pixel 9", "0.1.0"),
            MediatheqJson,
            Dispatchers.Unconfined,
        )
    private val serverId = ServerId("0f1e2d3c4b5a69788796a5b4c3d2e1f0")
    private val itemId = MediaId(serverId, "bb3a3eca20f7fe27bfba701c68fc454b")

    private fun server(basePath: String = "/") =
        SignedInServer(
            ServerConnection(mockServer.url(basePath).toString(), "device-1", accessToken = "token-1"),
            serverId,
            userId = "11111111222233334444555555555555",
        )

    private val opus = TranscodingTarget("ogg", "opus", 256_000, 2)
    private val wifi =
        DeviceProfile(listOf("flac", "mp3", "ogg"), listOf("flac", "mp3", "opus"), opus, maxStreamingBitrate = null)
    private val metered = wifi.copy(maxStreamingBitrate = 256_000)

    private fun enqueue(path: String) =
        mockServer.enqueue(MockResponse(body = javaClass.getResource("/jellyfin/$path")!!.readText()))

    private fun body(): JsonObject = Json.parseToJsonElement(mockServer.takeRequest().body!!.utf8()).jsonObject

    @Test
    fun `unmetered network gets the original file, seekable, without token in the url`() =
        runTest {
            enqueue("items_playbackinfo/direct_play.json")

            val stream = (source.stream(server(), itemId, wifi) as MqResult.Success).value

            assertThat(stream.playMethod).isEqualTo(PlayMethod.DirectPlay)
            assertThat(stream.canSeek).isTrue()
            assertThat(stream.url).startsWith(mockServer.url("/Audio/${itemId.itemId}/stream").toString())
            assertThat(stream.url).contains("static=true")
            assertThat(stream.url).contains("playSessionId=${stream.playSessionId}")
            assertThat(stream.url.lowercase()).doesNotContain("apikey")
        }

    @Test
    fun `the PlaybackInfo request carries the exact profile`() =
        runTest {
            enqueue("items_playbackinfo/transcode.json")

            source.stream(server(), itemId, metered)

            val request = mockServer.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/Items/${itemId.itemId}/PlaybackInfo")
            assertThat(request.headers["Authorization"]).endsWith(", Token=\"token-1\"")
            assertThat(Json.parseToJsonElement(request.body!!.utf8()))
                .isEqualTo(
                    Json.parseToJsonElement(
                        """
                        {"UserId":"11111111222233334444555555555555","MaxStreamingBitrate":256000,
                         "DeviceProfile":{"Name":"mediatheQ","MaxStreamingBitrate":256000,
                          "MusicStreamingTranscodingBitrate":256000,
                          "DirectPlayProfiles":[{"Type":"Audio","Container":"flac,mp3,ogg","AudioCodec":"flac,mp3,opus"}],
                          "TranscodingProfiles":[{"Type":"Audio","Container":"ogg","AudioCodec":"opus","Protocol":"http",
                            "Context":"Streaming","MaxAudioChannels":"2"}],
                          "CodecProfiles":[],"ContainerProfiles":[],"SubtitleProfiles":[]},
                         "AutoOpenLiveStream":false}
                        """.trimIndent(),
                    ),
                )
        }

    @Test
    fun `an unlimited profile sends a large explicit cap, never the server default`() =
        runTest {
            enqueue("items_playbackinfo/direct_play.json")

            source.stream(server(), itemId, wifi)

            assertThat(body()["MaxStreamingBitrate"].toString()).isEqualTo("1000000000")
        }

    @Test
    fun `metered network gets the 256 kbps Opus transcode, unseekable, token stripped`() =
        runTest {
            enqueue("items_playbackinfo/transcode.json")

            val stream = (source.stream(server(), itemId, metered) as MqResult.Success).value

            assertThat(stream.playMethod).isEqualTo(PlayMethod.Transcode)
            assertThat(stream.canSeek).isFalse()
            assertThat(stream.url).contains("/stream.ogg?")
            assertThat(stream.url).contains("AudioCodec=opus")
            assertThat(stream.url).contains("AudioBitrate=256000")
            assertThat(stream.url).contains("PlaySessionId=${stream.playSessionId}")
            assertThat(stream.url).doesNotContain("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            assertThat(stream.url.lowercase()).doesNotContain("apikey")
        }

    @Test
    fun `a server under a base path keeps it once`() {
        assertThat(
            serverUrl("http://nas/jellyfin", "/audio/x/stream.ogg?a=1"),
        ).isEqualTo("http://nas/jellyfin/audio/x/stream.ogg?a=1")
        assertThat(
            serverUrl("http://nas/jellyfin/", "/jellyfin/audio/x/stream.ogg"),
        ).isEqualTo("http://nas/jellyfin/audio/x/stream.ogg")
        assertThat(serverUrl("http://nas:8096", "/audio/x")).isEqualTo("http://nas:8096/audio/x")
    }

    @Test
    fun `token parameters are removed in any spelling`() {
        assertThat(withoutApiKey("/a?x=1&ApiKey=t&api_key=t&APIKEY=t&y=2")).isEqualTo("/a?x=1&y=2")
        assertThat(withoutApiKey("/a?ApiKey=t")).isEqualTo("/a")
        assertThat(withoutApiKey("/a")).isEqualTo("/a")
    }

    @Test
    fun `stream headers authenticate with the MediaBrowser header (R1)`() {
        val headers = source.streamHeaders(server())

        assertThat(headers.keys).containsExactly("Authorization")
        assertThat(headers.getValue("Authorization")).startsWith("MediaBrowser ")
        assertThat(headers.getValue("Authorization")).endsWith(", Token=\"token-1\"")
    }

    @Test
    fun `a playback error code fails the request`() =
        runTest {
            mockServer.enqueue(MockResponse(body = """{"MediaSources":[],"ErrorCode":"NoCompatibleStream"}"""))

            assertThat(source.stream(server(), itemId, wifi).errorOrNull()).isInstanceOf(MqError.Client::class.java)
        }

    @Test
    fun `a 401 is Unauthorized`() =
        runTest {
            mockServer.enqueue(MockResponse(code = 401))

            assertThat(source.stream(server(), itemId, wifi).errorOrNull()).isEqualTo(MqError.Unauthorized)
        }

    @Test
    fun `reports carry the play session, position in ticks and play method`() =
        runTest {
            repeat(3) { mockServer.enqueue(MockResponse(code = 204)) }
            val stream = PlaybackStream(itemId, "u", PlayMethod.Transcode, "src-1", "session-1", canSeek = false)
            val report = PlaybackReport(stream, position = 12.seconds, isPaused = true)

            source.reportStart(server(), report)
            source.reportProgress(server(), report)
            source.reportStopped(server(), report, failed = false)

            val expectedPlaying =
                """
                {"ItemId":"${itemId.itemId}","MediaSourceId":"src-1","PlaySessionId":"session-1","PositionTicks":120000000,
                 "IsPaused":true,"PlayMethod":"Transcode","CanSeek":false}
                """.trimIndent()
            listOf("/Sessions/Playing", "/Sessions/Playing/Progress").forEach { path ->
                val request = mockServer.takeRequest()
                assertThat(request.url.encodedPath).isEqualTo(path)
                assertThat(
                    Json.parseToJsonElement(request.body!!.utf8()),
                ).isEqualTo(Json.parseToJsonElement(expectedPlaying))
            }
            val stopped = mockServer.takeRequest()
            assertThat(stopped.url.encodedPath).isEqualTo("/Sessions/Playing/Stopped")
            assertThat(Json.parseToJsonElement(stopped.body!!.utf8()))
                .isEqualTo(
                    Json.parseToJsonElement(
                        """{"ItemId":"${itemId.itemId}","MediaSourceId":"src-1","PlaySessionId":"session-1","PositionTicks":120000000,"Failed":false}""",
                    ),
                )
        }
}
