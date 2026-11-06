package io.github.kentprodan.mediatheq.core.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.AudioOutputStore
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultAudioOutputRepositoryTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = DefaultAudioOutputRepository(AudioOutputStore(dataStore))
    private val headphones = OutputId(OutputKind.Bluetooth, "m4")
    private val dac = OutputId(OutputKind.Usb, "dac")

    private suspend fun outputs() = repository.outputs.first()

    @Test
    fun `at first only the phone, current, with default settings`() =
        runTest {
            assertThat(outputs()).isEqualTo(RememberedOutputs())
            assertThat(OutputId.Phone.toString()).isEqualTo(AudioOutputStore.PHONE)
        }

    @Test
    fun `the phone comes first, then the others by name, the current one marked`() =
        runTest {
            repository.use(headphones, "MOMENTUM 4")
            repository.use(dac, "Apple USB-C")

            assertThat(outputs().outputs.map { it.name }).containsExactly("", "Apple USB-C", "MOMENTUM 4").inOrder()
            assertThat(outputs().current).isEqualTo(dac)
        }

    @Test
    fun `a new output starts from the phone's settings, then keeps its own`() =
        runTest {
            val phone = OutputProfile(normalization = Normalization.Track, equalizerOn = true, bitPerfectUsb = true)
            repository.setProfile(OutputId.Phone, phone)

            repository.use(dac, "DAC")
            repository.setProfile(dac, phone.copy(bypass = true))

            assertThat(outputs().profileOf(dac)).isEqualTo(phone.copy(bypass = true))
            assertThat(outputs().profileOf(OutputId.Phone)).isEqualTo(phone)
        }

    @Test
    fun `an output that is not remembered gets the phone's profile`() =
        runTest {
            repository.setProfile(OutputId.Phone, OutputProfile(bypass = true))

            assertThat(outputs().profileOf(headphones)).isEqualTo(OutputProfile(bypass = true))
        }

    @Test
    fun `restore defaults resets only that output`() =
        runTest {
            repository.use(headphones, "MOMENTUM 4")
            repository.setProfile(headphones, OutputProfile(equalizerOn = true))
            repository.setProfile(OutputId.Phone, OutputProfile(bypass = true))

            repository.setProfile(headphones, null)

            assertThat(outputs().profileOf(headphones)).isEqualTo(OutputProfile())
            assertThat(outputs().profileOf(OutputId.Phone)).isEqualTo(OutputProfile(bypass = true))
        }

    @Test
    fun `a band change switches that output to custom gains`() =
        runTest {
            repository.use(headphones, "MOMENTUM 4")
            repository.setProfile(headphones, OutputProfile(eqPreset = EqPreset.BassBoost))

            repository.setEqBand(headphones, 4, 20f)

            val profile = outputs().profileOf(headphones)
            assertThat(profile.eqPreset).isEqualTo(EqPreset.Custom)
            assertThat(profile.eqGainsDb).containsExactly(6f, 4f, 0f, 0f, 12f).inOrder()
            assertThat(outputs().profileOf(OutputId.Phone)).isEqualTo(OutputProfile())
        }

    @Test
    fun `forgetting the current output makes the phone current, the phone stays`() =
        runTest {
            repository.use(headphones, "MOMENTUM 4")

            repository.forget(headphones)
            repository.forget(OutputId.Phone)

            assertThat(outputs()).isEqualTo(RememberedOutputs())
        }

    @Test
    fun `damaged stored values read as the defaults`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("out.phone.normalization")] = "Loud"
                it[stringPreferencesKey("out.phone.eq_bands")] = "1,2"
                it[stringPreferencesKey("current_output")] = "hdmi:1"
            }

            assertThat(outputs()).isEqualTo(RememberedOutputs())
        }
}
