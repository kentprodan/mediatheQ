package io.github.kentprodan.mediatheq.core.data

import kotlinx.coroutines.flow.Flow

/** Library and display preferences (grid, sorts, colors, ratings), kept across restarts. */
interface LibraryPreferencesRepository {
    val preferences: Flow<LibraryPreferences>

    suspend fun setFilterStyle(style: FilterControlStyle)

    /** Ignored outside [LibraryPreferences.ALBUM_COLUMNS]. */
    suspend fun setAlbumColumns(columns: Int)

    suspend fun setDynamicColor(on: Boolean)

    suspend fun setRatingMode(mode: RatingMode)

    suspend fun setOfflineMode(on: Boolean)

    /** Ignored when [filter] does not offer [sort]. */
    suspend fun setSort(
        filter: LibraryFilter,
        sort: LibrarySort,
    )

    /** Recent searches, newest first, at most [MAX_RECENT_SEARCHES]. Kept across sign-out, like the rest. */
    val recentSearches: Flow<List<String>>

    /** Puts [query] first (see [withRecentSearch]). */
    suspend fun addRecentSearch(query: String)

    suspend fun clearRecentSearches()
}
