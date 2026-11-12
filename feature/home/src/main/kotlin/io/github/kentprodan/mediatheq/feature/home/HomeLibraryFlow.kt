package io.github.kentprodan.mediatheq.feature.home

import io.github.kentprodan.mediatheq.core.data.HomeLibrary
import io.github.kentprodan.mediatheq.core.data.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What Home's sections draw from (#127), for Home and for a section's settings preview (#182). */
internal fun LibraryRepository.homeLibrary(): Flow<HomeLibrary> =
    combine(
        albums(),
        albumArtists(),
        tracks(),
        allTracks(),
        playlists(),
    ) { albums, artists, tracks, allTracks, playlists ->
        HomeLibrary(albums, artists, tracks, allTracks, playlists)
    }
