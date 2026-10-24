package io.github.kentprodan.mediatheq.provider.jellyfin

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.errorOrNull
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.network.MediatheqJson
import io.github.kentprodan.mediatheq.core.network.mediatheqOkHttpClient
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test

/** Contract tests for favorite and rating writes (jellyfin skill, section 7). */
class JellyfinUserDataSinkTest {
    @get:Rule
    val serverRule = MockWebServerRule()
    private val mockServer get() = serverRule.server

    private val sink =
        JellyfinUserDataSink(
            mediatheqOkHttpClient(),
            ClientInfo("mediatheQ", "Pixel 9", "0.1.0"),
            MediatheqJson,
            Dispatchers.Unconfined,
        )
    private val serverId = ServerId("0f1e2d3c4b5a69788796a5b4c3d2e1f0")
    private val userId = "11111111222233334444555555555555"
    private val server
        get() =
            SignedInServer(
                ServerConnection(mockServer.url("/").toString(), "device-1", accessToken = "token-1"),
                serverId,
                userId,
            )
    private val track = MediaId(serverId, "6f3c2a1b0e9d8c7b6a5f4e3d2c1b0a99")

    private fun success() =
        mockServer.enqueue(
            MockResponse(body = javaClass.getResource("/jellyfin/user_items_user_data/success.json")!!.readText()),
        )

    private fun RecordedRequest.assertUserDataPost(body: String) {
        assertThat(method).isEqualTo("POST")
        assertThat(url.encodedPath).isEqualTo("/UserItems/${track.itemId}/UserData")
        assertThat(url.queryParameterNames).containsExactly("userId")
        assertThat(url.queryParameter("userId")).isEqualTo(userId)
        assertThat(headers["Authorization"]).endsWith(", Token=\"token-1\"")
        assertThat(Json.parseToJsonElement(this.body!!.utf8())).isEqualTo(Json.parseToJsonElement(body))
    }

    @Test
    fun `favorite sends only IsFavorite`() =
        runTest {
            success()

            assertThat(sink.setFavorite(server, track, true)).isEqualTo(MqResult.Success(Unit))

            mockServer.takeRequest().assertUserDataPost("""{"IsFavorite":true}""")
        }

    @Test
    fun `unfavorite sends IsFavorite false`() =
        runTest {
            success()

            sink.setFavorite(server, track, false)

            mockServer.takeRequest().assertUserDataPost("""{"IsFavorite":false}""")
        }

    @Test
    fun `rating sends only Rating`() =
        runTest {
            success()

            assertThat(sink.setRating(server, track, 8.0)).isEqualTo(MqResult.Success(Unit))

            mockServer.takeRequest().assertUserDataPost("""{"Rating":8.0}""")
        }

    @Test
    fun `R14 clearing the rating sends 0, never the like endpoint`() =
        runTest {
            success()

            sink.setRating(server, track, null)

            mockServer.takeRequest().assertUserDataPost("""{"Rating":0.0}""")
        }

    @Test
    fun `a 401 is Unauthorized and a write is not retried`() =
        runTest {
            mockServer.enqueue(MockResponse(code = 401))

            assertThat(sink.setFavorite(server, track, true).errorOrNull()).isEqualTo(MqError.Unauthorized)
            assertThat(mockServer.requestCount).isEqualTo(1)
        }

    @Test
    fun `a 500 is a server error and a write is not retried`() =
        runTest {
            mockServer.enqueue(MockResponse(code = 500))

            assertThat(sink.setRating(server, track, 4.0).errorOrNull()).isEqualTo(MqError.Server(500))
            assertThat(mockServer.requestCount).isEqualTo(1)
        }
}
