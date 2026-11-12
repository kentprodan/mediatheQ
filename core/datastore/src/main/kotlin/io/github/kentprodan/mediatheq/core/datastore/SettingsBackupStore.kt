package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every setting of the app as stored (#150): the whole Library and Audio preference files, so a preference added later
 * is in the backup without anyone listing it. Values are String, Int, Long, Float, Double, Boolean or Set<String>.
 */
data class SettingsSnapshot(
    val library: Map<String, Any>,
    val audio: Map<String, Any>,
)

/**
 * Reads and replaces [SettingsSnapshot]s. Never touches the account file (tokens, DeviceId). [EXCLUDED] keys are not
 * settings (history, state): left out of a snapshot and kept on a replace.
 */
@Singleton
class SettingsBackupStore
    @Inject
    constructor(
        @param:LibraryPrefs private val library: DataStore<Preferences>,
        @param:AudioPrefs private val audio: DataStore<Preferences>,
        private val libraryStore: LibraryPreferencesStore,
        private val audioStore: AudioPreferencesStore,
        private val outputStore: AudioOutputStore,
    ) {
        /** Every setting now. Throws `IOException` when a file cannot be read. */
        suspend fun snapshot(): SettingsSnapshot = SettingsSnapshot(library.values(), audio.values())

        /**
         * Makes the settings exactly [snapshot]: anything not in it goes back to its default. Both files or neither: when
         * the second write fails the first is written back. Throws `IOException` when a file cannot be written, and
         * [WrongTypeException] (nothing written) when a value has a type this version reads differently.
         */
        suspend fun replace(snapshot: SettingsSnapshot) {
            val oldLibrary = library.data.first()
            library.edit {
                it.replaceWith(snapshot.library)
                it.check { prefs -> libraryStore.read(prefs) }
            }
            try {
                audio.edit { prefs ->
                    prefs.replaceWith(snapshot.audio)
                    // The output in use stays only while it has a profile among the restored ones.
                    val current = prefs[CURRENT_OUTPUT]
                    if (current != null && current != AudioOutputStore.PHONE && current !in prefs[OUTPUTS].orEmpty()) {
                        prefs.remove(CURRENT_OUTPUT)
                    }
                    prefs.check {
                        audioStore.read(it)
                        outputStore.read(it)
                    }
                }
            } catch (e: Exception) {
                library.edit { prefs ->
                    prefs.clear()
                    prefs += oldLibrary
                }
                throw e
            }
        }

        /**
         * Reads [prefs] as the stores do, inside the edit: a value of another type (a hand-edited file, a key whose type a
         * newer version changed) would otherwise be written and crash every reader of it.
         */
        private fun MutablePreferences.check(read: (Preferences) -> Unit) {
            try {
                read(this)
            } catch (e: ClassCastException) {
                throw WrongTypeException(e)
            }
        }

        private suspend fun DataStore<Preferences>.values(): Map<String, Any> =
            data
                .first()
                .asMap()
                .filterKeys { it.name !in EXCLUDED }
                .mapKeys { it.key.name }

        private fun MutablePreferences.replaceWith(values: Map<String, Any>) {
            val kept = asMap().filterKeys { it.name in EXCLUDED }
            clear()
            kept.forEach { (key, value) -> putAny(key.name, value) }
            values.filterKeys { it !in EXCLUDED }.forEach { (name, value) -> putAny(name, value) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun MutablePreferences.putAny(
            name: String,
            value: Any,
        ) {
            when (value) {
                is String -> {
                    this[stringPreferencesKey(name)] = value
                }

                is Int -> {
                    this[intPreferencesKey(name)] = value
                }

                is Long -> {
                    this[longPreferencesKey(name)] = value
                }

                is Float -> {
                    this[floatPreferencesKey(name)] = value
                }

                is Double -> {
                    this[doublePreferencesKey(name)] = value
                }

                is Boolean -> {
                    this[booleanPreferencesKey(name)] = value
                }

                is Set<*> -> {
                    this[stringSetPreferencesKey(name)] =
                        (value as Set<Any?>).filterIsInstance<String>().toSet()
                }

                else -> {
                    Unit
                } // A type DataStore cannot hold: skipped.
            }
        }

        companion object {
            /** Not settings: the recent searches (history) and the output in use (state). */
            val EXCLUDED = setOf("recent_searches", "current_output")
            private val CURRENT_OUTPUT = stringPreferencesKey("current_output")
            private val OUTPUTS = stringSetPreferencesKey("outputs")
        }
    }

/** A restored setting has a type this version of the app reads differently; nothing was written. */
class WrongTypeException(
    cause: ClassCastException,
) : IllegalArgumentException(cause)
