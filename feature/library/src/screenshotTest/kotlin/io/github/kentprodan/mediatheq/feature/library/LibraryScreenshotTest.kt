package io.github.kentprodan.mediatheq.feature.library

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.LibraryFilter
import io.github.kentprodan.mediatheq.core.data.LibrarySort
import io.github.kentprodan.mediatheq.core.data.MediaListPreferences
import io.github.kentprodan.mediatheq.core.data.TopButton
import io.github.kentprodan.mediatheq.core.data.TopButtonStyle
import io.github.kentprodan.mediatheq.core.data.YearAlbums
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.navigation.YearKey
import io.github.kentprodan.mediatheq.core.ui.ListSelection
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
// The count the ViewModel would give, so the header shows it and enables its buttons.
private fun Library(state: LibraryUiState) =
    MediatheqTheme {
        LibraryScreen(
            state.copy(itemCount = state.content.itemCount),
            onAction = {},
            onOpenAlbum = {},
            onOpenArtist = {},
            onOpenPlaylist = {},
        )
    }

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
fun LibraryLoadingScreenshot() = Library(LibraryUiState(content = LibraryContent.Loading))

@PreviewTest
@MqPreviews
@Composable
fun LibraryEmptyScreenshot() = Library(LibraryUiState(content = LibraryContent.Empty))

@PreviewTest
@MqPreviews
@Composable
fun LibraryOfflineScreenshot() = Library(LibraryUiState(content = LibraryContent.Offline))

/** Offline mode with nothing downloaded for the list (#63); with downloads, the tab lists them as usual. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryNothingDownloadedScreenshot() = Library(LibraryUiState(content = LibraryContent.NothingDownloaded))

@PreviewTest
@MqPreviews
@Composable
fun LibraryErrorScreenshot() = Library(LibraryUiState(content = LibraryContent.Error(MqError.Server(500))))

/** The list header with each top button style (#123): Normal (icon and label) here, with a count. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryHeaderNormalButtonsScreenshot() =
    Library(
        LibraryUiState(
            content = LibraryContent.Albums(albums),
            itemCount = albums.size,
            header = MediaListPreferences(topButtonStyle = TopButtonStyle.Normal),
        ),
    )

/** Large icons, two buttons hidden, inverted sort arrow, the filter on and search in list open. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryHeaderLargeIconsScreenshot() =
    Library(
        LibraryUiState(
            filter = LibraryFilter.Songs,
            content = LibraryContent.Songs(songs),
            itemCount = songs.size,
            listFilter = LibraryListFilter(favoritesOnly = true, search = "mo"),
            header =
                MediaListPreferences(
                    topButtonStyle = TopButtonStyle.LargeIcons,
                    hiddenTopButtons = setOf(TopButton.PlayNext, TopButton.AddToQueue),
                    invertedSortArrows = true,
                ),
        ),
    )

/** No top buttons, no count; a list that is not a tab shows back. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryHeaderHiddenButtonsScreenshot() =
    MediatheqTheme {
        LibraryScreen(
            LibraryUiState(
                filter = LibraryFilter.Albums,
                content = LibraryContent.Albums(albums),
                itemCount = albums.size,
                header = MediaListPreferences(topButtonStyle = TopButtonStyle.Hidden, showItemCounts = false),
            ),
            onAction = {},
            onOpenAlbum = {},
            onOpenArtist = {},
            onOpenPlaylist = {},
            onBack = {},
        )
    }

/** The filter or search leaves nothing. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryNoMatchesScreenshot() =
    Library(LibraryUiState(content = LibraryContent.NoMatches, listFilter = LibraryListFilter(search = "zzz")))

/** Selecting (#123): two albums checked, the selection toolbar at the bottom. */
@PreviewTest
@MqPreviews
@Composable
fun LibrarySelectionScreenshot() =
    Library(
        LibraryUiState(
            content = LibraryContent.Albums(albums),
            selection = ListSelection(setOf(albums[0].id, albums[3].id)),
        ),
    )

/** Selecting songs: checked rows, no menus. */
@PreviewTest
@MqPreviews
@Composable
fun LibrarySongsSelectionScreenshot() =
    Library(
        LibraryUiState(
            filter = LibraryFilter.Songs,
            content = LibraryContent.Songs(songs),
            selection = ListSelection(setOf(songs[1].id)),
        ),
    )

/** The Years list (#129): years newest first with their album counts, Unknown year last; sort by year. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryYearsScreenshot() =
    Library(
        LibraryUiState(
            filter = LibraryFilter.Years,
            sort = LibrarySort.Year,
            content =
                LibraryContent.Years(
                    listOf(
                        YearAlbums(2024, albums.take(3)),
                        YearAlbums(2019, albums.take(1)),
                        YearAlbums(1994, albums.take(5)),
                        YearAlbums(null, albums.take(2)),
                    ),
                ),
        ),
    )

/** A year's page (#129): the year as title, its albums, Back. */
@PreviewTest
@MqPreviews
@Composable
fun LibraryYearPageScreenshot() =
    MediatheqTheme {
        LibraryScreen(
            LibraryUiState(yearPage = YearKey(1994), content = LibraryContent.Albums(albums.take(4)), itemCount = 4),
            onAction = {},
            onOpenAlbum = {},
            onOpenArtist = {},
            onOpenPlaylist = {},
            onBack = {},
        )
    }
