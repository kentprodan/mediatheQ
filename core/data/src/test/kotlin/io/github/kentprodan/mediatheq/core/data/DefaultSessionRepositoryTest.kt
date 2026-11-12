package io.github.kentprodan.mediatheq.core.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.SavedAccount
import io.github.kentprodan.mediatheq.core.model.QuickConnectCode
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.server.AuthSession
import io.github.kentprodan.mediatheq.core.testing.FakeAccountDatabases
import io.github.kentprodan.mediatheq.core.testing.FakeAppLog
import io.github.kentprodan.mediatheq.core.testing.FakeAuthSource
import io.github.kentprodan.mediatheq.core.testing.FakeQueueRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultSessionRepositoryTest {
    private val authSource = FakeAuthSource()
    private val cipher = FakeTokenCipher()
    private val dataStore = InMemoryPreferencesDataStore()
    private val accountStore = AccountStore(dataStore, cipher)
    private val queue = FakeQueueRepository()
    private val databases = FakeAccountDatabases()
    private val restarts = mutableListOf<Unit>()
    private val repository =
        DefaultSessionRepository(
            authSource,
            accountStore,
            databases,
            { restarts += Unit },
            queue,
            Dispatchers.Unconfined,
        )

    private suspend fun loginAs(token: String) {
        authSource.login = MqResult.Success(FakeAuthSource.session(token))
        repository.login("http://192.168.1.10:8096", "Home", "Ana", "pw")
    }

    @Test
    fun `a login while an account is active adds the new one beside it, still inactive`() =
        runTest {
            loginAs("token-1")
            authSource.login = MqResult.Success(AuthSession(ServerId("server-2"), "user-2", "Bo", "token-2"))

            repository.login("https://office.org", "Office", "Bo", "pw")

            val accounts = (accountStore.accounts.first() as MqResult.Success).value
            assertThat(accounts.map { it.id }).containsExactly("mediatheq.db", "mediatheq-1.db").inOrder()
            assertThat(repository.session.first()).isEqualTo(SessionState.LoggedIn("Home", "Ana"))
            assertThat(repository.activeServerId.first()).isEqualTo(ServerId("server-1"))
            assertThat(restarts).isEmpty()
        }

    @Test
    fun `signing in to a restored signed-out account gives it its token under its own id`() =
        runTest {
            loginAs("token-1")
            accountStore.saveSignedOut(
                SavedAccount(
                    "mediatheq-restored.db",
                    "https://office.org",
                    ServerId("server-2"),
                    "Office",
                    "user-2",
                    "Bo",
                ),
            )
            authSource.login = MqResult.Success(AuthSession(ServerId("server-2"), "user-2", "Bo", "token-2"))

            repository.login("https://office.org", "Office", "Bo", "pw")

            val accounts = (accountStore.accounts.first() as MqResult.Success).value
            assertThat(accounts.map { it.id to it.accessToken })
                .containsExactly("mediatheq.db" to "token-1", "mediatheq-restored.db" to "token-2")
                .inOrder()
            assertThat(restarts).isEmpty()
        }

    @Test
    fun `signing in again to a saved account renews its token and keeps its database`() =
        runTest {
            loginAs("token-1")
            authSource.login = MqResult.Success(AuthSession(ServerId("server-2"), "user-2", "Bo", "token-2"))
            repository.login("https://office.org", "Office", "Bo", "pw")
            authSource.login = MqResult.Success(AuthSession(ServerId("server-2"), "user-2", "Bo", "token-3"))

            repository.login("https://office.org", "Office", "Bo", "pw")

            val accounts = (accountStore.accounts.first() as MqResult.Success).value
            assertThat(accounts.map { it.id to it.accessToken })
                .containsExactly("mediatheq.db" to "token-1", "mediatheq-1.db" to "token-3")
                .inOrder()
        }

    @Test
    fun `with none active, signing in to a saved account opens its database by a restart`() =
        runTest {
            loginAs("token-1")
            authSource.login = MqResult.Success(AuthSession(ServerId("server-2"), "user-2", "Bo", "token-2"))
            repository.login("https://office.org", "Office", "Bo", "pw")
            repository.onUnauthorized()

            repository.login("https://office.org", "Office", "Bo", "pw")

            assertThat(databases.selected).isEqualTo("mediatheq-1.db")
            assertThat(restarts).hasSize(1)
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
    fun `sign-in and sign-out are logged with the server's name, never its token`() =
        runTest {
            val log = FakeAppLog()
            val repository =
                DefaultSessionRepository(
                    authSource,
                    accountStore,
                    databases,
                    { restarts += Unit },
                    queue,
                    Dispatchers.Unconfined,
                    log,
                )
            authSource.login = MqResult.Success(FakeAuthSource.session("token-1"))

            repository.login("http://192.168.1.10:8096", "Home", "Ana", "pw")
            repository.logout()

            assertThat(
                log.lines,
            ).containsExactly("I Session: Signed in to Home as Ana", "I Session: Signed out").inOrder()
            assertThat(log.lines.joinToString()).doesNotContain("token-1")
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
    fun `logout clears the token at once even when the server hangs`() =
        runTest {
            loginAs("token-1")
            authSource.logoutHangs = true

            repository.logout()

            assertThat(repository.session.first()).isEqualTo(SessionState.LoggedOut())
        }

    @Test
    fun `a disk that cannot be written fails the login with Storage`() =
        runTest {
            accountStore.deviceId()
            dataStore.failing = true

            val result = repository.login("http://192.168.1.10:8096", "Home", "Ana", "pw")

            assertThat(result).isEqualTo(MqResult.Failure(MqError.Storage))
        }

    @Test
    fun `logout and an expired session forget the saved queue`() =
        runTest {
            loginAs("token-1")
            repository.logout()
            assertThat(queue.clears).isEqualTo(1)

            loginAs("token-2")
            repository.onUnauthorized()
            assertThat(queue.clears).isEqualTo(2)
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
