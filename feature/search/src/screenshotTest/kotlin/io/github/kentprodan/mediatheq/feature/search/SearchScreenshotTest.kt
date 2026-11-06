package io.github.kentprodan.mediatheq.feature.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.SearchPagePreferences
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.Genre
import io.github.kentprodan.mediatheq.core.model.RecordLabel
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

/** Offline mode searched only downloads (#63); results look as usual. */
@PreviewTest
@MqPreviews
@Composable
fun SearchNoDownloadsScreenshot() = Search(SearchUiState.NoResults("zzz", offline = true), query = "zzz")

@PreviewTest
@MqPreviews
@Composable
fun SearchErrorScreenshot() = Search(SearchUiState.Error(MqError.Storage), query = "mos")

/** Genres are a result group of their own (#130), last. */
@PreviewTest
@MqPreviews
@Composable
fun SearchGenresScreenshot() =
    Search(
        SearchUiState.Results(
            SearchResults(
                genres =
                    listOf(
                        Genre(
                            PreviewData.album.id.copy(itemId = "g1"),
                            "Electrónica",
                            albumCount = 4,
                            trackCount = 31,
                            artwork = PreviewData.artwork,
                        ),
                        Genre(PreviewData.album.id.copy(itemId = "g2"), "Folk", albumCount = 1, trackCount = 3),
                    ),
            ),
        ),
        query = "el",
    )

/** Record labels are a result group of their own (#131), after genres. */
@PreviewTest
@MqPreviews
@Composable
fun SearchRecordLabelsScreenshot() =
    Search(
        SearchUiState.Results(
            SearchResults(
                labels =
                    listOf(
                        RecordLabel(
                            PreviewData.album.id.copy(itemId = "l1"),
                            "Electrecord",
                            albumCount = 12,
                            artwork = PreviewData.artwork,
                        ),
                        RecordLabel(PreviewData.album.id.copy(itemId = "l2"), "Electra", albumCount = 1),
                    ),
            ),
        ),
        query = "ele",
    )

private val everything =
    SearchResults(
        artists = listOf(PreviewData.artist.copy(artwork = PreviewData.artwork)),
        albums = listOf(PreviewData.album.copy(artwork = PreviewData.artwork), PreviewData.longAlbum),
        tracks = listOf(PreviewData.track.copy(artwork = PreviewData.artwork), PreviewData.longTrack),
        playlists = listOf(PreviewData.playlist),
        genres =
            listOf(Genre(PreviewData.album.id.copy(itemId = "g1"), "Electrónica", albumCount = 4, trackCount = 31)),
    )

@Composable
private fun Chip(
    filter: SearchFilter,
    page: SearchPagePreferences = SearchPagePreferences(),
) = Search(SearchUiState.Results(everything, chosenFilter = filter, page = page), query = "mos")

/** Settings -> Search page: the chips wrap instead of scrolling (#136); no genre found, so Genres is disabled. */
@PreviewTest
@MqPreviews
@Composable
fun SearchChipsWrappedScreenshot() =
    Search(
        SearchUiState.Results(
            everything.copy(genres = emptyList()),
            page = SearchPagePreferences(singleLineChips = false),
        ),
        query = "mos",
    )

@PreviewTest
@MqPreviews
@Composable
fun SearchArtistsChipScreenshot() = Chip(SearchFilter.Artists)

@PreviewTest
@MqPreviews
@Composable
fun SearchAlbumsChipScreenshot() = Chip(SearchFilter.Albums)

/** Songs by title, each with its heart (the default). */
@PreviewTest
@MqPreviews
@Composable
fun SearchSongsChipScreenshot() = Chip(SearchFilter.Tracks)

@PreviewTest
@MqPreviews
@Composable
fun SearchSongsNoFavoriteScreenshot() = Chip(SearchFilter.Tracks, SearchPagePreferences(showFavorite = false))

@PreviewTest
@MqPreviews
@Composable
fun SearchPlaylistsChipScreenshot() = Chip(SearchFilter.Playlists)

@PreviewTest
@MqPreviews
@Composable
fun SearchGenresChipScreenshot() = Chip(SearchFilter.Genres)
