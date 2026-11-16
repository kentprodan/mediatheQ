package io.github.kentprodan.mediatheq.core.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * What Home's sections draw from (#127), for Home and for a section's settings preview (#182). Online the shown tracks
 * are all the tracks: they are read once, not twice, on every change of the library (#404).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun LibraryRepository.homeLibrary(): Flow<HomeLibrary> =
    offline.distinctUntilChanged().flatMapLatest { offline ->
        val tracks = if (offline) combine(tracks(), allTracks(), ::Pair) else allTracks().map { it to it }
        combine(albums(), albumArtists(), tracks, playlists()) { albums, artists, (shown, all), playlists ->
            HomeLibrary(albums, artists, shown, all, playlists)
        }
    }
