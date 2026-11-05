package io.github.kentprodan.mediatheq.feature.genre

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.GenreKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator

/** Registers the genre page (#130). */
fun EntryProviderScope<NavKey>.genreEntry(navigator: Navigator) {
    entry<GenreKey> { key ->
        GenreRoute(
            key.id,
            onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
            onOpenArtist = { navigator.navigateTo(ArtistKey(it)) },
            onBack = { navigator.back() },
        )
    }
}
