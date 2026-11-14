package io.github.kentprodan.mediatheq.feature.video

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.designsystem.preview.MqPreviews
import io.github.kentprodan.mediatheq.core.designsystem.theme.MediatheqTheme
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoFormat
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.model.VideoRange
import kotlin.time.Duration.Companion.minutes

private fun id(value: String) = MediaId(ServerId("s"), value)

private val sdrMovie =
    Video(
        id("m1"),
        VideoKind.Movie,
        "Moșoi: Tribul Mic",
        year = 2019,
        duration = 98.minutes,
        officialRating = "PG",
        tagline = "Un trib mic, o poveste mare.",
        overview = "A small tribe, a long road and a summer that changes everything.",
        genres = listOf("Comedy", "Drama"),
        format = VideoFormat("h264", 1920, 1080),
    )

@Composable
private fun Movie(state: MovieUiState) = MediatheqTheme { MovieScreen(state, {}, {}, {}) }

/** A movie never started: Play. */
@PreviewTest
@MqPreviews
@Composable
fun MovieSdrScreenshot() =
    Movie(MovieUiState.Content(sdrMovie, format = "1080p · H.264 · Stereo", externalSubtitles = 0, resumeAt = null))

/** A 4K HDR10 movie left halfway, favorite, with subtitle files: Resume and Play from start. */
@PreviewTest
@MqPreviews
@Composable
fun MovieHdrScreenshot() =
    Movie(
        MovieUiState.Content(
            sdrMovie.copy(
                format = VideoFormat("hevc", 3840, 2160, VideoRange.Hdr10),
                userData = UserData(isFavorite = true, playbackPosition = 41.minutes),
            ),
            format = "4K · HEVC · HDR10 · 5.1",
            externalSubtitles = 2,
            resumeAt = 41.minutes,
        ),
    )

@PreviewTest
@MqPreviews
@Composable
fun MovieErrorScreenshot() = Movie(MovieUiState.Error(MqError.NotFound))

private val season1 = Season(id("se1"), id("sr1"), "Season 1", 1)
private val season2 = Season(id("se2"), id("sr1"), "Season 2", 2)

private fun episode(
    n: Int,
    userData: UserData = UserData(),
) = Video(
    id("e$n"),
    VideoKind.Episode,
    "Episode title $n",
    duration = 44.minutes,
    seriesId = id("sr1"),
    seasonId = season2.id,
    seasonNumber = 2,
    episodeNumber = n,
    userData = userData,
)

private val episodes =
    listOf(
        episode(1, UserData(played = true)),
        episode(2, UserData(playbackPosition = 20.minutes)),
        episode(3),
        episode(4),
    )

@Composable
private fun SeriesPage(state: SeriesUiState) = MediatheqTheme { SeriesScreen(state, {}, { _, _ -> }, {}) }

/** Two seasons, the second selected; Next up is the episode left halfway; watched, halfway and new rows. */
@PreviewTest
@MqPreviews
@Composable
fun SeriesTwoSeasonsScreenshot() =
    SeriesPage(
        SeriesUiState.Content(
            Series(id("sr1"), "Show", year = 2021, overview = "Two friends, one van, every road in the country."),
            listOf(season1, season2),
            season2,
            episodes,
            nextUp = episodes[1],
        ),
    )

/** A series with no episodes yet. */
@PreviewTest
@MqPreviews
@Composable
fun SeriesEmptyScreenshot() =
    SeriesPage(SeriesUiState.Content(Series(id("sr1"), "Show"), emptyList(), null, emptyList(), nextUp = null))

@PreviewTest
@MqPreviews
@Composable
fun SeriesLoadingScreenshot() = SeriesPage(SeriesUiState.Loading)
