package io.github.kentprodan.mediatheq.feature.home

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

@Composable
private fun Home(state: HomeUiState) =
    MediatheqTheme {
        HomeScreen(state, {}, {}, {})
    }

private fun album(n: Int) =
    PreviewData.album.copy(
        id = PreviewData.album.id.copy(itemId = "album-$n"),
        title = "Album $n",
        artwork = PreviewData.artwork,
    )

private val albums = listOf(album(1), PreviewData.longAlbum, album(3), album(4))

@PreviewTest
@MqPreviews
@Composable
fun HomeFullScreenshot() =
    Home(
        HomeUiState.Content(
            recentlyPlayed = albums,
            recentlyAdded = albums.reversed(),
            favoriteAlbums = listOf(album(3)),
            favoriteTracks = listOf(PreviewData.track.copy(artwork = PreviewData.artwork), PreviewData.longTrack),
        ),
    )

@PreviewTest
@MqPreviews
@Composable
fun HomePartiallyEmptyScreenshot() =
    Home(HomeUiState.Content(recentlyAdded = albums, favoriteTracks = listOf(PreviewData.track, PreviewData.longTrack)))

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

@PreviewTest
@MqPreviews
@Composable
fun HomeErrorScreenshot() = Home(HomeUiState.Error(MqError.Server(500)))

@PreviewTest
@MqPreviews
@Composable
fun HomeLoadingScreenshot() = Home(HomeUiState.Loading)
