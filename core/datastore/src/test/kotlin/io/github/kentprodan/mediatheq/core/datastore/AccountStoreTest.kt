package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.common.MqResult
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AccountStoreTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val cipher = FakeTokenCipher()
    private val account =
        StoredAccount("http://nas:8096", ServerId("server-1"), "Home", "user-1", "Ana", accessToken = "secret-token")

    private fun store() = AccountStore(dataStore, cipher)

    @Test
    fun `nothing is saved on first start`() =
        runTest {
            assertThat(store().account.first()).isEqualTo(MqResult.Success(null))
        }

    @Test
    fun `a saved account reads back with its token`() =
        runTest {
            val store = store()

            store.save(account)

            assertThat(store.account.first()).isEqualTo(MqResult.Success(account))
        }

    @Test
    fun `the token is never written in plain text`() =
        runTest {
            store().save(account)

            assertThat(dataStore.current.toString()).doesNotContain("secret-token")
        }

    @Test
    fun `a token that cannot be decrypted fails with Storage`() =
        runTest {
            val store = store()
            store.save(account)

            cipher.failing = true

            assertThat(store.account.first()).isEqualTo(MqResult.Failure(MqError.Storage))
        }

    @Test
    fun `an unreadable disk fails with Storage instead of crashing`() =
        runTest {
            dataStore.failing = true

            assertThat(store().account.first()).isEqualTo(MqResult.Failure(MqError.Storage))
        }

    @Test
    fun `the DeviceId stays stable for the process when the disk fails`() =
        runTest {
            val store = store()
            dataStore.failing = true

            assertThat(store.deviceId()).isEqualTo(store.deviceId())
        }

    @Test
    fun `saving replaces the previous token`() =
        runTest {
            val store = store()
            store.save(account)

            store.save(account.copy(accessToken = "newer-token"))

            assertThat((store.account.first() as MqResult.Success).value?.accessToken).isEqualTo("newer-token")
        }

    @Test
    fun `clear forgets the account but keeps the DeviceId`() =
        runTest {
            val store = store()
            val deviceId = store.deviceId()
            store.save(account)

            store.clear()

            assertThat(store.account.first()).isEqualTo(MqResult.Success(null))
            assertThat(store.deviceId()).isEqualTo(deviceId)
        }

    private val second =
        account.copy(
            serverId = ServerId("server-2"),
            serverName = "Office",
            accessToken = "other",
            id = "mediatheq-2.db",
        )

    @Test
    fun `accounts list in the order they were added, the active one is the open database's`() =
        runTest {
            val store = AccountStore(dataStore, cipher, activeId = "mediatheq-2.db")
            store.save(account)
            store.save(second)

            assertThat(store.accounts.first()).isEqualTo(MqResult.Success(listOf(account, second)))
            assertThat(store.account.first()).isEqualTo(MqResult.Success(second))
            assertThat(store.baseUrl.first()).isEqualTo(second.baseUrl)
        }

    @Test
    fun `saving under an existing id replaces it in place`() =
        runTest {
            val store = store()
            store.save(account)
            store.save(second)

            store.save(account.copy(baseUrl = "https://nas.example.org"))

            assertThat((store.accounts.first() as MqResult.Success).value.map { it.baseUrl })
                .containsExactly("https://nas.example.org", second.baseUrl)
                .inOrder()
        }

    @Test
    fun `an account whose token cannot be read is left out, the others stay`() =
        runTest {
            val store = AccountStore(dataStore, cipher, activeId = "mediatheq-2.db")
            store.save(account)
            store.save(second)
            cipher.unreadable = setOf(cipher.encrypt(account.accessToken))

            assertThat(store.accounts.first()).isEqualTo(MqResult.Success(listOf(second)))
            assertThat(store.account.first()).isEqualTo(MqResult.Success(second))
            assertThat(store.ids()).containsExactly("mediatheq.db", "mediatheq-2.db").inOrder()
        }

    @Test
    fun `reorder keeps unknown ids out and the left out ones last`() =
        runTest {
            val store = store()
            val third = second.copy(id = "mediatheq-3.db")
            listOf(account, second, third).forEach { store.save(it) }

            store.reorder(listOf("mediatheq-3.db", "gone.db", "mediatheq.db"))

            assertThat((store.accounts.first() as MqResult.Success).value.map { it.id })
                .containsExactly("mediatheq-3.db", "mediatheq.db", "mediatheq-2.db")
                .inOrder()
        }

    @Test
    fun `removing one account keeps the other and its token`() =
        runTest {
            val store = store()
            store.save(account)
            store.save(second)

            store.remove(account.id)

            assertThat(store.accounts.first()).isEqualTo(MqResult.Success(listOf(second)))
            assertThat(store.account.first()).isEqualTo(MqResult.Success(null))
            assertThat(dataStore.current.toString()).doesNotContain("server-1")
        }

    @Test
    fun `the single account of before 147 becomes the first, active account`() =
        runTest {
            val legacy = InMemoryPreferencesDataStore()
            legacy.updateData {
                it.toMutablePreferences().apply {
                    this[stringPreferencesKey("base_url")] = "http://nas:8096"
                    this[stringPreferencesKey("server_id")] = "server-1"
                    this[stringPreferencesKey("server_name")] = "Home"
                    this[stringPreferencesKey("user_id")] = "user-1"
                    this[stringPreferencesKey("user_name")] = "Ana"
                    this[stringPreferencesKey("access_token_encrypted")] = cipher.encrypt("secret-token")
                    this[stringPreferencesKey("device_id")] = "device-1"
                }
            }
            val migration = AccountStore.SingleToListMigration
            assertThat(migration.shouldMigrate(legacy.current)).isTrue()

            legacy.updateData { migration.migrate(it) }
            val store = AccountStore(legacy, cipher)

            assertThat(migration.shouldMigrate(legacy.current)).isFalse()
            assertThat(store.accounts.first()).isEqualTo(MqResult.Success(listOf(account)))
            assertThat(store.account.first()).isEqualTo(MqResult.Success(account))
            assertThat(store.deviceId()).isEqualTo("device-1")
        }

    @Test
    fun `nothing migrates on a new install`() =
        runTest {
            assertThat(AccountStore.SingleToListMigration.shouldMigrate(dataStore.current)).isFalse()
        }

    @Test
    fun `the DeviceId is a random UUID created once`() =
        runTest {
            val store = store()

            val first = store.deviceId()

            assertThat(first).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            assertThat(store.deviceId()).isEqualTo(first)
        }

    @Test
    fun `a signed-out account is saved without a token and listed as such, never among the signed-in ones`() =
        runTest {
            val store = store()
            store.save(account)
            val restored =
                SavedAccount("mediatheq-2.db", "https://office.org", ServerId("server-2"), "Office", "u9", "Bo")

            store.saveSignedOut(restored)
            store.saveSignedOut(restored.copy(serverName = "Again"))

            assertThat(store.saved.first())
                .containsExactly(
                    SavedAccount(
                        account.id,
                        account.baseUrl,
                        account.serverId,
                        "Home",
                        "user-1",
                        "Ana",
                        signedIn = true,
                    ),
                    restored,
                ).inOrder()
            assertThat(store.accounts.first()).isEqualTo(MqResult.Success(listOf(account)))
            assertThat(
                dataStore.current
                    .asMap()
                    .keys
                    .map { it.name },
            ).doesNotContain("access_token_encrypted@mediatheq-2.db")
        }

    @Test
    fun `an account whose token cannot be decrypted lists as signed out`() =
        runTest {
            val store = store()
            store.save(account)
            cipher.failing = true

            assertThat(
                store.saved
                    .first()
                    .single()
                    .signedIn,
            ).isFalse()
        }
}
