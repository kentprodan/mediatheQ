package io.github.kentprodan.mediatheq.feature.tv.library

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumKey
import io.github.kentprodan.mediatheq.core.navigation.ArtistKey
import io.github.kentprodan.mediatheq.core.navigation.GenreKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.Navigator
import io.github.kentprodan.mediatheq.core.navigation.PlaylistKey
import io.github.kentprodan.mediatheq.core.navigation.YearKey

/** Registers the TV Library (#237) and the genre and year pages it leads to. */
fun EntryProviderScope<NavKey>.tvLibraryEntries(navigator: Navigator) {
    val navigation =
        TvLibraryNavigation(
            onOpenAlbum = { navigator.navigateTo(AlbumKey(it)) },
            onOpenArtist = { navigator.navigateTo(ArtistKey(it)) },
            onOpenPlaylist = { navigator.navigateTo(PlaylistKey(it)) },
            onOpenGenre = { navigator.navigateTo(GenreKey(it)) },
        )
    entry<LibraryKey> { TvLibraryRoute(navigation) }
    entry<GenreKey> { TvLibraryPageRoute(navigation, genre = it.id) }
    entry<YearKey> { TvLibraryPageRoute(navigation, year = it) }
}
