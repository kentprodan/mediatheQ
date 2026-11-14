package io.github.kentprodan.mediatheq.feature.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.Season
import io.github.kentprodan.mediatheq.core.model.Series
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.TrackChoice
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.navigation.VideoPlayerKey
import io.github.kentprodan.mediatheq.core.playback.NowPlayingItem
import io.github.kentprodan.mediatheq.core.playback.PlayerUiModel
import io.github.kentprodan.mediatheq.core.testing.FakePlaybackConnection
import io.github.kentprodan.mediatheq.core.testing.FakeTrackChoiceRepository
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

/** The tracks sheet's choices from the player, the movie page and an episode's menu (#274). */
class TrackChoiceViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun id(value: String) = MediaId(ServerId("s"), value)

    private val streams =
        listOf(
            MediaStreamInfo(1, StreamType.Audio, "eac3", "eng", isDefault = true),
            MediaStreamInfo(2, StreamType.Audio, "aac", "rum"),
            MediaStreamInfo(3, StreamType.Subtitle, "subrip", "eng"),
            MediaStreamInfo(4, StreamType.Subtitle, "subrip", "rum", isExternal = true),
        )
    private val videos = FakeVideoRepository()
    private val choices = FakeTrackChoiceRepository()
    private val picker = TrackPicker(videos, choices)
    private val movie = Video(id("m1"), VideoKind.Movie, "Moșoi", duration = 90.minutes)
    private val season = Season(id("se1"), id("sr1"), "Season 1", 1)
    private val episode =
        Video(id("e1"), VideoKind.Episode, "Pilot", seriesId = id("sr1"), seasonId = season.id, episodeNumber = 1)

    private fun test(block: suspend TestScope.() -> Unit) =
        runTest(mainDispatcherRule.testDispatcher) {
            videos.streams.value = mapOf(movie.id to streams, episode.id to streams)
            block()
        }

    @Test
    fun `choosing a subtitle in the player saves it for the video and plays on with it`() =
        test {
            videos.movies.value = listOf(movie)
            val playback = FakePlaybackConnection()
            playback.state.value =
                PlayerUiModel(item = NowPlayingItem(movie.id, "Moșoi", "", isVideo = true), isPlaying = true)
            val viewModel = VideoPlayerViewModel(VideoPlayerKey(movie.id, attach = true), videos, playback, picker)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            viewModel.onAction(VideoPlayerAction.ChooseSubtitle(4))
            runCurrent()

            val saved = choices.choices.value[movie.id]
            assertThat(saved?.subtitleIndex).isEqualTo(4)
            assertThat(saved?.subtitleLanguage).isEqualTo("rum")
            assertThat(playback.commands).contains("reloadVideo")
        }

    @Test
    fun `the movie page's sheet saves an audio choice with its language, without playing`() =
        test {
            videos.movies.value = listOf(movie)
            val viewModel = MovieViewModel(movie.id, videos, FakeUserDataRepository(), picker)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            viewModel.onAction(MovieAction.ChooseAudio(2))
            runCurrent()

            val saved = choices.choices.value[movie.id]
            assertThat(saved?.audioIndex).isEqualTo(2)
            assertThat(saved?.audioLanguage).isEqualTo("rum")
            assertThat(saved?.subtitleOff).isTrue()
        }

    @Test
    fun `an episode's choice for the whole season is saved on the season, and the episode's own goes`() =
        test {
            videos.series.value = listOf(Series(id("sr1"), "Show"))
            videos.seriesSeasons.value = mapOf(id("sr1") to listOf(season))
            videos.seasonEpisodes.value = mapOf(season.id to listOf(episode))
            choices.save(episode.id, TrackChoice(audioIndex = 1))
            val viewModel = SeriesViewModel(id("sr1"), videos, FakeUserDataRepository(), picker)
            backgroundScope.launch { viewModel.uiState.collect() }
            runCurrent()

            viewModel.onAction(SeriesAction.OpenTracks(episode))
            runCurrent()
            viewModel.onAction(SeriesAction.WholeSeason(true))
            runCurrent()

            assertThat(choices.choices.value[season.id]).isNotNull()
            assertThat(choices.choices.value[episode.id]).isNull()
        }
}
