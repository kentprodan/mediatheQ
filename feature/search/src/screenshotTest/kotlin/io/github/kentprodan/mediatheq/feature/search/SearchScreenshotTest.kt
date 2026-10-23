package io.github.kentprodan.mediatheq.feature.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.SearchResults
import io.github.kentprodan.mediatheq.core.ui.preview.PreviewData

@Composable
private fun Search(
    state: SearchUiState,
    query: String = "",
) = MediatheqTheme {
    SearchScreen(state, {}, {}, {}, {}, textFieldState = TextFieldState(query))
}

@PreviewTest
@MqPreviews
@Composable
fun SearchLoadingScreenshot() = Search(SearchUiState.Loading)

@PreviewTest
@MqPreviews
@Composable
fun SearchRecentScreenshot() =
    Search(SearchUiState.Recent(listOf("mosoi", "Subcarpați", "a very long search that must ellipsize")))

@PreviewTest
@MqPreviews
@Composable
fun SearchStartScreenshot() = Search(SearchUiState.Recent(emptyList()))

@PreviewTest
@MqPreviews
@Composable
fun SearchResultsScreenshot() =
    Search(
        SearchUiState.Results(
            SearchResults(
                artists = listOf(PreviewData.artist.copy(artwork = PreviewData.artwork)),
                albums = listOf(PreviewData.album.copy(artwork = PreviewData.artwork), PreviewData.longAlbum),
                tracks = listOf(PreviewData.track.copy(artwork = PreviewData.artwork), PreviewData.longTrack),
                playlists = listOf(PreviewData.playlist),
            ),
        ),
        query = "mos",
    )

@PreviewTest
@MqPreviews
@Composable
fun SearchNoResultsScreenshot() = Search(SearchUiState.NoResults("zzz"), query = "zzz")

@PreviewTest
@MqPreviews
@Composable
fun SearchErrorScreenshot() = Search(SearchUiState.Error(MqError.Storage), query = "mos")
