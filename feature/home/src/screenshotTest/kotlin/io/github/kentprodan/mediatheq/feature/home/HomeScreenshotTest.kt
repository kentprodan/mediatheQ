package io.github.kentprodan.mediatheq.feature.home

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.HomeItems
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.HomeDisplay
import io.github.kentprodan.mediatheq.core.model.HomeSection
import io.github.kentprodan.mediatheq.core.model.HomeSectionSettings
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

@Composable
private fun Home(state: HomeUiState) =
    MediatheqTheme {
        HomeScreen(state, {}, HomeNavigation())
    }

private fun album(n: Int) =
    PreviewData.album.copy(
        id = PreviewData.album.id.copy(itemId = "album-$n"),
        title = "Album $n",
        artwork = PreviewData.artwork,
    )

private val albums = listOf(album(1), PreviewData.longAlbum, album(3), album(4))

private fun section(
    section: HomeSection,
    items: HomeItems,
    settings: HomeSectionSettings = section.defaultSettings,
) = HomeSectionContent(section, settings, items)

private val artists =
    listOf(
        PreviewData.artist.copy(artwork = PreviewData.artwork),
        PreviewData.longTrack.artists[1],
        PreviewData.artist.copy(id = PreviewData.artist.id.copy(itemId = "artist-3"), name = "Bo"),
    )

private val tracks = listOf(PreviewData.track.copy(artwork = PreviewData.artwork), PreviewData.longTrack)

/** Home with the default sections that have content (#127): rows, a two-row grid, a carousel, track lists. */
@PreviewTest
@MqPreviews
@Composable
fun HomeFullScreenshot() =
    Home(
        HomeUiState.Content(
            listOf(
                section(HomeSection.FavoritePlaylists, HomeItems.Playlists(listOf(PreviewData.playlist))),
                section(HomeSection.FavoriteAlbums, HomeItems.Albums(listOf(album(3)))),
                section(HomeSection.FavoriteTracks, HomeItems.Tracks(tracks)),
                section(HomeSection.FavoriteArtists, HomeItems.Artists(artists)),
                section(
                    HomeSection.RecentlyAddedAlbums,
                    HomeItems.Albums(albums + albums.map { it.copy(id = it.id.copy(itemId = it.id.itemId + "b")) }),
                ),
                section(HomeSection.RediscoverAlbums, HomeItems.Albums(albums)),
            ),
        ),
    )

/** Each display mode (#127): Row, Grid with 1, 2 and 3 rows, Carousel, List. */
@PreviewTest
@Preview(name = "Light", device = "spec:width=412dp,height=2400dp,dpi=420")
@Preview(name = "Dark", device = "spec:width=412dp,height=2400dp,dpi=420", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun HomeDisplayModesScreenshot() {
    val six = albums + albums.take(2).map { it.copy(id = it.id.copy(itemId = it.id.itemId + "b")) }
    val row = HomeSectionSettings(display = HomeDisplay.Row)
    Home(
        HomeUiState.Content(
            listOf(
                section(HomeSection.LastPlayedAlbums, HomeItems.Albums(six), row),
                section(
                    HomeSection.RandomAlbums,
                    HomeItems.Albums(six),
                    row.copy(display = HomeDisplay.Grid, gridRows = 1),
                ),
                section(
                    HomeSection.RecentlyAddedAlbums,
                    HomeItems.Albums(six),
                    row.copy(display = HomeDisplay.Grid, gridRows = 2),
                ),
                section(
                    HomeSection.MostPlayedAlbums,
                    HomeItems.Albums(six),
                    row.copy(display = HomeDisplay.Grid, gridRows = 3),
                ),
                section(HomeSection.RediscoverAlbums, HomeItems.Albums(six), row.copy(display = HomeDisplay.Carousel)),
                section(HomeSection.MostPlayedTracks, HomeItems.Tracks(tracks), row.copy(display = HomeDisplay.List)),
            ),
        ),
    )
}

/** Every section hidden: Shuffle all only, not an empty library. */
@PreviewTest
@MqPreviews
@Composable
fun HomeNoSectionsScreenshot() = Home(HomeUiState.Content())

@PreviewTest
@MqPreviews
@Composable
fun HomeFirstRunScreenshot() = Home(HomeUiState.FirstSync)

@PreviewTest
@MqPreviews
@Composable
fun HomeEmptyScreenshot() = Home(HomeUiState.Empty)

@PreviewTest
@MqPreviews
@Composable
fun HomeOfflineScreenshot() = Home(HomeUiState.Error(MqError.Network))

/** Offline mode with nothing downloaded (#63); with downloads, Home shows them like [HomeFullScreenshot]. */
@PreviewTest
@MqPreviews
@Composable
fun HomeNothingDownloadedScreenshot() = Home(HomeUiState.NothingDownloaded)

@PreviewTest
@MqPreviews
@Composable
fun HomeErrorScreenshot() = Home(HomeUiState.Error(MqError.Server(500)))

@PreviewTest
@MqPreviews
@Composable
fun HomeLoadingScreenshot() = Home(HomeUiState.Loading)

/** The Shortcuts section (#128): Track, Album and Decade mix cards, above an album row. */
@PreviewTest
@MqPreviews
@Composable
fun HomeShortcutsScreenshot() =
    Home(
        HomeUiState.Content(
            listOf(
                section(HomeSection.Shortcuts, HomeItems.Shortcuts(listOf(1980, 1990))),
                section(HomeSection.FavoriteAlbums, HomeItems.Albums(albums)),
            ),
        ),
    )

/** The Decade mix picker's content (#128); the sheet around it is a popup, which screenshots cannot render. */
@PreviewTest
@MqPreviews
@Composable
fun DecadePickerScreenshot() =
    MediatheqTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            DecadeList(listOf(1960, 1970, 1980, 1990, 2000, 2010, 2020), {})
        }
    }
