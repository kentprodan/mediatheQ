package io.github.kentprodan.mediatheq.provider.jellyfin

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.network.MediatheqJson
import io.github.kentprodan.mediatheq.core.network.mediatheqOkHttpClient
import io.github.kentprodan.mediatheq.core.server.LiveEvent
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import io.github.kentprodan.mediatheq.core.server.SignedInServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Rule
import org.junit.Test

/** The `/socket` client against a fake socket (MockWebServer's WebSocket upgrade). */
class JellyfinLiveEventsTest {
    @get:Rule
    val serverRule = MockWebServerRule()
    private val mockServer get() = serverRule.server

    private val liveEvents = JellyfinLiveEvents(mediatheqOkHttpClient(), MediatheqJson)
    private val server
        get() =
            SignedInServer(
                ServerConnection(mockServer.url("/").toString(), "device-1", accessToken = "token-1"),
                ServerId("0f1e2d3c4b5a69788796a5b4c3d2e1f0"),
                "11111111222233334444555555555555",
            )

    // The server side of the socket: what the app sent, and a handle to push frames.
    private val received = Channel<String>(Channel.UNLIMITED)
    private val serverSocket = Channel<WebSocket>(1)

    private fun upgrade() =
        mockServer.enqueue(
            MockResponse
                .Builder()
                .webSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            serverSocket.trySend(webSocket)
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            received.trySend(text)
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            // Answer the app's close, as Jellyfin does, so the server can shut down.
                            webSocket.close(code, null)
                        }
                    },
                ).build(),
        )

    private fun fixture(name: String) = javaClass.getResource("/jellyfin/socket/$name.json")!!.readText()

    // Real sockets: wait on wall-clock time, not runTest's virtual time.
    private fun realTimeTest(block: suspend () -> Unit) = runTest { withContext(Dispatchers.Default) { block() } }

    private suspend fun <T> Channel<T>.next() = withTimeout(5_000) { receive() }

    @Test
    fun `R1 connects to socket with ApiKey and deviceId and no auth header`() =
        realTimeTest {
            upgrade()

            liveEvents.events(server).test {
                assertThat(awaitItem()).isEqualTo(LiveEvent.Connected)
                cancelAndIgnoreRemainingEvents()
            }

            val request = mockServer.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/socket")
            assertThat(request.url.queryParameterNames).containsExactly("ApiKey", "deviceId")
            assertThat(request.url.queryParameter("ApiKey")).isEqualTo("token-1")
            assertThat(request.url.queryParameter("deviceId")).isEqualTo("device-1")
            assertThat(request.headers["Authorization"]).isNull()
        }

    @Test
    fun `server messages arrive as events and unknown ones are skipped`() =
        realTimeTest {
            upgrade()

            liveEvents.events(server).test {
                assertThat(awaitItem()).isEqualTo(LiveEvent.Connected)
                val socket = serverSocket.next()
                socket.send("""{"MessageType":"Sessions","Data":[]}""")
                socket.send(fixture("library_changed"))
                socket.send(fixture("user_data_changed"))

                assertThat(awaitItem()).isEqualTo(LiveEvent.LibraryChanged)
                assertThat(awaitItem()).isInstanceOf(LiveEvent.UserDataChanged::class.java)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ForceKeepAlive is answered with KeepAlive`() =
        realTimeTest {
            upgrade()

            liveEvents.events(server).test {
                awaitItem()
                serverSocket.next().send(fixture("force_keep_alive"))

                assertThat(received.next()).isEqualTo("""{"MessageType":"KeepAlive"}""")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a server close ends the flow with a network error`() =
        realTimeTest {
            upgrade()

            liveEvents.events(server).test {
                awaitItem()
                serverSocket.next().close(1001, "restarting")

                assertThat(awaitItem()).isEqualTo(LiveEvent.Closed(MqError.Network))
                awaitComplete()
            }
        }

    @Test
    fun `a 401 handshake ends the flow with Unauthorized`() =
        realTimeTest {
            mockServer.enqueue(MockResponse(code = 401))

            liveEvents.events(server).test {
                assertThat(awaitItem()).isEqualTo(LiveEvent.Closed(MqError.Unauthorized))
                awaitComplete()
            }
        }
}
