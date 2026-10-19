package io.github.kentprodan.mediatheq.provider.jellyfin

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.common.errorOrNull
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.network.MediatheqJson
import io.github.kentprodan.mediatheq.core.network.mediatheqOkHttpClient
import io.github.kentprodan.mediatheq.core.server.AuthSession
import io.github.kentprodan.mediatheq.core.server.ClientInfo
import io.github.kentprodan.mediatheq.core.server.ServerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Rule
import org.junit.Test

/** Contract tests against responses recorded from a Jellyfin 12.2.0 server (testing skill, section 4). */
class JellyfinAuthSourceTest {
    @get:Rule
    val serverRule = MockWebServerRule()
    private val server get() = serverRule.server

    private val source =
        JellyfinAuthSource(
            mediatheqOkHttpClient(),
            ClientInfo(client = "mediatheQ", device = "Pixel 9", version = "0.1.0"),
            MediatheqJson,
            Dispatchers.Unconfined,
        )

    private val anonymous get() = ServerConnection(server.url("/").toString(), deviceId = "device-1")
    private val signedIn get() = anonymous.copy(accessToken = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    private val code = QuickConnectCode("123456", "E".repeat(64))

    private fun fixture(path: String) = javaClass.getResource("/jellyfin/$path")!!.readText()

    private fun enqueue(
        path: String,
        code: Int = 200,
    ) = server.enqueue(MockResponse(code = code, body = fixture(path)))

    private fun assertAnonymousHeader(request: RecordedRequest) =
        assertThat(request.headers["Authorization"]).isEqualTo(
            "MediaBrowser Client=\"mediatheQ\", Device=\"Pixel%209\", DeviceId=\"device-1\", Version=\"0.1.0\"",
        )

    @Test
    fun `server info parses public info and sends the header without token`() =
        runTest {
            enqueue("system_info_public/version_12.json")

            val result = source.serverInfo(anonymous)

            assertThat(result).isEqualTo(
                MqResult.Success(ServerInfo(ServerId("0f1e2d3c4b5a69788796a5b4c3d2e1f0"), "Test Server", "12.2.0")),
            )
            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("GET")
            assertThat(request.url.encodedPath).isEqualTo("/System/Info/Public")
            assertAnonymousHeader(request)
        }

    @Test
    fun `requests keep the base path of the server address`() =
        runTest {
            enqueue("system_info_public/version_12.json")

            source.serverInfo(anonymous.copy(baseUrl = server.url("/jellyfin").toString()))

            assertThat(server.takeRequest().url.encodedPath).isEqualTo("/jellyfin/System/Info/Public")
        }

    @Test
    fun `login posts the credentials and maps the session`() =
        runTest {
            enqueue("users_authenticate_by_name/success.json")

            val result = source.login(anonymous, "test-user", "p\"ss")

            assertThat(result).isEqualTo(
                MqResult.Success(
                    AuthSession(
                        ServerId("0f1e2d3c4b5a69788796a5b4c3d2e1f0"),
                        userId = "11111111222233334444555555555555",
                        userName = "test-user",
                        accessToken = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    ),
                ),
            )
            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/Users/AuthenticateByName")
            assertThat(request.body?.utf8()).isEqualTo("""{"Username":"test-user","Pw":"p\"ss"}""")
            assertAnonymousHeader(request)
        }

    @Test
    fun `wrong password fails with Unauthorized`() =
        runTest {
            enqueue("users_authenticate_by_name/wrong_password.txt", code = 401)

            assertThat(source.login(anonymous, "test-user", "wrong").errorOrNull()).isEqualTo(MqError.Unauthorized)
        }

    @Test
    fun `Quick Connect enabled is read`() =
        runTest {
            enqueue("quick_connect_enabled/enabled.json")

            assertThat(source.isQuickConnectEnabled(anonymous)).isEqualTo(MqResult.Success(true))
            val request = server.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/QuickConnect/Enabled")
            assertAnonymousHeader(request)
        }

    @Test
    fun `Quick Connect start posts and returns the code`() =
        runTest {
            enqueue("quick_connect_initiate/success.json")

            assertThat(source.startQuickConnect(anonymous)).isEqualTo(MqResult.Success(code))
            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/QuickConnect/Initiate")
            assertAnonymousHeader(request)
        }

    @Test
    fun `Quick Connect state is pending, then approved`() =
        runTest {
            enqueue("quick_connect_connect/pending.json")
            enqueue("quick_connect_connect/authorized.json")

            assertThat(source.isQuickConnectApproved(anonymous, code)).isEqualTo(MqResult.Success(false))
            assertThat(source.isQuickConnectApproved(anonymous, code)).isEqualTo(MqResult.Success(true))
            val request = server.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/QuickConnect/Connect")
            assertThat(request.url.queryParameter("secret")).isEqualTo(code.secret)
            assertAnonymousHeader(request)
        }

    @Test
    fun `an unknown Quick Connect secret fails with NotFound`() =
        runTest {
            enqueue("quick_connect_connect/unknown_secret.json", code = 404)

            assertThat(source.isQuickConnectApproved(anonymous, code).errorOrNull()).isEqualTo(MqError.NotFound)
        }

    @Test
    fun `Quick Connect login posts the secret`() =
        runTest {
            enqueue("users_authenticate_with_quick_connect/success.json")

            val result = source.loginWithQuickConnect(anonymous, code)

            assertThat((result as MqResult.Success).value.accessToken).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
            val request = server.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/Users/AuthenticateWithQuickConnect")
            assertThat(request.body?.utf8()).isEqualTo("""{"Secret":"${code.secret}"}""")
            assertAnonymousHeader(request)
        }

    @Test
    fun `logout sends the token in the header`() =
        runTest {
            server.enqueue(MockResponse(code = 204))

            assertThat(source.logout(signedIn)).isEqualTo(MqResult.Success(Unit))
            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/Sessions/Logout")
            assertThat(request.headers["Authorization"]).isEqualTo(
                "MediaBrowser Client=\"mediatheQ\", Device=\"Pixel%209\", DeviceId=\"device-1\", Version=\"0.1.0\", " +
                    "Token=\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
            )
        }

    @Test
    fun `R9 a token revoked by a newer login fails with Unauthorized`() =
        runTest {
            // Recorded: logout with a token replaced by a newer login from the same DeviceId returns an empty 401.
            server.enqueue(MockResponse(code = 401))

            assertThat(source.logout(signedIn).errorOrNull()).isEqualTo(MqError.Unauthorized)
        }

    @Test
    fun `R1 requests use only the MediaBrowser header`() =
        runTest {
            enqueue("system_info_public/version_12.json")
            enqueue("users_authenticate_by_name/success.json")
            enqueue("quick_connect_enabled/enabled.json")
            enqueue("quick_connect_initiate/success.json")
            enqueue("quick_connect_connect/pending.json")
            enqueue("users_authenticate_with_quick_connect/success.json")
            server.enqueue(MockResponse(code = 204))

            source.serverInfo(anonymous)
            source.login(anonymous, "test-user", "secret")
            source.isQuickConnectEnabled(anonymous)
            source.startQuickConnect(anonymous)
            source.isQuickConnectApproved(anonymous, code)
            source.loginWithQuickConnect(anonymous, code)
            source.logout(signedIn)

            repeat(7) {
                val request = server.takeRequest()
                assertThat(request.headers.values("Authorization")).hasSize(1)
                assertThat(request.headers["Authorization"]).startsWith("MediaBrowser ")
                assertThat(request.headers.names().map { it.lowercase() })
                    .containsNoneOf("x-emby-token", "x-mediabrowser-token", "x-emby-authorization")
                assertThat(request.url.queryParameterNames.map { it.lowercase() }).containsNoneOf("api_key", "apikey")
                assertThat(request.url.encodedPath).doesNotMatch("(?i)^/(emby|mediabrowser)/.*")
            }
        }

    @Test
    fun `header values are URL-encoded`() {
        val header =
            authorizationHeader(
                ClientInfo("mediatheQ", "Pixel \"9\", Pro=x", "0.1.0"),
                ServerConnection("http://server", "device-1"),
            )

        assertThat(header).isEqualTo(
            "MediaBrowser Client=\"mediatheQ\", Device=\"Pixel%20%229%22%2C%20Pro%3Dx\", DeviceId=\"device-1\", " +
                "Version=\"0.1.0\"",
        )
    }

    @Test
    fun `an unreachable server fails with Network`() =
        runTest {
            val connection = anonymous
            server.close()

            assertThat(source.serverInfo(connection).errorOrNull()).isEqualTo(MqError.Network)
        }

    @Test
    fun `a page that is not Jellyfin fails with Unknown`() =
        runTest {
            server.enqueue(MockResponse(body = "<html>router login</html>"))

            assertThat(source.serverInfo(anonymous).errorOrNull()).isInstanceOf(MqError.Unknown::class.java)
        }

    @Test
    fun `server errors map to Server`() =
        runTest {
            server.enqueue(MockResponse(code = 503))

            assertThat(source.serverInfo(anonymous).errorOrNull()).isEqualTo(MqError.Server(503))
        }
}
