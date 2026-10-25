package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.datastore.LibraryPreferencesStore
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
                    dynamicColor = stored.dynamicColor ?: false,
                    ratingMode = enumOrNull<RatingMode>(stored.ratingMode) ?: RatingMode.WholeStars,
                )
            }

        override suspend fun setFilterStyle(style: FilterControlStyle) = store.setFilterStyle(style.name)

        override suspend fun setAlbumColumns(columns: Int) {
            if (columns in LibraryPreferences.ALBUM_COLUMNS) store.setAlbumColumns(columns)
        }

        override suspend fun setDynamicColor(on: Boolean) = store.setDynamicColor(on)

        override suspend fun setRatingMode(mode: RatingMode) = store.setRatingMode(mode.name)

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
    }
