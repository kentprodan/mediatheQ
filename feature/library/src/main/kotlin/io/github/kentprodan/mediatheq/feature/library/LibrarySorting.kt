package io.github.kentprodan.mediatheq.feature.library

import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.Track
import java.text.Collator
import java.util.Locale

// Sorting for the Library tabs. Names compare like a person reads them: case and diacritics are ignored ("Moșoi" next
// to "Mosoi"). The item id breaks ties so the order never jumps. Newest and latest year come first; unknown dates last.

private fun nameCollator(): Comparator<String> {
    val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
    return Comparator { a, b -> collator.compare(a, b) }
}

@JvmName("sortedAlbums")
internal fun List<Album>.sortedFor(sort: LibrarySort): List<Album> {
    val names = nameCollator()
    val byName = compareBy(names) { album: Album -> album.sortName }.thenBy { it.id.itemId }
    return sortedWith(
        when (sort) {
            LibrarySort.RecentlyAdded -> {
                compareByDescending<Album> { it.dateAdded }.then(byName)
            }

            LibrarySort.Year -> {
                compareByDescending<Album> { it.year }.then(byName)
            }

            LibrarySort.Artist -> {
                compareBy(names) { album: Album ->
                    album.albumArtists
                        .firstOrNull()
                        ?.sortName
                        .orEmpty()
                }.thenBy { it.year }
                    .then(byName)
            }

            else -> {
                byName
            }
        },
    )
}

/** Artists offer only one sort. */
internal fun List<Artist>.sortedByName(): List<Artist> =
    sortedWith(
        compareBy(nameCollator()) { artist: Artist -> artist.sortName }.thenBy {
            it.id.itemId
        },
    )

@JvmName("sortedPlaylists")
internal fun List<Playlist>.sortedFor(sort: LibrarySort): List<Playlist> {
    val byName = compareBy(nameCollator()) { playlist: Playlist -> playlist.sortName }.thenBy { it.id.itemId }
    return sortedWith(
        if (sort == LibrarySort.RecentlyAdded) compareByDescending<Playlist> { it.dateAdded }.then(byName) else byName,
    )
}

@JvmName("sortedTracks")
internal fun List<Track>.sortedFor(sort: LibrarySort): List<Track> {
    val names = nameCollator()
    val byName = compareBy(names) { track: Track -> track.sortName }.thenBy { it.id.itemId }
    val inAlbumOrder = compareBy<Track>({ it.discNumber }, { it.trackNumber }).then(byName)
    return sortedWith(
        when (sort) {
            LibrarySort.RecentlyAdded -> {
                compareByDescending<Track> { it.dateAdded }.then(byName)
            }

            LibrarySort.Artist -> {
                compareBy(names) { track: Track ->
                    track.artists
                        .firstOrNull()
                        ?.sortName
                        .orEmpty()
                }.thenBy(names) { it.albumTitle.orEmpty() }
                    .then(inAlbumOrder)
            }

            LibrarySort.Album -> {
                compareBy(names) { track: Track -> track.albumTitle.orEmpty() }.then(inAlbumOrder)
            }

            else -> {
                byName
            }
        },
    )
}
