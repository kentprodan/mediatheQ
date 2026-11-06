package io.github.kentprodan.mediatheq.core.testing

import io.github.kentprodan.mediatheq.core.data.AlbumPagePreferences
import io.github.kentprodan.mediatheq.core.data.ArtistPagePreferences
import io.github.kentprodan.mediatheq.core.data.GenrePagePreferences
import io.github.kentprodan.mediatheq.core.data.HomePreferences
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibraryPagePreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferences
import io.github.kentprodan.mediatheq.core.data.LibraryPreferencesRepository
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.NavigationPreferences
import io.github.kentprodan.mediatheq.core.data.NowPlayingPreferences
import io.github.kentprodan.mediatheq.core.data.PlaylistPagePreferences
import io.github.kentprodan.mediatheq.core.data.RatingMode
import io.github.kentprodan.mediatheq.core.data.SearchPagePreferences
import io.github.kentprodan.mediatheq.core.data.ThemePreferences
import io.github.kentprodan.mediatheq.core.data.validNavEntries
import io.github.kentprodan.mediatheq.core.data.validTransparency
import io.github.kentprodan.mediatheq.core.data.withRecentSearch
import io.github.kentprodan.mediatheq.core.model.HomeSection
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

    override suspend fun setLibraryPage(page: LibraryPagePreferences) {
        preferences.value = preferences.value.copy(libraryPage = page)
    }

    override suspend fun setAlbumPage(page: AlbumPagePreferences) {
        preferences.value = preferences.value.copy(albumPage = page)
    }

    override suspend fun setArtistPage(page: ArtistPagePreferences) {
        preferences.value = preferences.value.copy(artistPage = page)
    }

    override suspend fun restoreArtistPageDefaults() {
        preferences.value = preferences.value.copy(artistPage = ArtistPagePreferences())
    }

    override suspend fun setGenrePage(page: GenrePagePreferences) {
        preferences.value = preferences.value.copy(genrePage = page)
    }

    override suspend fun restoreGenrePageDefaults() {
        preferences.value = preferences.value.copy(genrePage = GenrePagePreferences())
    }

    override suspend fun setPlaylistPage(page: PlaylistPagePreferences) {
        preferences.value = preferences.value.copy(playlistPage = page)
    }

    override suspend fun restorePlaylistPageDefaults() {
        preferences.value = preferences.value.copy(playlistPage = PlaylistPagePreferences())
    }

    override suspend fun setSearchPage(page: SearchPagePreferences) {
        preferences.value = preferences.value.copy(searchPage = page)
    }

    override suspend fun restoreSearchPageDefaults() {
        preferences.value = preferences.value.copy(searchPage = SearchPagePreferences())
    }

    override suspend fun restoreAlbumPageDefaults() {
        preferences.value = preferences.value.copy(albumPage = AlbumPagePreferences())
    }

    override suspend fun restoreLibraryPageDefaults() {
        preferences.value = preferences.value.copy(libraryPage = LibraryPagePreferences())
    }

    override suspend fun setGenresGrid(on: Boolean) {
        preferences.value = preferences.value.copy(genresGrid = on)
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

    override suspend fun setNavigation(navigation: NavigationPreferences) {
        preferences.value =
            preferences.value.copy(
                navigation =
                    navigation.copy(
                        entries = validNavEntries(navigation.entries),
                        transparencyPercent = validTransparency(navigation.transparencyPercent),
                    ),
            )
    }

    override suspend fun restoreNavigationDefaults() {
        preferences.value = preferences.value.copy(navigation = NavigationPreferences())
    }

    override suspend fun setMediaLists(mediaLists: MediaListPreferences) {
        preferences.value = preferences.value.copy(mediaLists = mediaLists)
    }

    override suspend fun restoreMediaListsDefaults() {
        preferences.value = preferences.value.copy(mediaLists = MediaListPreferences())
    }

    override suspend fun setNowPlaying(nowPlaying: NowPlayingPreferences) {
        preferences.value = preferences.value.copy(nowPlaying = nowPlaying)
    }

    override suspend fun setHomeOrder(order: List<HomeSection>) {
        val known = order.distinct()
        val full = known + HomeSection.entries.filterNot { it in known }
        preferences.value = preferences.value.copy(home = preferences.value.home.copy(order = full))
    }

    override suspend fun setHomeHidden(hidden: Set<HomeSection>) {
        preferences.value = preferences.value.copy(home = preferences.value.home.copy(hidden = hidden))
    }

    override suspend fun resetHomeRows() {
        preferences.value = preferences.value.copy(home = HomePreferences())
    }

    override suspend fun restoreHomeDefaults() {
        preferences.value = preferences.value.copy(home = HomePreferences())
    }

    override suspend fun restoreNowPlayingDefaults() {
        preferences.value = preferences.value.copy(nowPlaying = NowPlayingPreferences())
    }

    override suspend fun setOfflineMode(on: Boolean) {
        preferences.value = preferences.value.copy(offlineMode = on)
    }

    override suspend fun setQualityDetail(on: Boolean) {
        preferences.value = preferences.value.copy(qualityDetail = on)
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
