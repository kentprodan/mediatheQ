package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.FilterControlStyle
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [LibraryPreferencesRepository] with the same validation rules as the real one. */
class FakeLibraryPreferencesRepository : LibraryPreferencesRepository {
    override val preferences = MutableStateFlow(LibraryPreferences())

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
