package io.github.kentprodan.mediatheq.core.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.testing.FakeAuthSource
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultSessionRepositoryTest {
    private val authSource = FakeAuthSource()
    private val cipher = FakeTokenCipher()
    private val accountStore = AccountStore(InMemoryPreferencesDataStore(), cipher)
    private val repository = DefaultSessionRepository(authSource, accountStore, Dispatchers.Unconfined)

    private suspend fun loginAs(token: String) {
        authSource.login = MqResult.Success(FakeAuthSource.session(token))
        repository.login("http://192.168.1.10:8096", "Home", "Ana", "pw")
    }

    @Test
    fun `starts logged out`() =
        runTest {
            assertThat(repository.session.first()).isEqualTo(SessionState.LoggedOut())
        }

    @Test
    fun `a successful login stores the session`() =
        runTest {
            loginAs("token-1")

            assertThat(repository.session.first()).isEqualTo(SessionState.LoggedIn("Home", "Ana"))
            assertThat((accountStore.account.first() as MqResult.Success).value?.accessToken).isEqualTo("token-1")
        }

    @Test
    fun `a failed login stores nothing`() =
        runTest {
            authSource.login = MqResult.Failure(MqError.Unauthorized)

            val result = repository.login("http://192.168.1.10:8096", "Home", "Ana", "wrong")

            assertThat(result).isEqualTo(MqResult.Failure(MqError.Unauthorized))
            assertThat(repository.session.first()).isEqualTo(SessionState.LoggedOut())
        }

    @Test
    fun `login sends the per-install DeviceId`() =
        runTest {
            loginAs("token-1")

            assertThat(authSource.connections.single().deviceId).isEqualTo(accountStore.deviceId())
            assertThat(authSource.connections.single().accessToken).isNull()
        }

    @Test
    fun `R9 a new login replaces the stored token and logout uses only the newest`() =
        runTest {
            loginAs("token-1")
            loginAs("token-2")

            repository.logout()

            assertThat(authSource.connections.last().accessToken).isEqualTo("token-2")
            assertThat(authSource.connections.mapNotNull { it.accessToken }).doesNotContain("token-1")
        }

    @Test
    fun `Quick Connect login stores the session`() =
        runTest {
            repository.loginWithQuickConnect("http://192.168.1.10:8096", "Home", QuickConnectCode("123456", "s"))

            assertThat(repository.session.first()).isEqualTo(SessionState.LoggedIn("Home", "Ana"))
        }

    @Test
    fun `a token that cannot be encrypted fails the login with Storage`() =
        runTest {
            cipher.failing = true

            val result = repository.login("http://192.168.1.10:8096", "Home", "Ana", "pw")

            assertThat(result).isEqualTo(MqResult.Failure(MqError.Storage))
        }

    @Test
    fun `logout clears the token even when the server is unreachable`() =
        runTest {
            loginAs("token-1")
            authSource.logout = MqResult.Failure(MqError.Network)

            repository.logout()

            assertThat(repository.session.first()).isEqualTo(SessionState.LoggedOut())
        }

    @Test
    fun `logout keeps the DeviceId`() =
        runTest {
            val deviceId = accountStore.deviceId()
            loginAs("token-1")

            repository.logout()

            assertThat(accountStore.deviceId()).isEqualTo(deviceId)
        }

    @Test
    fun `a 401 signs out with session expired, and the next login clears the reason`() =
        runTest {
            loginAs("token-1")

            repository.session.test {
                assertThat(awaitItem()).isInstanceOf(SessionState.LoggedIn::class.java)
                repository.onUnauthorized()
                assertThat(expectMostRecentItem()).isEqualTo(SessionState.LoggedOut(LogoutReason.SessionExpired))
                loginAs("token-2")
                assertThat(expectMostRecentItem()).isInstanceOf(SessionState.LoggedIn::class.java)
                repository.logout()
                assertThat(expectMostRecentItem()).isEqualTo(SessionState.LoggedOut())
            }
        }

    @Test
    fun `an unreadable token means logged out with a storage error`() =
        runTest {
            loginAs("token-1")

            cipher.failing = true

            assertThat(repository.session.first()).isEqualTo(SessionState.LoggedOut(LogoutReason.StorageError))
        }

    @Test
    fun `a supported server is checked at its normalized address`() =
        runTest {
            val check = repository.checkServer(" 192.168.1.10:8096/ ")

            assertThat(check).isEqualTo(
                ServerCheck.Supported("http://192.168.1.10:8096", ServerInfo(ServerId("server-1"), "Home", "12.2.0")),
            )
            assertThat(authSource.connections.single().baseUrl).isEqualTo("http://192.168.1.10:8096")
        }

    @Test
    fun `a server older than 12 is unsupported`() =
        runTest {
            val old = ServerInfo(ServerId("server-1"), "Home", "10.11.6")
            authSource.serverInfo = MqResult.Success(old)

            assertThat(repository.checkServer("http://192.168.1.10:8096")).isEqualTo(ServerCheck.Unsupported(old))
        }

    @Test
    fun `cleartext to a public address is refused without contacting it`() =
        runTest {
            assertThat(repository.checkServer("http://8.8.8.8:8096")).isEqualTo(ServerCheck.CleartextNotAllowed)
            assertThat(authSource.connections).isEmpty()
        }

    @Test
    fun `https to a public address is allowed`() =
        runTest {
            assertThat(repository.checkServer("https://8.8.8.8")).isInstanceOf(ServerCheck.Supported::class.java)
        }

    @Test
    fun `Tailscale and loopback addresses may use cleartext`() =
        runTest {
            assertThat(repository.checkServer("100.101.102.103:8096")).isInstanceOf(ServerCheck.Supported::class.java)
            assertThat(repository.checkServer("http://[::1]:8096")).isInstanceOf(ServerCheck.Supported::class.java)
        }

    @Test
    fun `an unreachable server fails with Network`() =
        runTest {
            authSource.serverInfo = MqResult.Failure(MqError.Network)

            assertThat(repository.checkServer("10.0.0.2")).isEqualTo(ServerCheck.Failed(MqError.Network))
        }

    @Test
    fun `an invalid address is rejected`() =
        runTest {
            assertThat(repository.checkServer("   ")).isEqualTo(ServerCheck.InvalidAddress)
            assertThat(repository.checkServer("ftp://nas")).isEqualTo(ServerCheck.InvalidAddress)
        }
}
