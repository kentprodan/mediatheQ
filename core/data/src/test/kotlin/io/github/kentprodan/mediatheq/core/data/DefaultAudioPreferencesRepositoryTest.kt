package io.github.kentprodan.mediatheq.core.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.datastore.AudioPreferencesStore
import io.github.kentprodan.mediatheq.core.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultAudioPreferencesRepositoryTest {
    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = DefaultAudioPreferencesRepository(AudioPreferencesStore(dataStore))

    @Test
    fun `defaults are bit-perfect off and a flat equalizer that is off`() =
        runTest {
            val preferences = repository.preferences.first()

            assertThat(preferences).isEqualTo(AudioPreferences())
            assertThat(preferences.bitPerfectUsb).isFalse()
            assertThat(preferences.equalizerOn).isFalse()
            assertThat(preferences.eqGainsDb).containsExactly(0f, 0f, 0f, 0f, 0f).inOrder()
        }

    @Test
    fun `toggles are kept`() =
        runTest {
            repository.setBitPerfectUsb(true)
            repository.setEqualizerOn(true)

            val preferences = repository.preferences.first()
            assertThat(preferences.bitPerfectUsb).isTrue()
            assertThat(preferences.equalizerOn).isTrue()
        }

    @Test
    fun `a preset gives its gains`() =
        runTest {
            repository.setEqPreset(EqPreset.BassBoost)

            assertThat(repository.preferences.first().eqGainsDb).isEqualTo(EqPreset.BassBoost.gainsDb)
        }

    @Test
    fun `moving a band starts from the preset heard and switches to custom`() =
        runTest {
            repository.setEqPreset(EqPreset.BassBoost)

            repository.setEqBand(index = 4, gainDb = 3f)

            val preferences = repository.preferences.first()
            assertThat(preferences.eqPreset).isEqualTo(EqPreset.Custom)
            assertThat(preferences.eqGainsDb).containsExactly(6f, 4f, 0f, 0f, 3f).inOrder()
        }

    @Test
    fun `custom gains are kept while another preset is chosen`() =
        runTest {
            repository.setEqBand(index = 0, gainDb = 5f)
            repository.setEqPreset(EqPreset.Vocal)

            repository.setEqPreset(EqPreset.Custom)

            assertThat(repository.preferences.first().eqGainsDb).containsExactly(5f, 0f, 0f, 0f, 0f).inOrder()
        }

    @Test
    fun `a band gain is clamped and an unknown band is ignored`() =
        runTest {
            repository.setEqBand(index = 2, gainDb = 30f)
            repository.setEqBand(index = 5, gainDb = 1f)

            assertThat(repository.preferences.first().eqGainsDb).containsExactly(0f, 0f, 12f, 0f, 0f).inOrder()
        }

    @Test
    fun `damaged stored values fall back to the defaults`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("eq_preset")] = "Loudness"
                it[stringPreferencesKey("eq_bands")] = "1,2,x"
            }

            val preferences = repository.preferences.first()
            assertThat(preferences.eqPreset).isEqualTo(EqPreset.Flat)
            assertThat(preferences.customGainsDb).isEqualTo(AudioPreferences().customGainsDb)
        }

    @Test
    fun `streaming defaults are original on Wi-Fi, 256 on mobile, album gain, 2 GB, Wi-Fi downloads`() =
        runTest {
            val preferences = repository.preferences.first()

            assertThat(preferences.qualityFor(metered = false)).isEqualTo(StreamingQuality.Original)
            assertThat(preferences.qualityFor(metered = true)).isEqualTo(StreamingQuality.Kbps256)
            assertThat(preferences.normalization).isEqualTo(Normalization.Album)
            assertThat(preferences.streamingCacheGb).isEqualTo(2)
            assertThat(preferences.downloadsUnmeteredOnly).isTrue()
        }

    @Test
    fun `streaming and storage choices are kept, each network on its own`() =
        runTest {
            repository.setStreamingQuality(metered = true, StreamingQuality.Kbps128)
            repository.setStreamingQuality(metered = false, StreamingQuality.Kbps256)
            repository.setNormalization(Normalization.Track)
            repository.setStreamingCacheGb(8)
            repository.setDownloadsUnmeteredOnly(false)

            val preferences = repository.preferences.first()
            assertThat(preferences.meteredQuality).isEqualTo(StreamingQuality.Kbps128)
            assertThat(preferences.unmeteredQuality).isEqualTo(StreamingQuality.Kbps256)
            assertThat(preferences.normalization).isEqualTo(Normalization.Track)
            assertThat(preferences.streamingCacheGb).isEqualTo(8)
            assertThat(preferences.downloadsUnmeteredOnly).isFalse()
        }

    @Test
    fun `a cache size that is not offered is refused`() =
        runTest {
            repository.setStreamingCacheGb(3)

            assertThat(repository.preferences.first().streamingCacheGb).isEqualTo(2)
        }

    @Test
    fun `unknown stored names and sizes fall back to the defaults`() =
        runTest {
            dataStore.edit {
                it[stringPreferencesKey("normalization")] = "Loudest"
                it[stringPreferencesKey("metered_quality")] = "Kbps999"
                it[
                    androidx.datastore.preferences.core
                        .intPreferencesKey("streaming_cache_gb"),
                ] = 100
            }

            assertThat(repository.preferences.first()).isEqualTo(AudioPreferences())
        }
}
