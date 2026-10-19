package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw Library preferences: names and numbers exactly as stored. `:core:data` validates them, so an old or unknown value
 * never reaches the UI. They survive sign-out (only the account is cleared).
 */
@Singleton
class LibraryPreferencesStore
    @Inject
    constructor(
        @param:LibraryPrefs private val dataStore: DataStore<Preferences>,
    ) {
        /** Stored values; empty preferences when the file cannot be read. */
        val values: Flow<StoredLibraryPreferences> =
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { prefs ->
                    StoredLibraryPreferences(
                        filterStyle = prefs[FILTER_STYLE],
                        albumColumns = prefs[ALBUM_COLUMNS],
                        sorts =
                            prefs
                                .asMap()
                                .filterKeys { it.name.startsWith(SORT_PREFIX) }
                                .map { (key, value) -> key.name.removePrefix(SORT_PREFIX) to value.toString() }
                                .toMap(),
                    )
                }

        suspend fun setFilterStyle(name: String) {
            dataStore.edit { it[FILTER_STYLE] = name }
        }

        suspend fun setAlbumColumns(columns: Int) {
            dataStore.edit { it[ALBUM_COLUMNS] = columns }
        }

        /** Stores the sort [name] for the Library tab [tab]. */
        suspend fun setSort(
            tab: String,
            name: String,
        ) {
            dataStore.edit { it[stringPreferencesKey(SORT_PREFIX + tab)] = name }
        }

        private companion object {
            val FILTER_STYLE = stringPreferencesKey("filter_style")
            val ALBUM_COLUMNS = intPreferencesKey("album_columns")
            const val SORT_PREFIX = "sort_"
        }
    }

/** Library preferences as stored; any field may be missing or out of date. */
data class StoredLibraryPreferences(
    val filterStyle: String? = null,
    val albumColumns: Int? = null,
    /** Tab name -> sort name. */
    val sorts: Map<String, String> = emptyMap(),
)
