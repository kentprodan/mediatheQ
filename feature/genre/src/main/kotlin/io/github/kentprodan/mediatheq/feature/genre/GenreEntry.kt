package io.github.kentprodan.mediatheq.feature.genre

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AddToPlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.GenreKey
import io.github.kentprodan.mediatheq.core.navigation.GenreTopTracksKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/** Registers the genre page (#130) and its "Show all" top tracks (#134). */
fun EntryProviderScope<NavKey>.genreEntry(navigator: Navigator) {
    entry<GenreKey> { key ->
        GenreRoute(
            key.id,
            onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
            onOpenArtist = { navigator.navigateTo(ArtistKey(it)) },
            onOpenTopTracks = { navigator.navigateTo(GenreTopTracksKey(key.id)) },
            onAddToPlaylist = { navigator.navigateTo(AddToPlaylistKey(it)) },
            onBack = { navigator.back() },
        )
    }
    entry<GenreTopTracksKey> { key -> GenreTopTracksRoute(key.id, onBack = { navigator.back() }) }
}
