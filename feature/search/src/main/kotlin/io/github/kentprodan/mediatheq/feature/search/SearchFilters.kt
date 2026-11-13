package io.github.kentprodan.mediatheq.feature.search

import io.github.kentprodan.mediatheq.core.common.nameComparator
import io.github.kentprodan.mediatheq.core.data.SearchSort
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.SearchResults

/** The filter chips under the search field (#136); record labels show only under All. */
enum class SearchFilter { All, Artists, Albums, Tracks, Playlists, Genres }

/** Whether [filter]'s chip has anything to show. */
internal fun SearchResults.has(filter: SearchFilter): Boolean =
    when (filter) {
        SearchFilter.All -> !isEmpty()
        SearchFilter.Artists -> artists.isNotEmpty()
        SearchFilter.Albums -> albums.isNotEmpty()
        SearchFilter.Tracks -> tracks.isNotEmpty()
        SearchFilter.Playlists -> playlists.isNotEmpty()
        SearchFilter.Genres -> genres.isNotEmpty()
    }

/**
 * What [filter] shows: All the best [ALL_PER_KIND] of each kind in the search's order (grouped as before #136), any
 * other chip its whole kind ordered by [sort].
 */
internal fun SearchResults.shown(
    filter: SearchFilter,
    sort: SearchSort,
): SearchResults =
    when (filter) {
        SearchFilter.All -> {
            SearchResults(
                artists.take(ALL_PER_KIND),
                albums.take(ALL_PER_KIND),
                tracks.take(ALL_PER_KIND),
                playlists.take(ALL_PER_KIND),
                genres.take(ALL_PER_KIND),
                labels.take(ALL_PER_KIND),
            )
        }

        SearchFilter.Artists -> {
            SearchResults(artists = artists.sortedBy(sort, { it.sortName }, { it.id }))
        }

        SearchFilter.Albums -> {
            SearchResults(albums = albums.sortedBy(sort, { it.sortName }, { it.id }))
        }

        SearchFilter.Tracks -> {
            SearchResults(tracks = tracks.sortedBy(sort, { it.sortName }, { it.id }))
        }

        SearchFilter.Playlists -> {
            SearchResults(playlists = playlists.sortedBy(sort, { it.sortName }, { it.id }))
        }

        SearchFilter.Genres -> {
            SearchResults(genres = genres.sortedBy(sort, { it.sortName }, { it.id }))
        }
    }

/** Relevance keeps the search's order (FTS rank, then sort name); Title compares names, the id breaks ties. */
private fun <T> List<T>.sortedBy(
    sort: SearchSort,
    name: (T) -> String,
    id: (T) -> MediaId,
): List<T> =
    when (sort) {
        SearchSort.Relevance -> this
        SearchSort.Title -> sortedWith(compareBy(nameComparator(), name).thenBy { id(it).itemId })
    }

/** How many of each kind All shows: the search's size before #136. */
internal const val ALL_PER_KIND = 20
