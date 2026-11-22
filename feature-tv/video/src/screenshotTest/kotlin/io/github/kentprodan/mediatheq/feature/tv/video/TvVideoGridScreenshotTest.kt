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
        TvVideoGridScreen(kind, state, {}, {})
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

/** Movies as tabs (#522): All selected over its grid. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvMoviesTabsScreenshot() {
    val arranged = arrange(posters, TvVideoSort.Name, TvVideoFilter(), 0)
    val grid =
        TvVideoGridUiState.Content(
            arranged.posters,
            arranged.letters,
            TvVideoSort.Name,
            TvVideoFilter(),
            genres = emptyList(),
            decades = emptyList(),
            total = posters.size,
        )
    MediatheqTvTheme {
        Surface {
            TvVideoTabsRoute(TvVideoKind.Movies, TvVideoNavigation()) { _, _ ->
                TvVideoGridScreen(TvVideoKind.Movies, grid, {}, {})
            }
        }
    }
}

/** The Series Networks tab (#522): a card per network with its count. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvSeriesNetworksScreenshot() =
    MediatheqTvTheme {
        Surface {
            TvVideoTabsRoute(TvVideoKind.Series, TvVideoNavigation(), TvVideoTab.Studios) { _, _ ->
                TvVideoGroups(
                    TvVideoKind.Series,
                    TvVideoGroupsUiState.Groups(
                        listOf(TvVideoGroup("AMC", 3), TvVideoGroup("BBC One", 12), TvVideoGroup("HBO", 1)),
                    ),
                    studios = true,
                    restoreFocus = false,
                ) {}
            }
        }
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

/** A started movie's page: Resume, From the start, the facts and the format. No backdrop: layoutlib loads no image. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvMovieScreenshot() =
    MediatheqTvTheme {
        Surface {
            TvMovieScreen(
                TvMovieUiState.Content(
                    io.github.kentprodan.mediatheq.core.model.Video(
                        MediaId(ServerId("s"), "m1"),
                        io.github.kentprodan.mediatheq.core.model.VideoKind.Movie,
                        "Blade Runner",
                        overview = "A blade runner must pursue and terminate four replicants.",
                        year = 1982,
                        duration = kotlin.time.Duration.parse("1h57m"),
                        genres = listOf("Science Fiction", "Drama"),
                        officialRating = "R",
                        communityRating = 7.9,
                    ),
                    format = "4K · HEVC · HDR10 · 5.1",
                    resumeAt = kotlin.time.Duration.parse("42m10s"),
                ),
                {},
                { _, _ -> },
            )
        }
    }

/** A series page: Resume S2 · E1, season tabs with Specials last, the episodes; no images in layoutlib. */
@PreviewTest
@MqTvPreviews
@Composable
fun TvSeriesPageScreenshot() {
    fun id(value: String) = MediaId(ServerId("s"), value)
    val show =
        io.github.kentprodan.mediatheq.core.model.Series(
            id("sr"),
            "Severance",
            year = 2022,
            communityRating = 8.7,
        )
    val one =
        io.github.kentprodan.mediatheq.core.model
            .Season(id("s1"), show.id, "Season 1", 1)
    val two =
        io.github.kentprodan.mediatheq.core.model
            .Season(id("s2"), show.id, "Season 2", 2)
    val specials =
        io.github.kentprodan.mediatheq.core.model
            .Season(id("s0"), show.id, "Specials", 0)
    val episodes =
        (1..4).map {
            io.github.kentprodan.mediatheq.core.model.Video(
                id("e$it"),
                io.github.kentprodan.mediatheq.core.model.VideoKind.Episode,
                "Episode $it",
                duration = kotlin.time.Duration.parse("52m"),
                seasonNumber = 2,
                episodeNumber = it,
                userData =
                    io.github.kentprodan.mediatheq.core.model
                        .UserData(played = it == 1),
            )
        }
    MediatheqTvTheme {
        Surface {
            TvSeriesPageScreen(
                TvSeriesPageUiState.Content(show, listOf(one, two, specials), two, episodes, episodes[1], episodes[0]),
                {},
                { _, _ -> },
            )
        }
    }
}
