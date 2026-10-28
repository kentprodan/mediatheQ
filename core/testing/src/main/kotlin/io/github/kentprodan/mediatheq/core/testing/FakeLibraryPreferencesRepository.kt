package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.FilterControlStyle
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.data.withRecentSearch
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [LibraryPreferencesRepository] with the same validation rules as the real one. */
class FakeLibraryPreferencesRepository : LibraryPreferencesRepository {
    override val preferences = MutableStateFlow(LibraryPreferences())

    override val recentSearches = MutableStateFlow<List<String>>(emptyList())

    override suspend fun addRecentSearch(query: String) {
        recentSearches.value = recentSearches.value.withRecentSearch(query)
    }

    override suspend fun clearRecentSearches() {
        recentSearches.value = emptyList()
    }

    override suspend fun setFilterStyle(style: FilterControlStyle) {
        preferences.value = preferences.value.copy(filterStyle = style)
    }

    override suspend fun setAlbumColumns(columns: Int) {
        if (columns in
            LibraryPreferences.ALBUM_COLUMNS
        ) {
            preferences.value = preferences.value.copy(albumColumns = columns)
        }
    }

    override suspend fun setTheme(theme: ThemePreferences) {
        preferences.value = preferences.value.copy(theme = theme.copy(customSeed = theme.customSeed or OPAQUE))
    }

    override suspend fun restoreThemeDefaults() {
        preferences.value = preferences.value.copy(theme = ThemePreferences())
    }

    override suspend fun setOfflineMode(on: Boolean) {
        preferences.value = preferences.value.copy(offlineMode = on)
    }

    override suspend fun setRatingMode(mode: RatingMode) {
        preferences.value = preferences.value.copy(ratingMode = mode)
    }

    override suspend fun setSort(
        filter: LibraryFilter,
        sort: LibrarySort,
    ) {
        if (sort in
            filter.sorts
        ) {
            preferences.value = preferences.value.copy(sorts = preferences.value.sorts + (filter to sort))
        }
    }
}

private const val OPAQUE = 0xFF000000.toInt()
