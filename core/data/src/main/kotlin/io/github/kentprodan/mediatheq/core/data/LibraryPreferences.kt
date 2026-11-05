package io.github.kentprodan.mediatheq.core.data

/** The Library lists, each with the sorts it offers (first = default). */
enum class LibraryFilter(
    val sorts: List<LibrarySort>,
) {
    Albums(listOf(LibrarySort.Name, LibrarySort.RecentlyAdded, LibrarySort.Year, LibrarySort.Artist)),
    Artists(listOf(LibrarySort.Name)),
    Playlists(listOf(LibrarySort.Name, LibrarySort.RecentlyAdded)),
    Songs(listOf(LibrarySort.Name, LibrarySort.RecentlyAdded, LibrarySort.Artist, LibrarySort.Album)),

    /** The years of the albums (#129). */
    Years(listOf(LibrarySort.Year, LibrarySort.AlbumCount)),
}

enum class LibrarySort { Name, RecentlyAdded, Year, Artist, Album, AlbumCount }

/** How ratings are shown and given: whole stars, half stars, or not at all (favorites only). */
enum class RatingMode { WholeStars, HalfStars, Off }

/** Validated Library and display preferences. */
data class LibraryPreferences(
    val albumColumns: Int = DEFAULT_ALBUM_COLUMNS,
    val sorts: Map<LibraryFilter, LibrarySort> = emptyMap(),
    val ratingMode: RatingMode = RatingMode.WholeStars,
    /** Only downloads show and play, also with a network (#63); without one, offline mode is on anyway. */
    val offlineMode: Boolean = false,
    val theme: ThemePreferences = ThemePreferences(),
    val navigation: NavigationPreferences = NavigationPreferences(),
    val mediaLists: MediaListPreferences = MediaListPreferences(),
    val nowPlaying: NowPlayingPreferences = NowPlayingPreferences(),
    val home: HomePreferences = HomePreferences(),
    val libraryPage: LibraryPagePreferences = LibraryPagePreferences(),
    /** Now Playing shows the format detail ("FLAC · 24/96 · 1250 kb/s") instead of its kind (#125). */
    val qualityDetail: Boolean = false,
) {
    /** The sort of [filter]: the stored one when that tab offers it, else its default. */
    fun sortFor(filter: LibraryFilter): LibrarySort =
        sorts[filter]?.takeIf { it in filter.sorts } ?: filter.sorts.first()

    companion object {
        const val DEFAULT_ALBUM_COLUMNS = 2
        val ALBUM_COLUMNS = 2..4
    }
}
