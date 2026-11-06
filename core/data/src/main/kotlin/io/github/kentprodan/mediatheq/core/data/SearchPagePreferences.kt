package io.github.kentprodan.mediatheq.core.data

/** How a filter chip's full list is ordered in Search (#136); All always keeps the best matches first. */
enum class SearchSort {
    /** The search's own order: best match first. */
    Relevance,
    Title,
}

/** Settings -> Interface -> Page settings -> Search page (#136). */
data class SearchPagePreferences(
    /** The filter chips scroll in one row; off, they wrap. */
    val singleLineChips: Boolean = true,
    /** A heart toggle on the track rows of the results. */
    val showFavorite: Boolean = true,
    /** A tap on a track result; `null` = the Default track action of Navigation and media start (#122). */
    val trackAction: TrackAction? = null,
    val sort: SearchSort = SearchSort.Title,
)
