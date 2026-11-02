package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
import io.github.kentprodan.mediatheq.core.datastore.StoredMediaLists
import io.github.kentprodan.mediatheq.core.datastore.StoredNavigation
import io.github.kentprodan.mediatheq.core.datastore.StoredTheme
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
                    filterStyle = enumOrNull<FilterControlStyle>(stored.filterStyle) ?: FilterControlStyle.Chips,
                    albumColumns =
                        stored.albumColumns?.takeIf { it in LibraryPreferences.ALBUM_COLUMNS }
                            ?: LibraryPreferences.DEFAULT_ALBUM_COLUMNS,
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
                )
            }

        override suspend fun setFilterStyle(style: FilterControlStyle) = store.setFilterStyle(style.name)

        override suspend fun setAlbumColumns(columns: Int) {
            if (columns in LibraryPreferences.ALBUM_COLUMNS) store.setAlbumColumns(columns)
        }

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
