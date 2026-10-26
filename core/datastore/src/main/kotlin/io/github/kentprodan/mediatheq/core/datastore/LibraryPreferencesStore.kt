package io.github.kentprodan.mediatheq.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        private val data = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

        /** Stored values; empty preferences when the file cannot be read. */
        val values: Flow<StoredLibraryPreferences> =
            data
                .map { prefs ->
                    StoredLibraryPreferences(
                        filterStyle = prefs[FILTER_STYLE],
                        albumColumns = prefs[ALBUM_COLUMNS],
                        dynamicColor = prefs[DYNAMIC_COLOR],
                        ratingMode = prefs[RATING_MODE],
                        offlineMode = prefs[OFFLINE_MODE],
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

        suspend fun setDynamicColor(on: Boolean) {
            dataStore.edit { it[DYNAMIC_COLOR] = on }
        }

        suspend fun setRatingMode(name: String) {
            dataStore.edit { it[RATING_MODE] = name }
        }

        suspend fun setOfflineMode(on: Boolean) {
            dataStore.edit { it[OFFLINE_MODE] = on }
        }

        /** Stores the sort [name] for the Library tab [tab]. */
        suspend fun setSort(
            tab: String,
            name: String,
        ) {
            dataStore.edit { it[stringPreferencesKey(SORT_PREFIX + tab)] = name }
        }

        /** Recent searches in stored order; `:core:data` decides the order and the limit. */
        val recentSearches: Flow<List<String>> = data.map { it[RECENT_SEARCHES].toList() }

        /**
         * Replaces the recent searches with [transform] of the stored ones, atomically. Best effort: a failed write is
         * dropped, since a lost recent search is not worth crashing a tap.
         */
        suspend fun updateRecentSearches(transform: (List<String>) -> List<String>) {
            try {
                dataStore.edit { prefs ->
                    val searches = transform(prefs[RECENT_SEARCHES].toList())
                    if (searches.isEmpty()) {
                        prefs.remove(RECENT_SEARCHES)
                    } else {
                        prefs[RECENT_SEARCHES] = searches.joinToString(SEPARATOR)
                    }
                }
            } catch (_: IOException) {
            }
        }

        // One string, one search per line: callers keep searches on a single line.
        private fun String?.toList() = this?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

        private companion object {
            val FILTER_STYLE = stringPreferencesKey("filter_style")
            val ALBUM_COLUMNS = intPreferencesKey("album_columns")
            val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
            val RATING_MODE = stringPreferencesKey("rating_mode")
            val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
            const val SORT_PREFIX = "sort_"
            val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
            const val SEPARATOR = "\n"
        }
    }

/** Library preferences as stored; any field may be missing or out of date. */
data class StoredLibraryPreferences(
    val filterStyle: String? = null,
    val albumColumns: Int? = null,
    /** Tab name -> sort name. */
    val sorts: Map<String, String> = emptyMap(),
    val dynamicColor: Boolean? = null,
    val ratingMode: String? = null,
    val offlineMode: Boolean? = null,
)
