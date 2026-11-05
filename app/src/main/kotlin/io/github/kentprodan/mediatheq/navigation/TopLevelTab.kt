package io.github.kentprodan.mediatheq.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import io.github.kentprodan.mediatheq.R
import io.github.kentprodan.mediatheq.core.data.MainNavEntry
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons
import io.github.kentprodan.mediatheq.core.navigation.AlbumArtistsKey
import io.github.kentprodan.mediatheq.core.navigation.AlbumsKey
import io.github.kentprodan.mediatheq.core.navigation.DownloadsKey
import io.github.kentprodan.mediatheq.core.navigation.GenresKey
import io.github.kentprodan.mediatheq.core.navigation.HomeKey
import io.github.kentprodan.mediatheq.core.navigation.LibraryKey
import io.github.kentprodan.mediatheq.core.navigation.PlaylistsKey
import io.github.kentprodan.mediatheq.core.navigation.SearchKey
import io.github.kentprodan.mediatheq.core.navigation.TracksKey

/**
 * The main navigation destinations, each with its own back stack starting at [rootKey]; one per [MainNavEntry] (#122),
 * shown as the user chose. [Home] is the start tab.
 */
enum class TopLevelTab(
    val rootKey: NavKey,
    @param:StringRes val label: Int,
) {
    Home(HomeKey, R.string.tab_home),
    Library(LibraryKey, R.string.tab_library),
    Albums(AlbumsKey, R.string.tab_albums),
    AlbumArtists(AlbumArtistsKey, R.string.tab_album_artists),
    Genres(GenresKey, R.string.tab_genres),
    Playlists(PlaylistsKey, R.string.tab_playlists),
    Tracks(TracksKey, R.string.tab_tracks),
    Search(SearchKey, R.string.tab_search),
    Downloads(DownloadsKey, R.string.tab_downloads),
    ;

    /**
     * Filled icon when [selected], so selection is not shown by color alone.
     * shortcut: the Library lists have no filled icon yet and rely on the bar's selection indicator; add filled
     * Material Symbols when they become common entries.
     */
    @Composable
    fun icon(selected: Boolean): ImageVector =
        when (this) {
            Home -> if (selected) MqIcons.HomeFilled else MqIcons.Home
            Library -> if (selected) MqIcons.LibraryFilled else MqIcons.Library
            Albums -> MqIcons.GridView
            AlbumArtists -> MqIcons.Person
            Genres -> MqIcons.Genres
            Playlists -> MqIcons.QueueMusic
            Tracks -> MqIcons.MusicNote
            Search -> if (selected) MqIcons.SearchFilled else MqIcons.Search
            Downloads -> if (selected) MqIcons.DownloadFilled else MqIcons.Download
        }

    companion object {
        /** The tab of a chosen navigation entry. */
        fun of(entry: MainNavEntry): TopLevelTab = valueOf(entry.name)
    }
}
