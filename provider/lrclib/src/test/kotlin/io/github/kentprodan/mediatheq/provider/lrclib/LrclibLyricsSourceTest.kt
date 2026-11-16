package io.github.kentprodan.mediatheq.provider.lrclib

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.LyricLine
import io.github.kentprodan.mediatheq.core.model.Lyrics
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LrclibLyricsSourceTest {
    @get:Rule
    val serverRule = MockWebServerRule()
    private val server get() = serverRule.server
    private val source by lazy { LrclibLyricsSource(OkHttpClient(), server.url("/"), Duration.ZERO) }

    private fun answer(
        code: Int,
        body: String = "",
    ) = server.enqueue(MockResponse(code = code, body = body))

    @Test
    fun `the exact match comes first, with title, artist, album and length, and synced lyrics`() =
        runTest {
            answer(200, """{"duration":240.0,"syncedLyrics":"[00:01.50] Hello\n[00:03.00] World"}""")

            val lyrics = source.lyrics("Strobe", "deadmau5", "For Lack", 4.minutes)

            val request = server.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/api/get")
            assertThat(request.url.queryParameter("track_name")).isEqualTo("Strobe")
            assertThat(request.url.queryParameter("album_name")).isEqualTo("For Lack")
            assertThat(request.url.queryParameter("duration")).isEqualTo("240")
            assertThat(request.headers["User-Agent"]).startsWith("mediatheQ")
            assertThat(lyrics)
                .isEqualTo(
                    MqResult.Success(
                        Lyrics(listOf(LyricLine("Hello", 1500.milliseconds), LyricLine("World", 3.seconds))),
                    ),
                )
        }

    @Test
    fun `no exact match searches, keeping only a length within 5 s and preferring synced`() =
        runTest {
            answer(404)
            answer(
                200,
                """[
                {"duration":300.0,"syncedLyrics":"[00:01.00] too long"},
                {"duration":242.0,"plainLyrics":"plain"},
                {"duration":238.0,"syncedLyrics":"[00:02.00] near"}
                ]""",
            )

            val lyrics = source.lyrics("Strobe", "deadmau5", "For Lack", 4.minutes)

            server.takeRequest()
            assertThat(server.takeRequest().url.encodedPath).isEqualTo("/api/search")
            assertThat(lyrics).isEqualTo(MqResult.Success(Lyrics(listOf(LyricLine("near", 2.seconds)))))
        }

    @Test
    fun `an instrumental track has no lyrics`() =
        runTest {
            answer(200, """{"instrumental":true,"duration":240.0}""")
            answer(200, "[]")

            assertThat(source.lyrics("Intro", "A", "B", 4.minutes)).isEqualTo(MqResult.Success(null))
        }

    @Test
    fun `transient answers are tried three times, a client error once`() =
        runTest {
            answer(503)
            answer(429)
            answer(500)

            assertThat(source.lyrics("T", "A", null, Duration.ZERO)).isEqualTo(MqResult.Failure(MqError.Server(500)))
            assertThat(server.requestCount).isEqualTo(3)

            answer(400)
            assertThat(
                source.lyrics("T", "A", null, Duration.ZERO),
            ).isEqualTo(MqResult.Failure(MqError.Client(400, null)))
            assertThat(server.requestCount).isEqualTo(4)
        }

    @Test
    fun `without a title or an artist nothing is asked`() =
        runTest {
            assertThat(source.lyrics("", "A", "B", 4.minutes)).isEqualTo(MqResult.Success(null))
            assertThat(server.requestCount).isEqualTo(0)
        }

    @Test
    fun `LRC stamps of every precision, several per line, in time order`() {
        val lyrics = parseLrc("[ar: someone]\n[00:10.5]b\n[00:01.05][00:20.005] a \nno stamp")!!

        assertThat(lyrics.lines)
            .containsExactly(
                LyricLine("a", 1050.milliseconds),
                LyricLine("b", 10500.milliseconds),
                LyricLine("a", 20005.milliseconds),
            ).inOrder()
        assertThat(parseLrc("just text")).isNull()
    }
}
