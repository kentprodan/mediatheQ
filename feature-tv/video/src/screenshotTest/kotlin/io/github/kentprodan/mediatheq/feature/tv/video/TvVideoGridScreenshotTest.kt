package io.github.kentprodan.mediatheq.feature.tv.video

import androidx.compose.runtime.Composable
import androidx.tv.material3.Surface
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.designsystem.tv.MediatheqTvTheme
import io.github.kentprodan.mediatheq.core.designsystem.tv.MqTvPreviews
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.ServerId

private val posters =
    listOf(
        "Alien" to 1979,
        "Arrival" to 2016,
        "Blade Runner" to 1982,
        "Brazil" to 1985,
        "Heat" to 1995,
        "Inception" to 2010,
        "Jaws" to 1975,
        "Moon" to 2009,
    ).mapIndexed { i, (title, year) ->
        TvPoster(
            MediaId(ServerId("s"), title),
            title,
            year = year,
            watched = i == 1,
            progress = if (i == 2) 0.4f else null,
        )
    }

@Composable
private fun Grid(
    kind: TvVideoKind,
    state: TvVideoGridUiState,
) = MediatheqTvTheme {
    Surface {
        TvVideoGridScreen(kind, state, {}, {}, onOpenCollections = { }.takeIf { kind == TvVideoKind.Movies })
    }
}

/** Movies by name with the A-Z strip; a watched poster and a started one. No posters: layoutlib loads no image. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvMoviesGridScreenshot() {
    val arranged = arrange(posters, TvVideoSort.Name, TvVideoFilter(), 0)
    Grid(
        TvVideoKind.Movies,
        TvVideoGridUiState.Content(
            arranged.posters,
            arranged.letters,
            TvVideoSort.Name,
            TvVideoFilter(),
            genres = emptyList(),
            decades = emptyList(),
            total = posters.size,
        ),
    )
}

/** Filtered by year with two filters on: the count says how many of the whole grid show. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvSeriesFilteredScreenshot() {
    val filter = TvVideoFilter(watched = TvWatched.Unwatched, decade = 2010)
    val arranged = arrange(posters, TvVideoSort.Year, filter, 0)
    Grid(
        TvVideoKind.Series,
        TvVideoGridUiState.Content(
            arranged.posters,
            arranged.letters,
            TvVideoSort.Year,
            filter,
            emptyList(),
            emptyList(),
            40,
        ),
    )
}

/** The filters keep nothing. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvMoviesNothingMatchesScreenshot() =
    Grid(
        TvVideoKind.Movies,
        TvVideoGridUiState.Content(
            emptyList(),
            emptyList(),
            TvVideoSort.Name,
            TvVideoFilter(favorites = true),
            emptyList(),
            emptyList(),
            8,
        ),
    )

/** Nothing synced. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvSeriesEmptyScreenshot() = Grid(TvVideoKind.Series, TvVideoGridUiState.Empty)

/** Collections: no Filter button. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvCollectionsScreenshot() {
    val arranged = arrange(posters.take(3), TvVideoSort.Name, TvVideoFilter(), 0)
    Grid(
        TvVideoKind.Collections,
        TvVideoGridUiState.Content(
            arranged.posters,
            arranged.letters,
            TvVideoSort.Name,
            TvVideoFilter(),
            emptyList(),
            emptyList(),
            3,
        ),
    )
}
