package io.github.kentprodan.mediatheq.feature.library

import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.ListSelection
import java.text.Normalizer

/**
 * What the list header narrows a Library list to (#123): favorites only, downloads only, and the text typed in
 * "search in list" (`null` while the field is closed). Kept on screen only, not saved.
 */
data class LibraryListFilter(
    val favoritesOnly: Boolean = false,
    val downloadedOnly: Boolean = false,
    val search: String? = null,
) {
    val isActive: Boolean get() = favoritesOnly || downloadedOnly || !search.isNullOrBlank()
}

/**
 * [this] content narrowed by [filter]. [downloaded] holds downloaded tracks and fully downloaded albums and playlists;
 * artists have no download state, so "downloads only" keeps them all. Names match the search without case or
 * diacritics ("mosoi" finds "Moșoi"), anywhere in the name.
 */
internal fun LibraryContent.filtered(
    filter: LibraryListFilter,
    downloaded: Set<MediaId>,
): LibraryContent {
    if (!filter.isActive) return this
    val words =
        filter.search
            ?.let(::fold)
            ?.split(' ')
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    fun keep(
        id: MediaId,
        favorite: Boolean,
        names: List<String>,
        hasDownloadState: Boolean = true,
    ): Boolean =
        (!filter.favoritesOnly || favorite) &&
            (!filter.downloadedOnly || !hasDownloadState || id in downloaded) &&
            words.all { word -> names.any { fold(it).contains(word) } }

    return when (this) {
        is LibraryContent.Albums -> {
            LibraryContent.Albums(
                albums.filter {
                    keep(
                        it.id,
                        it.userData.isFavorite,
                        listOf(it.title) + it.albumArtists.map { a -> a.name },
                    )
                },
            )
        }

        is LibraryContent.Artists -> {
            LibraryContent.Artists(
                artists.filter { keep(it.id, it.userData.isFavorite, listOf(it.name), hasDownloadState = false) },
            )
        }

        is LibraryContent.Playlists -> {
            LibraryContent.Playlists(playlists.filter { keep(it.id, it.userData.isFavorite, listOf(it.name)) })
        }

        is LibraryContent.Songs -> {
            LibraryContent.Songs(
                tracks.filter {
                    keep(it.id, it.userData.isFavorite, listOf(it.title) + it.artists.map { a -> a.name })
                },
            )
        }

        else -> {
            this
        }
    }
}

/** The ids of [this] content's items, in list order. */
internal val LibraryContent.ids: List<MediaId>
    get() =
        when (this) {
            is LibraryContent.Albums -> albums.map { it.id }
            is LibraryContent.Artists -> artists.map { it.id }
            is LibraryContent.Playlists -> playlists.map { it.id }
            is LibraryContent.Songs -> tracks.map { it.id }
            else -> emptyList()
        }

/** [this] content with only the [selection]'s items, in list order. */
internal fun LibraryContent.picked(selection: ListSelection<MediaId>): LibraryContent =
    when (this) {
        is LibraryContent.Albums -> LibraryContent.Albums(selection.pick(albums) { it.id })
        is LibraryContent.Artists -> LibraryContent.Artists(selection.pick(artists) { it.id })
        is LibraryContent.Playlists -> LibraryContent.Playlists(selection.pick(playlists) { it.id })
        is LibraryContent.Songs -> LibraryContent.Songs(selection.pick(tracks) { it.id })
        else -> this
    }

/** How many items [this] content lists; 0 for the states without items. */
internal val LibraryContent.itemCount: Int
    get() =
        when (this) {
            is LibraryContent.Albums -> albums.size
            is LibraryContent.Artists -> artists.size
            is LibraryContent.Playlists -> playlists.size
            is LibraryContent.Songs -> tracks.size
            is LibraryContent.Years -> years.size
            is LibraryContent.Genres -> genres.size
            else -> 0
        }

private val MARKS = Regex("\\p{Mn}+")

private fun fold(text: String): String = MARKS.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")
