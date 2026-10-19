package io.github.kentprodan.mediatheq.core.data

import kotlinx.coroutines.flow.Flow

/** Library screen preferences, kept across restarts. */
interface LibraryPreferencesRepository {
    val preferences: Flow<LibraryPreferences>

    suspend fun setFilterStyle(style: FilterControlStyle)

    /** Ignored outside [LibraryPreferences.ALBUM_COLUMNS]. */
    suspend fun setAlbumColumns(columns: Int)

    /** Ignored when [filter] does not offer [sort]. */
    suspend fun setSort(
        filter: LibraryFilter,
        sort: LibrarySort,
    )
}
