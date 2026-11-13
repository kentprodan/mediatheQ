package io.github.kentprodan.mediatheq.core.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.AudioOutputStore
import io.github.kentprodan.mediatheq.core.model.OutputId
import io.github.kentprodan.mediatheq.core.model.OutputKind
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    fun `custom gains are kept while another preset is chosen, an unknown band is ignored`() =
        runTest {
            repository.setEqBand(OutputId.Phone, 0, 5f)
            repository.setEqBand(OutputId.Phone, 5, 1f)
            val custom = outputs().profileOf(OutputId.Phone)

            repository.setProfile(OutputId.Phone, custom.copy(eqPreset = EqPreset.Vocal))
            assertThat(outputs().profileOf(OutputId.Phone).eqGainsDb).isEqualTo(EqPreset.Vocal.gainsDb)
            repository.setProfile(OutputId.Phone, custom.copy(eqPreset = EqPreset.Custom))

            assertThat(outputs().profileOf(OutputId.Phone).eqGainsDb).containsExactly(5f, 0f, 0f, 0f, 0f).inOrder()
        }

    @Test
    fun `quick changes of different settings each keep the other`() =
        runTest {
            coroutineScope {
                launch { repository.updateProfile(OutputId.Phone) { it.copy(bypass = true) } }
                launch { repository.setEqBand(OutputId.Phone, 0, 5f) }
                launch { repository.updateProfile(OutputId.Phone) { it.copy(normalization = Normalization.Off) } }
            }

            val profile = outputs().profileOf(OutputId.Phone)
            assertThat(profile.bypass).isTrue()
            assertThat(profile.normalization).isEqualTo(Normalization.Off)
            assertThat(profile.eqGainsDb.first()).isEqualTo(5f)
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

    @Test
    fun `an HDMI output passes Atmos through by default and keeps the switch off once set`() =
        runTest {
            val tv = OutputId(OutputKind.Hdmi, "tv")
            repository.use(tv, "LG TV")
            assertThat(outputs().profileOf(tv).atmosPassthrough).isTrue()

            repository.setProfile(tv, outputs().profileOf(tv).copy(atmosPassthrough = false))

            // Read back through a new repository on the same store.
            val again = DefaultAudioOutputRepository(AudioOutputStore(dataStore)).outputs.first()
            assertThat(again.profileOf(tv).atmosPassthrough).isFalse()
            assertThat(again.profileOf(OutputId.Phone).atmosPassthrough).isTrue()
        }
}
