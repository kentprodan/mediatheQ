package io.github.kentprodan.mediatheq.core.data

/** The Library tabs, each with the sorts it offers (first = default). */
enum class LibraryFilter(
    val sorts: List<LibrarySort>,
) {
    Albums(listOf(LibrarySort.Name, LibrarySort.RecentlyAdded, LibrarySort.Year, LibrarySort.Artist)),
    Artists(listOf(LibrarySort.Name)),
    Playlists(listOf(LibrarySort.Name, LibrarySort.RecentlyAdded)),
    Songs(listOf(LibrarySort.Name, LibrarySort.RecentlyAdded, LibrarySort.Artist, LibrarySort.Album)),
}

enum class LibrarySort { Name, RecentlyAdded, Year, Artist, Album }

/** How the Library tabs are switched: a chip row or connected toggle buttons (both offered, ADR 0014). */
enum class FilterControlStyle { Chips, ConnectedButtons }

/** Validated Library preferences. */
data class LibraryPreferences(
    val filterStyle: FilterControlStyle = FilterControlStyle.Chips,
    val albumColumns: Int = DEFAULT_ALBUM_COLUMNS,
    val sorts: Map<LibraryFilter, LibrarySort> = emptyMap(),
) {
    /** The sort of [filter]: the stored one when that tab offers it, else its default. */
    fun sortFor(filter: LibraryFilter): LibrarySort =
        sorts[filter]?.takeIf { it in filter.sorts } ?: filter.sorts.first()

    companion object {
        const val DEFAULT_ALBUM_COLUMNS = 2
        val ALBUM_COLUMNS = 2..4
    }
}
