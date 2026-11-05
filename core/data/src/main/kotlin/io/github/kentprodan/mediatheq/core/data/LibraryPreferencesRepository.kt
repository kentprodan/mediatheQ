package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.HomeSection
import kotlinx.coroutines.flow.Flow

/** Library and display preferences (grid, sorts, colors, ratings), kept across restarts. */
interface LibraryPreferencesRepository {
    val preferences: Flow<LibraryPreferences>

    /** The Genres list as a grid (#130), or rows. */
    suspend fun setGenresGrid(on: Boolean)

    /** Ignored outside [LibraryPreferences.ALBUM_COLUMNS]. */
    suspend fun setAlbumColumns(columns: Int)

    suspend fun setTheme(theme: ThemePreferences)

    /** Settings -> Theme, font and colors -> Restore defaults: only the theme goes back to [ThemePreferences]. */
    suspend fun restoreThemeDefaults()

    /** Stored validated: see [validNavEntries] and [validTransparency]. */
    suspend fun setNavigation(navigation: NavigationPreferences)

    /** Settings -> Navigation and media start -> Restore defaults: only that page goes back to its defaults. */
    suspend fun restoreNavigationDefaults()

    suspend fun setMediaLists(mediaLists: MediaListPreferences)

    /** Settings -> Media lists -> Restore defaults: only that page goes back to its defaults. */
    suspend fun restoreMediaListsDefaults()

    suspend fun setNowPlaying(nowPlaying: NowPlayingPreferences)

    /** Settings -> Now playing screens -> Restore defaults: only that page goes back to its defaults (#126). */
    suspend fun restoreNowPlayingDefaults()

    /** Home's sections in this order; sections missing from [order] keep their default place after it. */
    suspend fun setHomeOrder(order: List<HomeSection>)

    suspend fun setHomeHidden(hidden: Set<HomeSection>)

    /** Home page -> Reset active rows: order and visibility back to default (section settings, #182, kept). */
    suspend fun resetHomeRows()

    /** Home page -> Restore defaults: everything on the page. */
    suspend fun restoreHomeDefaults()

    /** Stored validated: tiles distinct and never none, columns in their ranges (else the defaults). */
    suspend fun setLibraryPage(page: LibraryPagePreferences)

    /** Settings -> Library page -> Restore defaults: only that page goes back to its defaults (#129). */
    suspend fun restoreLibraryPageDefaults()

    /** Stored validated: the section order holds every section once (#132). */
    suspend fun setAlbumPage(page: AlbumPagePreferences)

    /** Settings -> Album page -> Restore defaults: only that page goes back to its defaults (#132). */
    suspend fun restoreAlbumPageDefaults()

    suspend fun setRatingMode(mode: RatingMode)

    /** Now Playing's quality label: the format detail or its kind (#125). */
    suspend fun setQualityDetail(on: Boolean)

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
