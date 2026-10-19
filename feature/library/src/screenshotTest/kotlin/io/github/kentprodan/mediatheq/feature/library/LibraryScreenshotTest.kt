package io.github.kentprodan.mediatheq.feature.library

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.FilterControlStyle
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

private val albums =
    List(8) { i ->
        val base = if (i % 2 == 0) PreviewData.album else PreviewData.longAlbum
        base.copy(id = MediaId(base.id.serverId, "album-$i"), artwork = PreviewData.artwork.takeIf { i % 3 != 2 })
    }
private val artists =
    List(5) { i ->
        PreviewData.artist.copy(
            id = MediaId(PreviewData.artist.id.serverId, "artist-$i"),
            artwork = PreviewData.artwork.takeIf { i % 2 == 0 },
        )
    }
private val playlists =
    List(4) { i -> PreviewData.playlist.copy(id = MediaId(PreviewData.playlist.id.serverId, "p-$i")) }
private val songs =
    List(6) { i ->
        val base = if (i % 2 == 0) PreviewData.track else PreviewData.longTrack
        base.copy(id = MediaId(base.id.serverId, "t-$i"), artwork = PreviewData.artwork.takeIf { i % 3 != 1 })
    }

@Composable
private fun Library(state: LibraryUiState) =
    MediatheqTheme { LibraryScreen(state, onAction = {}, onOpenAlbum = {}, onOpenArtist = {}, onOpenPlaylist = {}) }

@PreviewTest
@MqPreviews
@Composable
fun LibraryAlbumsTwoColumnsScreenshot() = Library(LibraryUiState(content = LibraryContent.Albums(albums)))

@PreviewTest
@MqPreviews
@Composable
fun LibraryAlbumsThreeColumnsScreenshot() =
    Library(LibraryUiState(albumColumns = 3, sort = LibrarySort.Year, content = LibraryContent.Albums(albums)))

@PreviewTest
@MqPreviews
@Composable
fun LibraryAlbumsFourColumnsScreenshot() =
    Library(LibraryUiState(albumColumns = 4, content = LibraryContent.Albums(albums)))

@PreviewTest
@MqPreviews
@Composable
fun LibraryArtistsScreenshot() =
    Library(LibraryUiState(filter = LibraryFilter.Artists, content = LibraryContent.Artists(artists)))

@PreviewTest
@MqPreviews
@Composable
fun LibraryPlaylistsScreenshot() =
    Library(LibraryUiState(filter = LibraryFilter.Playlists, content = LibraryContent.Playlists(playlists)))

@PreviewTest
@MqPreviews
@Composable
fun LibrarySongsScreenshot() =
    Library(LibraryUiState(filter = LibraryFilter.Songs, content = LibraryContent.Songs(songs)))

@PreviewTest
@MqPreviews
@Composable
fun LibraryConnectedButtonsScreenshot() =
    Library(
        LibraryUiState(filterStyle = FilterControlStyle.ConnectedButtons, content = LibraryContent.Albums(albums)),
    )

@PreviewTest
@MqPreviews
@Composable
fun LibraryLoadingScreenshot() = Library(LibraryUiState(content = LibraryContent.Loading))

@PreviewTest
@MqPreviews
@Composable
fun LibraryEmptyScreenshot() = Library(LibraryUiState(content = LibraryContent.Empty))

@PreviewTest
@MqPreviews
@Composable
fun LibraryOfflineScreenshot() = Library(LibraryUiState(content = LibraryContent.Offline))

@PreviewTest
@MqPreviews
@Composable
fun LibraryErrorScreenshot() = Library(LibraryUiState(content = LibraryContent.Error(MqError.Server(500))))
