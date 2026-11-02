package io.github.kentprodan.mediatheq.core.data

import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Track
import kotlinx.coroutines.flow.first

/** The tracks of [albums] in list order, each album's in play order (top buttons, selection, #123). */
suspend fun LibraryRepository.tracksOfAlbums(albums: List<MediaId>): List<Track> =
    albums.flatMap { albumTracks(it).first() }

/** The tracks of [artists] in list order; a track two of them share comes once, at its first place. */
suspend fun LibraryRepository.tracksOfArtists(artists: List<MediaId>): List<Track> =
    artists.flatMap { artistTracks(it).first() }.distinctBy { it.id }

/** The tracks of [playlists] in list order, each playlist's in its own order. */
suspend fun PlaylistRepository.tracksOfPlaylists(playlists: List<MediaId>): List<Track> =
    playlists.flatMap { id -> items(id).first().map { it.track } }
