package io.github.kentprodan.mediatheq.core.datastore

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.testing.FakeTokenCipher
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class StoredSyncRecordTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val store = AccountStore(dataStore, FakeTokenCipher())
    private val account =
        StoredAccount("http://nas:8096", ServerId("server-1"), "Home", "user-1", "Ana", accessToken = "secret-token")
    private val record = StoredSyncRecord(Instant.fromEpochMilliseconds(1_000), null, 3120, 24.seconds)

    @Test
    fun `the active account's record is saved and read back`() =
        runTest {
            store.save(account)

            store.saveSyncRecord(record)

            assertThat(store.syncRecords.first()).containsExactly(account.id, record)
        }

    @Test
    fun `no record is written once the account is gone`() =
        runTest {
            store.saveSyncRecord(record)
            store.save(account)

            assertThat(store.syncRecords.first()).isEmpty()
        }

    @Test
    fun `removing the account removes its record`() =
        runTest {
            store.save(account)
            store.saveSyncRecord(record)

            store.remove(account.id)
            store.save(account)

            assertThat(store.syncRecords.first()).isEmpty()
        }

    @Test
    fun `every error reads back as itself`() {
        val errors =
            listOf(
                MqError.Network,
                MqError.Unauthorized,
                MqError.Forbidden,
                MqError.NotFound,
                MqError.Server(503),
                MqError.Client(400),
                MqError.Storage,
            )
        errors.forEach { error ->
            assertThat(decodeSyncRecord(record.copy(error = error).encode())).isEqualTo(record.copy(error = error))
        }
    }

    @Test
    fun `an unknown error reads back as Unknown`() {
        val decoded = decodeSyncRecord(record.copy(error = MqError.Unknown(RuntimeException("x"))).encode())

        assertThat(decoded?.error).isInstanceOf(MqError.Unknown::class.java)
    }

    @Test
    fun `an unreadable value is ignored`() {
        assertThat(decodeSyncRecord("garbage")).isNull()
        assertThat(decodeSyncRecord("1|x|2|")).isNull()
        assertThat(decodeSyncRecord("1|2|3|Martian:7")?.error).isInstanceOf(MqError.Unknown::class.java)
    }
}
