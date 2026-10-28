package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
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
