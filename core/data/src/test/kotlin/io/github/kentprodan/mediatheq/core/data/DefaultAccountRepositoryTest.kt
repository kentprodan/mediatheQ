package io.github.kentprodan.mediatheq.core.data

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.datastore.AccountStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAccount
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.ServerInfo
import io.github.kentprodan.mediatheq.core.testing.FakeAccountDatabases
import io.github.kentprodan.mediatheq.core.testing.FakeAuthSource
import io.github.kentprodan.mediatheq.core.testing.FakeDownloadRepository
import io.github.kentprodan.mediatheq.core.testing.FakeSessionRepository
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultAccountRepositoryTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val cipher = FakeTokenCipher()
    private val databases = FakeAccountDatabases("mediatheq.db", "mediatheq.db", "mediatheq-2.db", "orphan.db")
    private val store = AccountStore(dataStore, cipher, databases.open)
    private val session = FakeSessionRepository()
    private val authSource = FakeAuthSource()
    private val downloads = FakeDownloadRepository()
    private var restarts = 0
    private val repository =
        DefaultAccountRepository(
            store,
            databases,
            session,
            authSource,
            downloads,
            { restarts++ },
            Dispatchers.Unconfined,
        )

    private val home =
        StoredAccount("http://nas:8096", ServerId("server-1"), "Home", "user-1", "Ana", "token-1", "mediatheq.db")
    private val office =
        StoredAccount("https://office.org", ServerId("server-2"), "Office", "user-9", "Bo", "token-2", "mediatheq-2.db")

    private suspend fun saveBoth() {
        store.save(home)
        store.save(office)
    }

    @Test
    fun `accounts list in order with the open database's one active`() =
        runTest {
            saveBoth()

            assertThat(repository.accounts.first())
                .containsExactly(
                    Account("mediatheq.db", "Home", "Ana", "http://nas:8096", active = true),
                    Account("mediatheq-2.db", "Office", "Bo", "https://office.org", active = false),
                ).inOrder()
        }

    @Test
    fun `making another account active opens its database at the next start`() =
        runTest {
            saveBoth()

            repository.makeActive("mediatheq-2.db")

            assertThat(databases.selected).isEqualTo("mediatheq-2.db")
            assertThat(restarts).isEqualTo(1)
        }

    @Test
    fun `making the active or an unknown account active changes nothing`() =
        runTest {
            saveBoth()

            repository.makeActive("mediatheq.db")
            repository.makeActive("gone.db")

            assertThat(databases.selected).isNull()
            assertThat(restarts).isEqualTo(0)
        }

    @Test
    fun `removing an inactive account signs it out and deletes its database, the active one stays`() =
        runTest {
            saveBoth()

            repository.remove("mediatheq-2.db", deleteDownloads = false)

            assertThat(repository.accounts.first().map { it.id }).containsExactly("mediatheq.db")
            assertThat(databases.files).doesNotContain("mediatheq-2.db")
            assertThat(authSource.connections.single().accessToken).isEqualTo("token-2")
            assertThat(downloads.removedServers).isEmpty()
            assertThat(restarts).isEqualTo(0)
        }

    @Test
    fun `removing the active account opens the next one`() =
        runTest {
            saveBoth()

            repository.remove("mediatheq.db", deleteDownloads = true)

            assertThat(repository.accounts.first().map { it.id }).containsExactly("mediatheq-2.db")
            assertThat(databases.selected).isEqualTo("mediatheq-2.db")
            assertThat(downloads.removedServers).containsExactly(ServerId("server-1"))
            assertThat(restarts).isEqualTo(1)
        }

    @Test
    fun `clean up keeps the database of an account whose token cannot be read`() =
        runTest {
            saveBoth()
            cipher.unreadable = setOf(cipher.encrypt(office.accessToken))

            repository.cleanUp()

            assertThat(databases.files).containsExactly("mediatheq.db", "mediatheq-2.db")
        }

    @Test
    fun `when the next account cannot be selected, the active one stays`() =
        runTest {
            saveBoth()
            databases.selectWorks = false

            repository.remove("mediatheq.db", deleteDownloads = false)

            assertThat(repository.accounts.first().map { it.id }).containsExactly("mediatheq.db", "mediatheq-2.db")
            assertThat(restarts).isEqualTo(0)
        }

    @Test
    fun `a disk that cannot be written keeps the order and the account, without crashing`() =
        runTest {
            saveBoth()
            dataStore.failing = true

            repository.reorder(listOf("mediatheq-2.db", "mediatheq.db"))
            repository.remove("mediatheq-2.db", deleteDownloads = false)

            dataStore.failing = false
            assertThat(repository.accounts.first().map { it.id }).containsExactly("mediatheq.db", "mediatheq-2.db")
            assertThat(databases.files).contains("mediatheq-2.db")
        }

    @Test
    fun `removing the last account is a sign-out`() =
        runTest {
            store.save(home)
            session.session.value = SessionState.LoggedIn("Home", "Ana")

            repository.remove("mediatheq.db", deleteDownloads = false)

            assertThat(session.session.value).isEqualTo(SessionState.LoggedOut())
            assertThat(restarts).isEqualTo(0)
        }

    @Test
    fun `downloads stay when another account uses the same server`() =
        runTest {
            store.save(home)
            store.save(office.copy(serverId = ServerId("server-1")))

            repository.remove("mediatheq-2.db", deleteDownloads = true)

            assertThat(downloads.removedServers).isEmpty()
        }

    @Test
    fun `an address of the same server is saved, the account keeps its id and token`() =
        runTest {
            saveBoth()
            session.serverCheck =
                ServerCheck.Supported("http://100.1.2.3:8096", ServerInfo(ServerId("server-1"), "Home", "12.3.0"))

            val check = repository.editAddress("mediatheq.db", "100.1.2.3")

            assertThat(check).isInstanceOf(ServerCheck.Supported::class.java)
            assertThat(store.account.first()).isEqualTo(MqResult.Success(home.copy(baseUrl = "http://100.1.2.3:8096")))
        }

    @Test
    fun `an address where another server answers is not saved`() =
        runTest {
            saveBoth()
            session.serverCheck =
                ServerCheck.Supported("http://other:8096", ServerInfo(ServerId("server-3"), "X", "12.3.0"))

            assertThat(repository.editAddress("mediatheq.db", "other")).isEqualTo(ServerCheck.OtherServer)
            assertThat(store.account.first()).isEqualTo(MqResult.Success(home))
        }

    @Test
    fun `clean up deletes databases no account owns, never the open one`() =
        runTest {
            store.save(home)

            repository.cleanUp()

            assertThat(databases.files).containsExactly("mediatheq.db")
        }

    @Test
    fun `clean up deletes nothing when the accounts cannot be read`() =
        runTest {
            store.save(home)
            dataStore.failing = true

            repository.cleanUp()

            assertThat(databases.files).containsExactly("mediatheq.db", "mediatheq-2.db", "orphan.db")
        }
}
