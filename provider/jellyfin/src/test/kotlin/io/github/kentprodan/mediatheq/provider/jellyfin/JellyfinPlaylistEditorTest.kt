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

/** Contract tests for playlist writes, shaped after requests checked on a Jellyfin 12.2.0 server (2026-10-22). */
class JellyfinPlaylistEditorTest {
    @get:Rule
    val serverRule = MockWebServerRule()
    private val mockServer get() = serverRule.server

    private val editor =
        JellyfinPlaylistEditor(
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
    private val playlist = MediaId(serverId, "p1")

    private fun noContent(times: Int = 1) = repeat(times) { mockServer.enqueue(MockResponse(code = 204)) }

    private fun RecordedRequest.query(): Map<String, String?> =
        url.queryParameterNames.associateWith { url.queryParameter(it) }

    private fun RecordedRequest.ids(name: String): List<String> = url.queryParameter(name)!!.split(",")

    @Test
    fun `create posts an empty audio playlist and returns its id`() =
        runTest {
            mockServer.enqueue(
                MockResponse(body = javaClass.getResource("/jellyfin/playlists_create/success.json")!!.readText()),
            )

            val id = (editor.create(server, "Road trip") as MqResult.Success).value

            val request = mockServer.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/Playlists")
            assertThat(request.url.query).isNull()
            assertThat(Json.parseToJsonElement(request.body!!.utf8())).isEqualTo(
                Json.parseToJsonElement("""{"Name":"Road trip","UserId":"$userId","MediaType":"Audio"}"""),
            )
            assertThat(request.headers["Authorization"]).endsWith(", Token=\"token-1\"")
            assertThat(id).isEqualTo(MediaId(serverId, "e512974d6519242955404c6564f3460d"))
        }

    @Test
    fun `R3 playlist add chunks requests above 200 ids`() =
        runTest {
            noContent(3)
            val tracks = (1..450).map { MediaId(serverId, "t$it") }

            assertThat(editor.add(server, playlist, tracks)).isEqualTo(MqResult.Success(Unit))

            val requests = List(3) { mockServer.takeRequest() }
            requests.forEach {
                assertThat(it.method).isEqualTo("POST")
                assertThat(it.url.encodedPath).isEqualTo("/Playlists/p1/Items")
                assertThat(it.query().keys).containsExactly("ids", "userId")
                assertThat(it.url.queryParameter("userId")).isEqualTo(userId)
            }
            assertThat(requests.map { it.ids("ids").size }).containsExactly(200, 200, 50).inOrder()
            assertThat(requests.flatMap { it.ids("ids") }).isEqualTo(tracks.map { it.itemId })
        }

    @Test
    fun `add stops at the first failed chunk`() =
        runTest {
            mockServer.enqueue(MockResponse(code = 204))
            mockServer.enqueue(MockResponse(code = 403))

            val result = editor.add(server, playlist, (1..450).map { MediaId(serverId, "t$it") })

            assertThat(result.errorOrNull()).isEqualTo(MqError.Forbidden)
            assertThat(mockServer.requestCount).isEqualTo(2)
        }

    @Test
    fun `R3 playlist remove sends entry ids in chunks of 200`() =
        runTest {
            noContent(2)
            val entries = (1..201).map { "e$it" }

            assertThat(editor.remove(server, playlist, entries)).isEqualTo(MqResult.Success(Unit))

            val requests = List(2) { mockServer.takeRequest() }
            requests.forEach {
                assertThat(it.method).isEqualTo("DELETE")
                assertThat(it.url.encodedPath).isEqualTo("/Playlists/p1/Items")
                assertThat(it.query().keys).containsExactly("entryIds")
            }
            assertThat(requests.flatMap { it.ids("entryIds") }).isEqualTo(entries)
        }

    @Test
    fun `R13 move puts the entry at the new index`() =
        runTest {
            noContent()

            assertThat(editor.move(server, playlist, "e7", 0)).isEqualTo(MqResult.Success(Unit))

            val request = mockServer.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/Playlists/p1/Items/e7/Move/0")
            assertThat(request.url.query).isNull()
        }

    @Test
    fun `rename sends only the new name`() =
        runTest {
            noContent()

            assertThat(editor.rename(server, playlist, "Night drive")).isEqualTo(MqResult.Success(Unit))

            val request = mockServer.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/Playlists/p1")
            assertThat(Json.parseToJsonElement(request.body!!.utf8()))
                .isEqualTo(Json.parseToJsonElement("""{"Name":"Night drive"}"""))
        }

    @Test
    fun `R5 delete targets only the one playlist item, never a bulk delete`() =
        runTest {
            noContent()

            assertThat(editor.deletePlaylist(server, playlist)).isEqualTo(MqResult.Success(Unit))

            val request = mockServer.takeRequest()
            assertThat(request.method).isEqualTo("DELETE")
            assertThat(request.url.encodedPath).isEqualTo("/Items/p1")
            assertThat(request.url.query).isNull()
        }
}
