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
import io.github.kentprodan.mediatheq.core.model.StreamChoice
import io.github.kentprodan.mediatheq.core.model.SubtitleMethod
import io.github.kentprodan.mediatheq.core.model.SubtitleProfile
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

/** Contract tests against `PlaybackInfo` answers recorded from Jellyfin 12.2.0 and 12.3.0 (token and ids replaced). */
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
            assertThat(stream.sizeBytes).isEqualTo(27_735_057)
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
    fun `a profile with nothing to play as it is sends no direct play profile, so the server always converts`() =
        runTest {
            enqueue("items_playbackinfo/transcode.json")
            val convertOnly =
                DeviceProfile(
                    directPlayContainers = emptyList(),
                    directPlayCodecs = emptyList(),
                    transcoding =
                        TranscodingTarget(
                            container = "ogg",
                            codec = "opus",
                            bitrate = 192_000,
                            maxChannels = 2,
                        ),
                    maxStreamingBitrate = 192_000,
                )

            source.stream(server(), itemId, convertOnly)

            val profile = body()["DeviceProfile"]!!.jsonObject
            assertThat(profile["DirectPlayProfiles"].toString()).isEqualTo("[]")
            assertThat(profile["MusicStreamingTranscodingBitrate"].toString()).isEqualTo("192000")
        }

    @Test
    fun `an unlimited profile sends a large explicit cap, never the server default`() =
        runTest {
            enqueue("items_playbackinfo/direct_play.json")

            source.stream(server(), itemId, wifi)

            assertThat(body()["MaxStreamingBitrate"].toString()).isEqualTo("1000000000")
        }

    @Test
    fun `R11 an Atmos track on a passthrough route is asked uncapped with eac3 and truehd and direct plays`() =
        runTest {
            // Recorded from 12.3.0 (2026-11-13) for an E-AC-3 JOC M4A with this profile (#242, ADR 0039).
            enqueue("items_playbackinfo/passthrough_atmos.json")
            val passthrough =
                DeviceProfile(
                    directPlayContainers = listOf("m4a", "mp4", "mka", "flac"),
                    directPlayCodecs = listOf("flac", "aac", "ac3", "eac3", "truehd"),
                    transcoding = TranscodingTarget("mp4", "opus", 256_000, 2, segmented = true),
                    maxStreamingBitrate = null,
                )

            val stream = (source.stream(server(), itemId, passthrough) as MqResult.Success).value

            val body = body()
            val profile = body["DeviceProfile"]!!.jsonObject
            assertThat(body["MaxStreamingBitrate"].toString()).isEqualTo("1000000000")
            assertThat(profile["MaxStreamingBitrate"].toString()).isEqualTo("1000000000")
            assertThat(profile["MusicStreamingTranscodingBitrate"].toString()).isEqualTo("256000")
            assertThat(profile["DirectPlayProfiles"].toString()).contains("\"AudioCodec\":\"flac,aac,ac3,eac3,truehd\"")
            assertThat(stream.playMethod).isEqualTo(PlayMethod.DirectPlay)
            assertThat(stream.segmented).isFalse()
            assertThat(stream.canSeek).isTrue()
            assertThat(stream.url).contains("static=true")
            assertThat(stream.sizeBytes).isEqualTo(25_612_783)
        }

    @Test
    fun `R10 R11 R12 metered network gets the 256 kbps Opus transcode, unseekable, token stripped`() =
        runTest {
            enqueue("items_playbackinfo/transcode.json")

            val stream = (source.stream(server(), itemId, metered) as MqResult.Success).value

            assertThat(stream.playMethod).isEqualTo(PlayMethod.Transcode)
            assertThat(stream.canSeek).isFalse()
            assertThat(stream.segmented).isFalse()
            assertThat(stream.url).contains("/stream.ogg?")
            assertThat(stream.url).contains("AudioCodec=opus")
            assertThat(stream.url).contains("AudioBitrate=256000")
            assertThat(stream.url).contains("PlaySessionId=${stream.playSessionId}")
            assertThat(stream.url).doesNotContain("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            assertThat(stream.url.lowercase()).doesNotContain("apikey")
        }

    @Test
    fun `R15 an HLS transcode is a seekable playlist, token stripped, play session kept`() =
        runTest {
            enqueue("items_playbackinfo/transcode_hls.json")
            val hls = metered.copy(transcoding = opus.copy(container = "mp4", segmented = true))

            val stream = (source.stream(server(), itemId, hls) as MqResult.Success).value

            assertThat(stream.playMethod).isEqualTo(PlayMethod.Transcode)
            assertThat(stream.segmented).isTrue()
            assertThat(stream.canSeek).isTrue()
            assertThat(stream.url).startsWith(mockServer.url("/audio/").toString())
            assertThat(stream.url).contains("/master.m3u8?")
            assertThat(stream.url).contains("SegmentContainer=mp4")
            assertThat(stream.url).contains("PlaySessionId=${stream.playSessionId}")
            assertThat(stream.url.lowercase()).doesNotContain("apikey")
            assertThat(stream.url).doesNotContain("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            val profile = body()["DeviceProfile"]!!.jsonObject["TranscodingProfiles"].toString()
            assertThat(profile).contains("\"Protocol\":\"hls\"")
            assertThat(profile).contains("\"Container\":\"mp4\"")
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
    fun `R10 token parameters are removed in any spelling`() {
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

    private val video =
        DeviceProfile(
            directPlayContainers = listOf("mp4", "mkv"),
            directPlayCodecs = listOf("aac", "opus"),
            transcoding =
                TranscodingTarget("mp4", "aac,opus", 192_000, 6, segmented = true, videoCodec = "hevc,h264"),
            maxStreamingBitrate = 1_500_000,
            videoCodecs = listOf("h264", "hevc"),
            maxHeight = 480,
        )

    @Test
    fun `a video PlaybackInfo request carries the video profile, its HLS conversion and the decoder limits`() =
        runTest {
            enqueue("items_playbackinfo/video_transcode_hls.json")

            source.stream(server(), itemId, video)

            assertThat(body()["DeviceProfile"])
                .isEqualTo(
                    Json.parseToJsonElement(
                        """
                        {"Name":"mediatheQ","MaxStreamingBitrate":1500000,"MusicStreamingTranscodingBitrate":192000,
                         "DirectPlayProfiles":[{"Type":"Video","Container":"mp4,mkv","AudioCodec":"aac,opus",
                           "VideoCodec":"h264,hevc"}],
                         "TranscodingProfiles":[{"Type":"Video","Container":"mp4","AudioCodec":"aac,opus","Protocol":"hls",
                           "Context":"Streaming","MaxAudioChannels":"6","VideoCodec":"hevc,h264",
                           "BreakOnNonKeyFrames":true,"MinSegments":1}],
                         "CodecProfiles":[
                           {"Type":"Video","Conditions":[
                             {"Condition":"LessThanEqual","Property":"Width","Value":"3840","IsRequired":false},
                             {"Condition":"LessThanEqual","Property":"VideoBitDepth","Value":"10","IsRequired":false},
                             {"Condition":"EqualsAny","Property":"VideoRangeType","Value":"SDR","IsRequired":false},
                             {"Condition":"LessThanEqual","Property":"Height","Value":"480","IsRequired":false}]},
                           {"Type":"VideoAudio","Conditions":[
                             {"Condition":"LessThanEqual","Property":"AudioChannels","Value":"8","IsRequired":false}]}],
                         "ContainerProfiles":[],"SubtitleProfiles":[]}
                        """.trimIndent(),
                    ),
                )
        }

    @Test
    fun `a video direct play is the original file from the Videos route`() =
        runTest {
            enqueue("items_playbackinfo/video_direct_play.json")

            val stream = (source.stream(server(), itemId, video.copy(maxHeight = null)) as MqResult.Success).value

            assertThat(stream.playMethod).isEqualTo(PlayMethod.DirectPlay)
            assertThat(stream.url).startsWith(mockServer.url("/Videos/${itemId.itemId}/stream").toString())
            assertThat(stream.url).contains("static=true")
            assertThat(stream.canSeek).isTrue()
            assertThat(stream.segmented).isFalse()
        }

    @Test
    fun `a video remux and a video conversion are HLS playlists without the token`() =
        runTest {
            enqueue("items_playbackinfo/video_direct_stream.json")
            enqueue("items_playbackinfo/video_transcode_hls.json")

            val remux = (source.stream(server(), itemId, video) as MqResult.Success).value
            val converted = (source.stream(server(), itemId, video) as MqResult.Success).value

            assertThat(remux.playMethod).isEqualTo(PlayMethod.DirectStream)
            assertThat(converted.playMethod).isEqualTo(PlayMethod.Transcode)
            for (stream in listOf(remux, converted)) {
                assertThat(stream.segmented).isTrue()
                assertThat(stream.canSeek).isTrue()
                assertThat(stream.url).contains("/videos/cc4b4fdb-31a8-af38-cfcb-812d79ad565c/master.m3u8")
                assertThat(stream.url.lowercase()).doesNotContain("apikey")
            }
            assertThat(converted.url).contains("MaxHeight=480")
        }

    @Test
    fun `a video request carries the start position as ticks and the chosen stream indexes`() =
        runTest {
            enqueue("items_playbackinfo/video_transcode_hls.json")

            source.stream(
                server(),
                itemId,
                video,
                StreamChoice(90.seconds, audioStreamIndex = 1, subtitleStreamIndex = 3),
            )

            val body = body()
            assertThat(body["StartTimeTicks"].toString()).isEqualTo("900000000")
            assertThat(body["AudioStreamIndex"].toString()).isEqualTo("1")
            assertThat(body["SubtitleStreamIndex"].toString()).isEqualTo("3")
        }

    @Test
    fun `R25 an external subtitle comes as an absolute file url without ApiKey, an embedded one as a track`() =
        runTest {
            enqueue("items_playbackinfo/video_external_subtitles.json")

            val subtitles = (source.stream(server(), itemId, video) as MqResult.Success).value.subtitles

            assertThat(subtitles.map { it.index to it.method })
                .containsExactly(2 to SubtitleMethod.Embed, 3 to SubtitleMethod.External)
                .inOrder()
            val external = subtitles.last()
            assertThat(external.url).startsWith(mockServer.url("/Videos/").toString())
            assertThat(external.url).endsWith("/Subtitles/3/0/Stream.subrip")
            assertThat(external.language).isEqualTo("eng")
        }

    @Test
    fun `a burned-in subtitle is marked Encode, an HLS one Hls, and a dropped one is left out`() =
        runTest {
            enqueue("items_playbackinfo/video_burn_in.json")
            enqueue("items_playbackinfo/video_hls_subtitles.json")

            val burnIn = (source.stream(server(), itemId, video) as MqResult.Success).value.subtitles
            val hls = (source.stream(server(), itemId, video) as MqResult.Success).value.subtitles

            assertThat(burnIn.single().method).isEqualTo(SubtitleMethod.Encode)
            assertThat(hls.single().method).isEqualTo(SubtitleMethod.Hls)
            assertThat(hls.single().isForced).isTrue()
        }

    @Test
    fun `the subtitle profiles go to the server as Format and Method`() =
        runTest {
            enqueue("items_playbackinfo/video_transcode_hls.json")

            source.stream(
                server(),
                itemId,
                video.copy(
                    subtitleProfiles =
                        listOf(
                            SubtitleProfile("subrip", SubtitleMethod.External),
                            SubtitleProfile("pgssub", SubtitleMethod.Encode),
                        ),
                ),
            )

            assertThat(body()["DeviceProfile"]!!.jsonObject["SubtitleProfiles"].toString())
                .isEqualTo("""[{"Format":"subrip","Method":"External"},{"Format":"pgssub","Method":"Encode"}]""")
        }
}
