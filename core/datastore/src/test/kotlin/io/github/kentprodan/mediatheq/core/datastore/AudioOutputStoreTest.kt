package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.AudioOutputStore.Companion.GlobalToPhoneMigration
import io.github.kentprodan.mediatheq.core.datastore.AudioOutputStore.Companion.PHONE
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AudioOutputStoreTest {
    private val store = AudioOutputStore(InMemoryPreferencesDataStore())

    private suspend fun output(id: String) =
        store.values
            .first()
            .outputs
            .single { it.id == id }

    @Test
    fun `a fresh store has only the phone, with nothing set`() =
        runTest {
            val values = store.values.first()

            assertThat(values.current).isNull()
            assertThat(values.outputs).containsExactly(StoredOutput(PHONE, null, StoredOutputProfile()))
        }

    @Test
    fun `a new output copies the phone's profile and becomes current`() =
        runTest {
            val phone = StoredOutputProfile(normalization = "Track", equalizerOn = true, eqBands = "1,2,3,4,5")
            store.setProfile(PHONE, phone)

            store.use("bluetooth:ab", "MOMENTUM 4")

            assertThat(output("bluetooth:ab")).isEqualTo(StoredOutput("bluetooth:ab", "MOMENTUM 4", phone))
            assertThat(store.values.first().current).isEqualTo("bluetooth:ab")
        }

    @Test
    fun `using a known output again keeps its own profile`() =
        runTest {
            store.use("usb:dac", "DAC")
            store.setProfile("usb:dac", StoredOutputProfile(bitPerfectUsb = true))
            store.setProfile(PHONE, StoredOutputProfile(bypass = true))

            store.use(PHONE, "")
            store.use("usb:dac", "DAC")

            assertThat(output("usb:dac").profile).isEqualTo(StoredOutputProfile(bitPerfectUsb = true))
        }

    @Test
    fun `profiles of unknown outputs are not written`() =
        runTest {
            store.setProfile("usb:gone", StoredOutputProfile(bypass = true))

            assertThat(
                store.values
                    .first()
                    .outputs
                    .map { it.id },
            ).containsExactly(PHONE)
        }

    @Test
    fun `forgetting drops the output, its profile and its current mark, never the phone`() =
        runTest {
            store.use("bluetooth:ab", "Car")
            store.setProfile("bluetooth:ab", StoredOutputProfile(bypass = true))

            store.forget("bluetooth:ab")
            store.forget(PHONE)
            store.use("bluetooth:ab", "Car")

            val values = store.values.first()
            assertThat(values.outputs.map { it.id }).containsExactly(PHONE, "bluetooth:ab").inOrder()
            assertThat(output("bluetooth:ab").profile).isEqualTo(StoredOutputProfile())
        }

    @Test
    fun `the old global values move into the phone's profile once`() =
        runTest {
            val old =
                mutablePreferencesOf(
                    booleanPreferencesKey("bit_perfect_usb") to true,
                    booleanPreferencesKey("equalizer_on") to true,
                    stringPreferencesKey("eq_preset") to "Custom",
                    stringPreferencesKey("eq_bands") to "1,2,3,4,5",
                    stringPreferencesKey("normalization") to "Off",
                    stringPreferencesKey("metered_quality") to "Kbps128",
                )
            assertThat(GlobalToPhoneMigration.shouldMigrate(old)).isTrue()

            val migrated = GlobalToPhoneMigration.migrate(old)

            assertThat(GlobalToPhoneMigration.shouldMigrate(migrated)).isFalse()
            assertThat(migrated[stringPreferencesKey("metered_quality")]).isEqualTo("Kbps128")
            val dataStore = InMemoryPreferencesDataStore().apply { updateData { migrated } }
            assertThat(
                AudioOutputStore(dataStore)
                    .values
                    .first()
                    .outputs
                    .single()
                    .profile,
            ).isEqualTo(
                StoredOutputProfile(
                    normalization = "Off",
                    equalizerOn = true,
                    eqPreset = "Custom",
                    eqBands = "1,2,3,4,5",
                    bitPerfectUsb = true,
                ),
            )
        }

    @Test
    fun `a fresh install has nothing to migrate`() =
        runTest {
            assertThat(GlobalToPhoneMigration.shouldMigrate(mutablePreferencesOf())).isFalse()
        }
}
