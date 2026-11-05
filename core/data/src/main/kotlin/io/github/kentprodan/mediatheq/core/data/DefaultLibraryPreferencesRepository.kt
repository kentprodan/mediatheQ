package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import io.github.kentprodan.mediatheq.core.datastore.StoredAlbumPage
import io.github.kentprodan.mediatheq.core.datastore.StoredArtistPage
import io.github.kentprodan.mediatheq.core.datastore.StoredGenrePage
import io.github.kentprodan.mediatheq.core.datastore.StoredHome
import io.github.kentprodan.mediatheq.core.datastore.StoredLibraryPage
import io.github.kentprodan.mediatheq.core.datastore.StoredMediaLists
import io.github.kentprodan.mediatheq.core.datastore.StoredNavigation
import io.github.kentprodan.mediatheq.core.datastore.StoredNowPlaying
import io.github.kentprodan.mediatheq.core.datastore.StoredTheme
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.LibraryShortcut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultLibraryPreferencesRepository
    @Inject
    constructor(
        private val store: LibraryPreferencesStore,
    ) : LibraryPreferencesRepository {
        override val preferences: Flow<LibraryPreferences> =
            store.values.map { stored ->
                LibraryPreferences(
                    albumColumns =
                        stored.albumColumns?.takeIf { it in LibraryPreferences.ALBUM_COLUMNS }
                            ?: LibraryPreferences.DEFAULT_ALBUM_COLUMNS,
                    genresGrid = stored.genresGrid ?: false,
                    sorts =
                        stored.sorts
                            .mapNotNull { (tab, sort) ->
                                val filter = enumOrNull<LibraryFilter>(tab) ?: return@mapNotNull null
                                enumOrNull<LibrarySort>(sort)?.takeIf { it in filter.sorts }?.let { filter to it }
                            }.toMap(),
                    ratingMode = enumOrNull<RatingMode>(stored.ratingMode) ?: RatingMode.WholeStars,
                    offlineMode = stored.offlineMode ?: false,
                    theme = theme(stored.theme, stored.dynamicColor),
                    navigation = navigation(stored.navigation),
                    mediaLists = mediaLists(stored.mediaLists),
                    nowPlaying = nowPlaying(stored.nowPlaying),
                    home = home(stored.home),
                    libraryPage = libraryPage(stored.libraryPage),
                    albumPage = albumPage(stored.albumPage),
                    artistPage = artistPage(stored.artistPage),
                    genrePage = genrePage(stored.genrePage),
                    qualityDetail = stored.qualityDetail ?: false,
                )
            }

        override suspend fun setAlbumColumns(columns: Int) {
            if (columns in LibraryPreferences.ALBUM_COLUMNS) store.setAlbumColumns(columns)
        }

        override suspend fun setGenresGrid(on: Boolean) = store.setGenresGrid(on)

        override suspend fun setTheme(theme: ThemePreferences) =
            store.setTheme(
                StoredTheme(
                    mode = theme.mode.name,
                    colorSource = theme.colorSource.name,
                    customSeed = theme.customSeed,
                    nowPlayingColors = theme.nowPlayingColors.name,
                    ignoreSurfaces = theme.ignoreSurfaces,
                    instantColors = theme.instantColors,
                    font = theme.font.name,
                ),
            )

        override suspend fun restoreThemeDefaults() = store.setTheme(StoredTheme())

        private fun theme(
            stored: StoredTheme,
            dynamicColor: Boolean?,
        ): ThemePreferences {
            val defaults = ThemePreferences()
            return ThemePreferences(
                mode = enumOrNull<ThemeMode>(stored.mode) ?: defaults.mode,
                // Before #121 a switch chose Material You; it carries over until a source is picked.
                colorSource =
                    enumOrNull<ColorSource>(stored.colorSource)
                        ?: if (dynamicColor == true) ColorSource.MaterialYou else defaults.colorSource,
                customSeed = stored.customSeed?.let { it or OPAQUE } ?: defaults.customSeed,
                nowPlayingColors = enumOrNull<NowPlayingColors>(stored.nowPlayingColors) ?: defaults.nowPlayingColors,
                ignoreSurfaces = stored.ignoreSurfaces ?: defaults.ignoreSurfaces,
                instantColors = stored.instantColors ?: defaults.instantColors,
                font = enumOrNull<AppFont>(stored.font) ?: defaults.font,
            )
        }

        override suspend fun setNavigation(navigation: NavigationPreferences) =
            store.setNavigation(
                StoredNavigation(
                    entries = validNavEntries(navigation.entries).map { it.name },
                    portraitStyle = navigation.portraitStyle.name,
                    landscapeStyle = navigation.landscapeStyle.name,
                    transparencyPercent = validTransparency(navigation.transparencyPercent),
                    slowStartMessages = navigation.slowStartMessages,
                    trackAction = navigation.trackAction.name,
                    expandPlayer = navigation.expandPlayer,
                ),
            )

        override suspend fun restoreNavigationDefaults() = store.setNavigation(StoredNavigation())

        private fun navigation(stored: StoredNavigation): NavigationPreferences {
            val defaults = NavigationPreferences()
            return NavigationPreferences(
                // Unknown names (an entry removed in a later version) are dropped.
                entries =
                    stored.entries?.let { names -> validNavEntries(names.mapNotNull { enumOrNull<MainNavEntry>(it) }) }
                        ?: defaults.entries,
                portraitStyle = enumOrNull<PortraitNavStyle>(stored.portraitStyle) ?: defaults.portraitStyle,
                landscapeStyle = enumOrNull<LandscapeNavStyle>(stored.landscapeStyle) ?: defaults.landscapeStyle,
                transparencyPercent =
                    stored.transparencyPercent?.let(::validTransparency) ?: defaults.transparencyPercent,
                slowStartMessages = stored.slowStartMessages ?: defaults.slowStartMessages,
                trackAction = enumOrNull<TrackAction>(stored.trackAction) ?: defaults.trackAction,
                expandPlayer = stored.expandPlayer ?: defaults.expandPlayer,
            )
        }

        override suspend fun setMediaLists(mediaLists: MediaListPreferences) =
            store.setMediaLists(
                StoredMediaLists(
                    showItemCounts = mediaLists.showItemCounts,
                    topButtonStyle = mediaLists.topButtonStyle.name,
                    hiddenTopButtons = mediaLists.hiddenTopButtons.map { it.name },
                    invertedSortArrows = mediaLists.invertedSortArrows,
                    hideBackButton = mediaLists.hideBackButton,
                    trackInfo = mediaLists.trackInfo.name,
                    secondTrackInfo = mediaLists.secondTrackInfo.name,
                    centerGridText = mediaLists.centerGridText,
                    favoriteOverlay = mediaLists.favoriteOverlay,
                    offlineOverlay = mediaLists.offlineOverlay,
                    swipeRight = mediaLists.swipeRight.name,
                    swipeLeft = mediaLists.swipeLeft.name,
                ),
            )

        override suspend fun restoreMediaListsDefaults() = store.setMediaLists(StoredMediaLists())

        override suspend fun setNowPlaying(nowPlaying: NowPlayingPreferences) =
            store.setNowPlaying(
                StoredNowPlaying(
                    floatingMiniPlayer = nowPlaying.floatingMiniPlayer,
                    miniSwipeToSkip = nowPlaying.miniSwipeToSkip,
                    miniProgress = nowPlaying.miniProgress.name,
                    background = nowPlaying.background.name,
                    progress = nowPlaying.progress.name,
                    fallback = nowPlaying.fallback.name,
                    showRating = nowPlaying.showRating,
                    coverTap = nowPlaying.coverTap.name,
                    coverSwipe = nowPlaying.coverSwipe,
                    playButtonWhenPaused = nowPlaying.playButtonWhenPaused,
                ),
            )

        override suspend fun restoreNowPlayingDefaults() = store.setNowPlaying(StoredNowPlaying())

        override suspend fun setHomeOrder(order: List<HomeSection>) = store.setHomeOrder(order.map { it.name })

        override suspend fun setHomeHidden(hidden: Set<HomeSection>) = store.setHomeHidden(hidden.map { it.name })

        override suspend fun resetHomeRows() = store.setHome(StoredHome())

        // Until the section settings are stored (#182), the page holds only the rows.
        override suspend fun restoreHomeDefaults() = store.setHome(StoredHome())

        /** Unknown names are dropped; sections the stored order lacks (new ones) follow it in their default order. */
        private fun home(stored: StoredHome): HomePreferences {
            val defaults = HomePreferences()
            val order =
                stored.order
                    ?.mapNotNull { enumOrNull<HomeSection>(it) }
                    ?.distinct()
                    ?.let { known -> known + HomeSection.entries.filterNot { it in known } }
                    ?: defaults.order
            val hidden = stored.hidden?.mapNotNull { enumOrNull<HomeSection>(it) }?.toSet() ?: defaults.hidden
            return HomePreferences(order, hidden)
        }

        override suspend fun setLibraryPage(page: LibraryPagePreferences) {
            val defaults = LibraryPagePreferences()
            store.setLibraryPage(
                StoredLibraryPage(
                    shortcuts =
                        page.shortcuts
                            .distinct()
                            .ifEmpty { defaults.shortcuts }
                            .map { it.name },
                    style = page.style.name,
                    columns = page.columns.takeIf { it in LibraryPagePreferences.COLUMNS } ?: defaults.columns,
                    landscapeColumns =
                        page.landscapeColumns.takeIf { it in LibraryPagePreferences.LANDSCAPE_COLUMNS }
                            ?: defaults.landscapeColumns,
                    spacing = page.spacing.name,
                ),
            )
        }

        override suspend fun restoreLibraryPageDefaults() = store.setLibraryPage(StoredLibraryPage())

        override suspend fun setAlbumPage(page: AlbumPagePreferences) {
            store.setAlbumPage(
                StoredAlbumPage(
                    headerBackground = page.headerBackground.name,
                    showQuality = page.showQuality,
                    showRatingBar = page.showRatingBar,
                    hiddenButtons = page.hiddenButtons.map { it.name },
                    sectionOrder = completeOrder(page.sectionOrder).map { it.name },
                    showMoreFromArtist = page.showMoreFromArtist,
                    moreAlbumsSort = page.moreAlbumsSort.name,
                    alwaysShowTrackArtists = page.alwaysShowTrackArtists,
                    showTrackNumbers = page.showTrackNumbers,
                    showTrackThumbnails = page.showTrackThumbnails,
                    multilineTrackTitles = page.multilineTrackTitles,
                    showAbout = page.showAbout,
                ),
            )
        }

        override suspend fun restoreAlbumPageDefaults() = store.setAlbumPage(StoredAlbumPage())

        override suspend fun setArtistPage(page: ArtistPagePreferences) {
            store.setArtistPage(
                StoredArtistPage(
                    headerBackground = page.headerBackground.name,
                    hiddenButtons = page.hiddenButtons.map { it.name },
                    playAction = page.playAction.name,
                    sectionOrder = completeArtistOrder(page.sectionOrder).map { it.name },
                    albumsSort = page.albumsSort.name,
                    albumColumns = page.albumColumns.coerceIn(ArtistPagePreferences.COLUMNS),
                    landscapeAlbumColumns =
                        page.landscapeAlbumColumns.coerceIn(
                            ArtistPagePreferences.LANDSCAPE_COLUMNS,
                        ),
                    showAppearsOn = page.showAppearsOn,
                    showTopTracks = page.showTopTracks,
                    topTracksCount = page.topTracksCount.coerceIn(ArtistPagePreferences.TOP_TRACKS),
                    showAbout = page.showAbout,
                    dynamicColors = page.dynamicColors,
                ),
            )
        }

        override suspend fun restoreArtistPageDefaults() = store.setArtistPage(StoredArtistPage())

        override suspend fun setGenrePage(page: GenrePagePreferences) {
            store.setGenrePage(
                StoredGenrePage(
                    headerBackground = page.headerBackground.name,
                    hiddenButtons = page.hiddenButtons.map { it.name },
                    sectionOrder = completeGenreOrder(page.sectionOrder).map { it.name },
                    showAlbums = page.showAlbums,
                    showArtists = page.showArtists,
                    showAlbumArtists = page.showAlbumArtists,
                    showTopTracks = page.showTopTracks,
                    topTracksCount = page.topTracksCount.coerceIn(GenrePagePreferences.TOP_TRACKS),
                    dynamicColors = page.dynamicColors,
                ),
            )
        }

        override suspend fun restoreGenrePageDefaults() = store.setGenrePage(StoredGenrePage())

        /** Unknown names are dropped, the order is completed and a count out of range is clamped. */
        private fun genrePage(stored: StoredGenrePage): GenrePagePreferences {
            val defaults = GenrePagePreferences()
            return GenrePagePreferences(
                headerBackground =
                    enumOrNull<GenreHeaderBackground>(stored.headerBackground) ?: defaults.headerBackground,
                hiddenButtons =
                    stored.hiddenButtons?.mapNotNull { enumOrNull<GenreButton>(it) }?.toSet() ?: defaults.hiddenButtons,
                sectionOrder =
                    stored.sectionOrder?.let { names ->
                        completeGenreOrder(names.mapNotNull { enumOrNull<GenreSection>(it) })
                    }
                        ?: defaults.sectionOrder,
                showAlbums = stored.showAlbums ?: defaults.showAlbums,
                showArtists = stored.showArtists ?: defaults.showArtists,
                showAlbumArtists = stored.showAlbumArtists ?: defaults.showAlbumArtists,
                showTopTracks = stored.showTopTracks ?: defaults.showTopTracks,
                topTracksCount =
                    stored.topTracksCount?.coerceIn(GenrePagePreferences.TOP_TRACKS) ?: defaults.topTracksCount,
                dynamicColors = stored.dynamicColors ?: defaults.dynamicColors,
            )
        }

        private fun completeGenreOrder(order: List<GenreSection>): List<GenreSection> =
            order.distinct().let { known -> known + GenreSection.entries.filterNot { it in known } }

        /** Unknown names are dropped, the order is completed and counts out of range are clamped. */
        private fun artistPage(stored: StoredArtistPage): ArtistPagePreferences {
            val defaults = ArtistPagePreferences()
            return ArtistPagePreferences(
                headerBackground =
                    enumOrNull<ArtistHeaderBackground>(stored.headerBackground) ?: defaults.headerBackground,
                hiddenButtons =
                    stored.hiddenButtons
                        ?.mapNotNull {
                            enumOrNull<ArtistButton>(
                                it,
                            )
                        }?.toSet() ?: defaults.hiddenButtons,
                playAction = enumOrNull<ArtistPlayAction>(stored.playAction) ?: defaults.playAction,
                sectionOrder =
                    stored.sectionOrder?.let { names ->
                        completeArtistOrder(names.mapNotNull { enumOrNull<ArtistSection>(it) })
                    }
                        ?: defaults.sectionOrder,
                albumsSort = enumOrNull<MoreAlbumsSort>(stored.albumsSort) ?: defaults.albumsSort,
                albumColumns = stored.albumColumns?.coerceIn(ArtistPagePreferences.COLUMNS) ?: defaults.albumColumns,
                landscapeAlbumColumns =
                    stored.landscapeAlbumColumns?.coerceIn(ArtistPagePreferences.LANDSCAPE_COLUMNS)
                        ?: defaults.landscapeAlbumColumns,
                showAppearsOn = stored.showAppearsOn ?: defaults.showAppearsOn,
                showTopTracks = stored.showTopTracks ?: defaults.showTopTracks,
                topTracksCount =
                    stored.topTracksCount?.coerceIn(ArtistPagePreferences.TOP_TRACKS) ?: defaults.topTracksCount,
                showAbout = stored.showAbout ?: defaults.showAbout,
                dynamicColors = stored.dynamicColors ?: defaults.dynamicColors,
            )
        }

        private fun completeArtistOrder(order: List<ArtistSection>): List<ArtistSection> =
            order.distinct().let { known -> known + ArtistPagePreferences.DEFAULT_SECTIONS.filterNot { it in known } }

        /** Unknown names are dropped; the order is completed so every section shows once. */
        private fun albumPage(stored: StoredAlbumPage): AlbumPagePreferences {
            val defaults = AlbumPagePreferences()
            return AlbumPagePreferences(
                headerBackground =
                    enumOrNull<AlbumHeaderBackground>(stored.headerBackground) ?: defaults.headerBackground,
                showQuality = stored.showQuality ?: defaults.showQuality,
                showRatingBar = stored.showRatingBar ?: defaults.showRatingBar,
                hiddenButtons =
                    stored.hiddenButtons?.mapNotNull { enumOrNull<AlbumButton>(it) }?.toSet() ?: defaults.hiddenButtons,
                sectionOrder =
                    stored.sectionOrder?.let { names ->
                        completeOrder(names.mapNotNull { enumOrNull<AlbumSection>(it) })
                    }
                        ?: defaults.sectionOrder,
                showMoreFromArtist = stored.showMoreFromArtist ?: defaults.showMoreFromArtist,
                moreAlbumsSort = enumOrNull<MoreAlbumsSort>(stored.moreAlbumsSort) ?: defaults.moreAlbumsSort,
                alwaysShowTrackArtists = stored.alwaysShowTrackArtists ?: defaults.alwaysShowTrackArtists,
                showTrackNumbers = stored.showTrackNumbers ?: defaults.showTrackNumbers,
                showTrackThumbnails = stored.showTrackThumbnails ?: defaults.showTrackThumbnails,
                multilineTrackTitles = stored.multilineTrackTitles ?: defaults.multilineTrackTitles,
                showAbout = stored.showAbout ?: defaults.showAbout,
            )
        }

        private fun completeOrder(order: List<AlbumSection>): List<AlbumSection> =
            order.distinct().let { known -> known + AlbumSection.entries.filterNot { it in known } }

        /** Unknown or repeated tile names are dropped; none left (or none stored) means every tile. */
        private fun libraryPage(stored: StoredLibraryPage): LibraryPagePreferences {
            val defaults = LibraryPagePreferences()
            return LibraryPagePreferences(
                shortcuts =
                    stored.shortcuts
                        ?.mapNotNull { enumOrNull<LibraryShortcut>(it) }
                        ?.distinct()
                        ?.ifEmpty { null }
                        ?: defaults.shortcuts,
                style = enumOrNull<LibraryHubStyle>(stored.style) ?: defaults.style,
                columns = stored.columns?.takeIf { it in LibraryPagePreferences.COLUMNS } ?: defaults.columns,
                landscapeColumns =
                    stored.landscapeColumns?.takeIf { it in LibraryPagePreferences.LANDSCAPE_COLUMNS }
                        ?: defaults.landscapeColumns,
                spacing = enumOrNull<LibraryHubSpacing>(stored.spacing) ?: defaults.spacing,
            )
        }

        private fun nowPlaying(stored: StoredNowPlaying): NowPlayingPreferences {
            val defaults = NowPlayingPreferences()
            return NowPlayingPreferences(
                floatingMiniPlayer = stored.floatingMiniPlayer ?: defaults.floatingMiniPlayer,
                miniSwipeToSkip = stored.miniSwipeToSkip ?: defaults.miniSwipeToSkip,
                miniProgress = enumOrNull<MiniProgress>(stored.miniProgress) ?: defaults.miniProgress,
                background = enumOrNull<PlayerBackground>(stored.background) ?: defaults.background,
                progress = enumOrNull<PlayerProgress>(stored.progress) ?: defaults.progress,
                fallback = enumOrNull<ProgressFallback>(stored.fallback) ?: defaults.fallback,
                showRating = stored.showRating ?: defaults.showRating,
                coverTap = enumOrNull<CoverTap>(stored.coverTap) ?: defaults.coverTap,
                coverSwipe = stored.coverSwipe ?: defaults.coverSwipe,
                playButtonWhenPaused = stored.playButtonWhenPaused ?: defaults.playButtonWhenPaused,
            )
        }

        private fun mediaLists(stored: StoredMediaLists): MediaListPreferences {
            val defaults = MediaListPreferences()
            return MediaListPreferences(
                showItemCounts = stored.showItemCounts ?: defaults.showItemCounts,
                topButtonStyle = enumOrNull<TopButtonStyle>(stored.topButtonStyle) ?: defaults.topButtonStyle,
                hiddenTopButtons =
                    stored.hiddenTopButtons?.mapNotNull { enumOrNull<TopButton>(it) }?.toSet()
                        ?: defaults.hiddenTopButtons,
                invertedSortArrows = stored.invertedSortArrows ?: defaults.invertedSortArrows,
                hideBackButton = stored.hideBackButton ?: defaults.hideBackButton,
                trackInfo = enumOrNull<TrackInfo>(stored.trackInfo) ?: defaults.trackInfo,
                secondTrackInfo = enumOrNull<TrackInfo>(stored.secondTrackInfo) ?: defaults.secondTrackInfo,
                centerGridText = stored.centerGridText ?: defaults.centerGridText,
                favoriteOverlay = stored.favoriteOverlay ?: defaults.favoriteOverlay,
                offlineOverlay = stored.offlineOverlay ?: defaults.offlineOverlay,
                swipeRight = enumOrNull<SwipeAction>(stored.swipeRight) ?: defaults.swipeRight,
                swipeLeft = enumOrNull<SwipeAction>(stored.swipeLeft) ?: defaults.swipeLeft,
            )
        }

        override suspend fun setRatingMode(mode: RatingMode) = store.setRatingMode(mode.name)

        override suspend fun setQualityDetail(on: Boolean) = store.setQualityDetail(on)

        override suspend fun setOfflineMode(on: Boolean) = store.setOfflineMode(on)

        override suspend fun setSort(
            filter: LibraryFilter,
            sort: LibrarySort,
        ) {
            if (sort in filter.sorts) store.setSort(filter.name, sort.name)
        }

        override val recentSearches: Flow<List<String>> =
            store.recentSearches.map { it.take(MAX_RECENT_SEARCHES) }

        override suspend fun addRecentSearch(query: String) = store.updateRecentSearches { it.withRecentSearch(query) }

        override suspend fun clearRecentSearches() = store.updateRecentSearches { emptyList() }

        private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
            enumValues<E>().firstOrNull {
                it.name ==
                    name
            }

        private companion object {
            const val OPAQUE = 0xFF000000.toInt()
        }
    }
