package io.github.kentprodan.mediatheq.feature.tv.library

import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.model.Artwork
import io.github.kentprodan.mediatheq.core.model.MediaId

/** One list of the TV Library (#237): a tab, or a genre's or year's albums. */
data class TvLibraryUiState(
    val filter: LibraryFilter,
    val sort: LibrarySort = filter.sorts.first(),
    val content: TvLibraryContent = TvLibraryContent.Loading,
    /** The sort runs against its natural direction (#349). */
    val reversed: Boolean = false,
)

sealed interface TvLibraryContent {
    /** Before the first sync is done. */
    data object Loading : TvLibraryContent

    /** Synced, but nothing for this list. */
    data object Empty : TvLibraryContent

    /** Nothing on the TV yet and the first sync failed. */
    data class Error(
        val error: MqError,
    ) : TvLibraryContent

    /** The cards, in the list's sort; [letters] is the A-Z strip when sorted by name. */
    data class Items(
        val items: List<TvLibraryItem>,
        val letters: List<Letter> = emptyList(),
    ) : TvLibraryContent {
        /** Turned around (#349): Z to A, each letter jumping to its first card in the new order. */
        fun reversed(): Items {
            val ends = letters.drop(1).map { it.index } + items.size
            return Items(
                items.reversed(),
                letters.zip(ends) { letter, end -> Letter(letter.letter, items.size - end) }.reversed(),
            )
        }
    }
}

/** A card of the grid: what it is ([kind]), its name, a second line and its picture. */
data class TvLibraryItem(
    val id: MediaId,
    val kind: Kind,
    val title: String,
    val subtitle: String? = null,
    val artwork: Artwork? = null,
    /** Albums of a genre, for its second line. */
    val albumCount: Int? = null,
) {
    enum class Kind { Album, Artist, Playlist, Genre }
}

/** A letter of the A-Z strip and the index of the first item that starts with it. */
data class Letter(
    val letter: Char,
    val index: Int,
)

sealed interface TvLibraryAction {
    data class SetSort(
        val sort: LibrarySort,
    ) : TvLibraryAction

    /** Retry from the error and empty states. */
    data object Sync : TvLibraryAction

    /** Sort's Reverse order (#349). */
    data object ReverseSort : TvLibraryAction
}
