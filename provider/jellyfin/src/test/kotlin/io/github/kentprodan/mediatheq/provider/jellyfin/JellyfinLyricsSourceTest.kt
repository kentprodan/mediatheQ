package io.github.kentprodan.mediatheq.provider.jellyfin

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.errorOrNull
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.network.MediatheqJson
import io.github.kentprodan.mediatheq.core.network.mediatheqOkHttpClient
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Contract tests for lyrics (jellyfin skill, section 10). */
class JellyfinLyricsSourceTest {
    @get:Rule
    val serverRule = MockWebServerRule()
    private val mockServer get() = serverRule.server

    private val source =
        JellyfinLyricsSource(
            mediatheqOkHttpClient(),
            ClientInfo("mediatheQ", "Pixel 9", "0.1.0"),
            MediatheqJson,
            Dispatchers.Unconfined,
        )
    private val serverId = ServerId("0f1e2d3c4b5a69788796a5b4c3d2e1f0")
    private val server
        get() =
            SignedInServer(
                ServerConnection(mockServer.url("/").toString(), "device-1", accessToken = "token-1"),
                serverId,
                "11111111222233334444555555555555",
            )
    private val track = MediaId(serverId, "6f3c2a1b0e9d8c7b6a5f4e3d2c1b0a99")

    private fun fixture(name: String) =
        mockServer.enqueue(MockResponse(body = javaClass.getResource("/jellyfin/audio_lyrics/$name.json")!!.readText()))

    @Test
    fun `lyrics are a GET of the track's lyrics with the auth header and no query`() =
        runTest {
            fixture("plain")

            source.lyrics(server, track)

            val request = mockServer.takeRequest()
            assertThat(request.method).isEqualTo("GET")
            assertThat(request.url.encodedPath).isEqualTo("/Audio/${track.itemId}/Lyrics")
            assertThat(request.url.queryParameterNames).isEmpty()
            assertThat(request.headers["Authorization"]).endsWith(", Token=\"token-1\"")
        }

    @Test
    fun `R4 lines with starts are synced although IsSynced is missing`() =
        runTest {
            fixture("synced_without_flag")

            val lyrics = (source.lyrics(server, track) as MqResult.Success).value!!

            assertThat(lyrics.isSynced).isTrue()
            assertThat(lyrics.lines)
                .containsExactly(
                    LyricLine("First line of the song", 12_500.milliseconds),
                    LyricLine("", 15_000.milliseconds),
                    LyricLine("Second line of the song", 21_340.milliseconds),
                ).inOrder()
        }

    @Test
    fun `lines without starts are plain lyrics`() =
        runTest {
            fixture("plain")

            assertThat(source.lyrics(server, track))
                .isEqualTo(
                    MqResult.Success(
                        Lyrics(listOf(LyricLine("First line of the song"), LyricLine("Second line of the song"))),
                    ),
                )
        }

    @Test
    fun `a 404 means the track has no lyrics`() =
        runTest {
            mockServer.enqueue(MockResponse(code = 404))

            assertThat(source.lyrics(server, track)).isEqualTo(MqResult.Success(null))
        }

    @Test
    fun `an empty list means the track has no lyrics`() =
        runTest {
            mockServer.enqueue(MockResponse(body = """{"Lyrics":[]}"""))

            assertThat(source.lyrics(server, track)).isEqualTo(MqResult.Success(null))
        }

    @Test
    fun `a 401 is Unauthorized`() =
        runTest {
            mockServer.enqueue(MockResponse(code = 401))

            assertThat(source.lyrics(server, track).errorOrNull()).isEqualTo(MqError.Unauthorized)
        }
}
