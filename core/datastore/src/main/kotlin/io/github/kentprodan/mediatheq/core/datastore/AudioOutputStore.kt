package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembered audio outputs and their profiles (#140), exactly as stored in the audio DataStore; `:core:data` validates
 * them. Ids are `OutputId` strings; [PHONE] is always there. They survive sign-out like the other audio settings.
 */
@Singleton
class AudioOutputStore
    @Inject
    constructor(
        @param:AudioPrefs private val dataStore: DataStore<Preferences>,
    ) {
        /** Stored values; only the phone when the file cannot be read. */
        val values: Flow<StoredOutputs> =
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map(::read)

        /** What [prefs] hold; also run on a restored backup before it is written (#150). */
        internal fun read(prefs: Preferences) =
            StoredOutputs(
                current = prefs[CURRENT],
                outputs =
                    (listOf(PHONE) + prefs[OUTPUTS].orEmpty().sorted()).map {
                        StoredOutput(it, prefs[stringPreferencesKey(it.key(NAME))], prefs.profile(it))
                    },
            )

        /** Makes [id] the current output; remembers it first (named [name], a copy of the phone's profile) if new. */
        suspend fun use(
            id: String,
            name: String,
        ) {
            dataStore.edit { prefs ->
                if (id != PHONE) {
                    val known = prefs[OUTPUTS].orEmpty()
                    if (id !in known) {
                        prefs[OUTPUTS] = known + id
                        prefs.writeProfile(id, prefs.profile(PHONE))
                    }
                    prefs[stringPreferencesKey(id.key(NAME))] = name
                }
                prefs[CURRENT] = id
            }
        }

        /** Stores every value of [id]'s profile at once; a `null` value removes it (the default). Unknown ids: no-op. */
        suspend fun setProfile(
            id: String,
            profile: StoredOutputProfile,
        ) {
            dataStore.edit { prefs ->
                if (id == PHONE || id in prefs[OUTPUTS].orEmpty()) prefs.writeProfile(id, profile)
            }
        }

        /** Forgets [id] with its name and profile; the phone cannot be forgotten. */
        suspend fun forget(id: String) {
            if (id == PHONE) return
            dataStore.edit { prefs ->
                prefs[OUTPUTS] = prefs[OUTPUTS].orEmpty() - id
                prefs.remove(stringPreferencesKey(id.key(NAME)))
                prefs.writeProfile(id, StoredOutputProfile())
                if (prefs[CURRENT] == id) prefs.remove(CURRENT)
            }
        }

        companion object {
            /** The phone's id, as `OutputId.Phone` writes it. */
            const val PHONE = "phone"

            private val OUTPUTS = stringSetPreferencesKey("outputs")
            private val CURRENT = stringPreferencesKey("current_output")
            private const val NAME = "name"
            private const val NORMALIZATION = "normalization"
            private const val BYPASS = "bypass"
            private const val EQUALIZER_ON = "eq_on"
            private const val EQ_PRESET = "eq_preset"
            private const val EQ_BANDS = "eq_bands"
            private const val BIT_PERFECT_USB = "bit_perfect"
            private const val ATMOS_PASSTHROUGH = "atmos_passthrough"

            private fun String.key(field: String) = "out.$this.$field"

            private fun Preferences.profile(id: String) =
                StoredOutputProfile(
                    normalization = this[stringPreferencesKey(id.key(NORMALIZATION))],
                    bypass = this[booleanPreferencesKey(id.key(BYPASS))],
                    equalizerOn = this[booleanPreferencesKey(id.key(EQUALIZER_ON))],
                    eqPreset = this[stringPreferencesKey(id.key(EQ_PRESET))],
                    eqBands = this[stringPreferencesKey(id.key(EQ_BANDS))],
                    bitPerfectUsb = this[booleanPreferencesKey(id.key(BIT_PERFECT_USB))],
                    atmosPassthrough = this[booleanPreferencesKey(id.key(ATMOS_PASSTHROUGH))],
                )

            private fun MutablePreferences.writeProfile(
                id: String,
                profile: StoredOutputProfile,
            ) {
                put(stringPreferencesKey(id.key(NORMALIZATION)), profile.normalization)
                put(booleanPreferencesKey(id.key(BYPASS)), profile.bypass)
                put(booleanPreferencesKey(id.key(EQUALIZER_ON)), profile.equalizerOn)
                put(stringPreferencesKey(id.key(EQ_PRESET)), profile.eqPreset)
                put(stringPreferencesKey(id.key(EQ_BANDS)), profile.eqBands)
                put(booleanPreferencesKey(id.key(BIT_PERFECT_USB)), profile.bitPerfectUsb)
                put(booleanPreferencesKey(id.key(ATMOS_PASSTHROUGH)), profile.atmosPassthrough)
            }

            private fun <T> MutablePreferences.put(
                key: Preferences.Key<T>,
                value: T?,
            ) {
                if (value == null) remove(key) else this[key] = value
            }

            /** The global keys before #140, their phone counterparts. */
            private val GLOBAL_TO_PHONE: List<Pair<Preferences.Key<*>, Preferences.Key<*>>> =
                listOf(
                    booleanPreferencesKey("bit_perfect_usb") to booleanPreferencesKey(PHONE.key(BIT_PERFECT_USB)),
                    booleanPreferencesKey("equalizer_on") to booleanPreferencesKey(PHONE.key(EQUALIZER_ON)),
                    stringPreferencesKey("eq_preset") to stringPreferencesKey(PHONE.key(EQ_PRESET)),
                    stringPreferencesKey("eq_bands") to stringPreferencesKey(PHONE.key(EQ_BANDS)),
                    stringPreferencesKey("normalization") to stringPreferencesKey(PHONE.key(NORMALIZATION)),
                )

            /**
             * Moves the global equalizer, normalization and bit-perfect values (before #140) into the phone's profile,
             * once: new outputs copy the phone, so a DAC plugged in later keeps bit-perfect on.
             */
            val GlobalToPhoneMigration: DataMigration<Preferences> =
                object : DataMigration<Preferences> {
                    override suspend fun shouldMigrate(currentData: Preferences) =
                        GLOBAL_TO_PHONE.any { (global, _) -> global in currentData }

                    override suspend fun migrate(currentData: Preferences): Preferences =
                        currentData.toMutablePreferences().apply {
                            GLOBAL_TO_PHONE.forEach { (global, phone) -> move(global, phone) }
                        }

                    override suspend fun cleanUp() = Unit
                }

            @Suppress("UNCHECKED_CAST")
            private fun MutablePreferences.move(
                from: Preferences.Key<*>,
                to: Preferences.Key<*>,
            ) {
                val value = this[from] ?: return
                if (to !in this) this[to as Preferences.Key<Any>] = value
                remove(from)
            }
        }
    }

/** The outputs as stored: the phone first, then the others by id. [current] may name one that is gone. */
data class StoredOutputs(
    val current: String?,
    val outputs: List<StoredOutput>,
)

data class StoredOutput(
    val id: String,
    /** The device's name; `null` for the phone. */
    val name: String?,
    val profile: StoredOutputProfile,
)

/** One output's profile as stored; `null` = not set. */
data class StoredOutputProfile(
    val normalization: String? = null,
    val bypass: Boolean? = null,
    val equalizerOn: Boolean? = null,
    val eqPreset: String? = null,
    /** Custom band gains in dB, comma separated. */
    val eqBands: String? = null,
    val bitPerfectUsb: Boolean? = null,
    val atmosPassthrough: Boolean? = null,
)
