package io.github.kentprodan.mediatheq.feature.library

import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.YearAlbums
import io.github.kentprodan.mediatheq.core.model.Album
import io.github.kentprodan.mediatheq.core.model.Artist
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.Playlist
import io.github.kentprodan.mediatheq.core.model.RecordLabel
import io.github.kentprodan.mediatheq.core.model.Track
import io.github.kentprodan.mediatheq.core.ui.nameComparator

// Sorting for the Library lists. Names compare like a person reads them: case and diacritics are ignored ("Moșoi" next
// to "Mosoi"). The item id breaks ties so the order never jumps. Newest and latest year come first; unknown dates last.

@JvmName("sortedAlbums")
internal fun List<Album>.sortedFor(sort: LibrarySort): List<Album> {
    val names = nameComparator()
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
        compareBy(nameComparator()) { artist: Artist -> artist.sortName }.thenBy {
            it.id.itemId
        },
    )

@JvmName("sortedPlaylists")
internal fun List<Playlist>.sortedFor(sort: LibrarySort): List<Playlist> {
    val byName = compareBy(nameComparator()) { playlist: Playlist -> playlist.sortName }.thenBy { it.id.itemId }
    return sortedWith(
        if (sort == LibrarySort.RecentlyAdded) compareByDescending<Playlist> { it.dateAdded }.then(byName) else byName,
    )
}

@JvmName("sortedTracks")
internal fun List<Track>.sortedFor(sort: LibrarySort): List<Track> {
    val names = nameComparator()
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

/** Years come newest first (from `albumYears`); by album count the year with most albums first, ties newest first. */
@JvmName("sortedYears")
internal fun List<YearAlbums>.sortedFor(sort: LibrarySort): List<YearAlbums> =
    if (sort == LibrarySort.AlbumCount) sortedByDescending { it.albums.size } else this

/** Genres by name, or by album or track count (most first, ties by name). */
@JvmName("sortedGenres")
internal fun List<Genre>.sortedFor(sort: LibrarySort): List<Genre> {
    val byName = compareBy(nameComparator()) { genre: Genre -> genre.sortName }.thenBy { it.id.itemId }
    return sortedWith(
        when (sort) {
            LibrarySort.AlbumCount -> compareByDescending<Genre> { it.albumCount }.then(byName)
            LibrarySort.TrackCount -> compareByDescending<Genre> { it.trackCount }.then(byName)
            else -> byName
        },
    )
}

/** Record labels by name, or by album count (most first, ties by name). */
@JvmName("sortedRecordLabels")
internal fun List<RecordLabel>.sortedFor(sort: LibrarySort): List<RecordLabel> {
    val byName = compareBy(nameComparator()) { label: RecordLabel -> label.sortName }.thenBy { it.id.itemId }
    return sortedWith(
        if (sort ==
            LibrarySort.AlbumCount
        ) {
            compareByDescending<RecordLabel> { it.albumCount }.then(byName)
        } else {
            byName
        },
    )
}
