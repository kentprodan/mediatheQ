package io.github.kentprodan.mediatheq.core.datastore

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

    @Test
    fun `the DeviceId is a random UUID created once`() =
        runTest {
            val store = store()

            val first = store.deviceId()

            assertThat(first).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            assertThat(store.deviceId()).isEqualTo(first)
        }
}
