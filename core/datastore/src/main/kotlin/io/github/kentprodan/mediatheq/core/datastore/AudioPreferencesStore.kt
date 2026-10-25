package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Raw audio preferences, exactly as stored; `:core:data` validates them. They survive sign-out. */
@Singleton
class AudioPreferencesStore
    @Inject
    constructor(
        @param:AudioPrefs private val dataStore: DataStore<Preferences>,
    ) {
        /** Stored values; empty preferences when the file cannot be read. */
        val values: Flow<StoredAudioPreferences> =
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { prefs ->
                    StoredAudioPreferences(
                        bitPerfectUsb = prefs[BIT_PERFECT_USB],
                        equalizerOn = prefs[EQUALIZER_ON],
                        eqPreset = prefs[EQ_PRESET],
                        eqBands = prefs[EQ_BANDS],
                    )
                }

        suspend fun setBitPerfectUsb(on: Boolean) {
            dataStore.edit { it[BIT_PERFECT_USB] = on }
        }

        suspend fun setEqualizerOn(on: Boolean) {
            dataStore.edit { it[EQUALIZER_ON] = on }
        }

        /** Stores the preset [name], and the custom band gains when [bands] is not `null`, in one write. */
        suspend fun setEq(
            name: String,
            bands: String?,
        ) {
            dataStore.edit { prefs ->
                prefs[EQ_PRESET] = name
                bands?.let { prefs[EQ_BANDS] = it }
            }
        }

        private companion object {
            val BIT_PERFECT_USB = booleanPreferencesKey("bit_perfect_usb")
            val EQUALIZER_ON = booleanPreferencesKey("equalizer_on")
            val EQ_PRESET = stringPreferencesKey("eq_preset")
            val EQ_BANDS = stringPreferencesKey("eq_bands")
        }
    }

/** Audio preferences as stored; any field may be missing or out of date. */
data class StoredAudioPreferences(
    val bitPerfectUsb: Boolean? = null,
    val equalizerOn: Boolean? = null,
    val eqPreset: String? = null,
    /** Custom band gains in dB, comma separated. */
    val eqBands: String? = null,
)
