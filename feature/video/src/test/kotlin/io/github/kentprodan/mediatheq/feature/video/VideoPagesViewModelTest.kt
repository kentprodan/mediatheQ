package io.github.kentprodan.mediatheq.feature.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoFormat
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.model.VideoRange
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class VideoPagesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val videos = FakeVideoRepository()
    private val userData = FakeUserDataRepository()

    private val movie =
        Video(
            id("m1"),
            VideoKind.Movie,
            "Moșoi",
            year = 2019,
            duration = 98.minutes,
            format = VideoFormat("hevc", 3840, 2160, VideoRange.Hdr10),
            userData = UserData(playbackPosition = 41.minutes),
        )

    private val series = Series(id("sr1"), "Show")
    private val season1 = Season(id("se1"), id("sr1"), "Season 1", 1)
    private val season2 = Season(id("se2"), id("sr1"), "Season 2", 2)

    private fun episode(
        n: Int,
        season: Season,
        played: Boolean = false,
    ) = Video(
        id("${season.id.itemId}e$n"),
        VideoKind.Episode,
        "Episode $n",
        seriesId = id("sr1"),
        seasonId = season.id,
        seasonNumber = season.number,
        episodeNumber = n,
        userData = UserData(played = played),
    )

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    private fun TestScope.movieViewModel(movieId: MediaId = movie.id) =
        MovieViewModel(movieId, videos, userData).also { vm ->
            backgroundScope.launch { vm.uiState.collect() }
            runCurrent()
        }

    private fun TestScope.seriesViewModel() =
        SeriesViewModel(series.id, videos, userData).also { vm ->
            backgroundScope.launch { vm.uiState.collect() }
            runCurrent()
        }

    @Test
    fun `a movie shows its facts, format line and resume point`() =
        test {
            videos.movies.value = listOf(movie)

            val content = movieViewModel().uiState.value as MovieUiState.Content

            assertThat(content.movie).isEqualTo(movie)
            assertThat(content.format).isEqualTo("4K · HEVC · HDR10")
            assertThat(content.resumeAt).isEqualTo(41.minutes)
        }

    @Test
    fun `a watched movie has no resume point`() =
        test {
            videos.movies.value = listOf(movie.copy(userData = UserData(played = true, playbackPosition = 41.minutes)))

            assertThat((movieViewModel().uiState.value as MovieUiState.Content).resumeAt).isNull()
        }

    @Test
    fun `a movie not in the library is an error`() =
        test {
            assertThat(movieViewModel(id("gone")).uiState.value).isEqualTo(MovieUiState.Error(MqError.NotFound))
        }

    @Test
    fun `favorite and watched toggle the movie's own values`() =
        test {
            videos.movies.value = listOf(movie)
            val viewModel = movieViewModel()

            viewModel.onAction(MovieAction.ToggleFavorite)
            viewModel.onAction(MovieAction.ToggleWatched)
            runCurrent()

            assertThat(userData.calls).containsExactly("favorite:m1:true", "played:m1:true").inOrder()
        }

    @Test
    fun `the format line names resolution, codec, range and channels`() {
        val streams = listOf(MediaStreamInfo(1, StreamType.Audio, "eac3", channels = 6, isDefault = true))

        assertThat(formatLine(movie, streams)).isEqualTo("4K · HEVC · HDR10 · 5.1")
        assertThat(
            formatLine(movie.copy(format = VideoFormat("h264", 1920, 1080)), emptyList()),
        ).isEqualTo("1080p · H.264")
        assertThat(formatLine(movie.copy(format = null), emptyList())).isNull()
        assertThat(
            externalSubtitles(
                listOf(
                    MediaStreamInfo(2, StreamType.Subtitle, isExternal = true),
                    MediaStreamInfo(3, StreamType.Subtitle),
                ),
            ),
        ).isEqualTo(1)
    }

    @Test
    fun `a series opens on Next up's season with its episodes`() =
        test {
            videos.series.value = listOf(series)
            videos.seriesSeasons.value = mapOf(series.id to listOf(season1, season2))
            videos.seasonEpisodes.value =
                mapOf(
                    season1.id to listOf(episode(1, season1, played = true), episode(2, season1, played = true)),
                    season2.id to listOf(episode(1, season2), episode(2, season2)),
                )

            val content = seriesViewModel().uiState.value as SeriesUiState.Content

            assertThat(content.season).isEqualTo(season2)
            assertThat(content.episodes.map { it.title }).containsExactly("Episode 1", "Episode 2").inOrder()
            assertThat(content.nextUp?.id).isEqualTo(id("se2e1"))
        }

    @Test
    fun `choosing a season tab shows its episodes`() =
        test {
            videos.series.value = listOf(series)
            videos.seriesSeasons.value = mapOf(series.id to listOf(season1, season2))
            videos.seasonEpisodes.value =
                mapOf(season1.id to listOf(episode(1, season1)), season2.id to listOf(episode(9, season2)))
            val viewModel = seriesViewModel()

            viewModel.onAction(SeriesAction.SelectSeason(season2.id))
            runCurrent()

            val content = viewModel.uiState.value as SeriesUiState.Content
            assertThat(content.season).isEqualTo(season2)
            assertThat(content.episodes.single().title).isEqualTo("Episode 9")
        }

    @Test
    fun `a series without seasons is empty, and a missing one an error`() =
        test {
            videos.series.value = listOf(series)

            val content = seriesViewModel().uiState.value as SeriesUiState.Content
            assertThat(content.seasons).isEmpty()
            assertThat(content.episodes).isEmpty()

            videos.series.value = emptyList()
            runCurrent()
        }

    @Test
    fun `an episode's watched mark and the series' favorite toggle`() =
        test {
            videos.series.value = listOf(series)
            val viewModel = seriesViewModel()

            viewModel.onAction(SeriesAction.ToggleWatched(episode(3, season1, played = true)))
            viewModel.onAction(SeriesAction.ToggleFavorite)
            runCurrent()

            assertThat(userData.calls).containsExactly("played:se1e3:false", "favorite:sr1:true").inOrder()
        }

    @Test
    fun `marking Next up watched never switches the season tab`() =
        test {
            videos.series.value = listOf(series)
            videos.seriesSeasons.value = mapOf(series.id to listOf(season1, season2))
            videos.seasonEpisodes.value =
                mapOf(season1.id to listOf(episode(1, season1)), season2.id to listOf(episode(1, season2)))
            val viewModel = seriesViewModel()
            assertThat((viewModel.uiState.value as SeriesUiState.Content).season).isEqualTo(season1)

            videos.seasonEpisodes.value =
                mapOf(
                    season1.id to listOf(episode(1, season1, played = true)),
                    season2.id to listOf(episode(1, season2)),
                )
            runCurrent()

            val content = viewModel.uiState.value as SeriesUiState.Content
            assertThat(content.nextUp?.id).isEqualTo(id("se2e1"))
            assertThat(content.season).isEqualTo(season1)
        }
}
