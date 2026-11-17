package io.github.kentprodan.mediatheq.feature.tv.video

import com.google.common.truth.Truth.assertThat
import io.github.kentprodan.mediatheq.core.common.MqError
import io.github.kentprodan.mediatheq.core.data.TrackPicker
import io.github.kentprodan.mediatheq.core.model.MediaId
import io.github.kentprodan.mediatheq.core.model.MediaStreamInfo
import io.github.kentprodan.mediatheq.core.model.ServerId
import io.github.kentprodan.mediatheq.core.model.StreamType
import io.github.kentprodan.mediatheq.core.model.UserData
import io.github.kentprodan.mediatheq.core.model.Video
import io.github.kentprodan.mediatheq.core.model.VideoKind
import io.github.kentprodan.mediatheq.core.testing.FakeTrackChoiceRepository
import io.github.kentprodan.mediatheq.core.testing.FakeUserDataRepository
import io.github.kentprodan.mediatheq.core.testing.FakeVideoRepository
import io.github.kentprodan.mediatheq.core.testing.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class TvMovieViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val videos = FakeVideoRepository()
    private val userData = FakeUserDataRepository()
    private val choices = FakeTrackChoiceRepository()
    private val id = MediaId(ServerId("s"), "m1")

    private fun TestScope.viewModel(): TvMovieViewModel {
        val viewModel = TvMovieViewModel(id, videos, userData, TrackPicker(videos, choices))
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { block() }

    @Test
    fun `a started movie offers Resume, Favorite and Watched write, a subtitle choice is kept for the movie`() =
        test {
            videos.movies.value =
                listOf(
                    Video(
                        id,
                        VideoKind.Movie,
                        "Alien",
                        duration = 100.minutes,
                        userData = UserData(playbackPosition = 30.minutes),
                    ),
                )
            videos.streams.value =
                mapOf(
                    id to
                        listOf(
                            MediaStreamInfo(1, StreamType.Audio, language = "eng", isDefault = true),
                            MediaStreamInfo(2, StreamType.Subtitle, language = "ron"),
                        ),
                )
            val viewModel = viewModel()

            val content = viewModel.uiState.value as TvMovieUiState.Content
            assertThat(content.resumeAt).isEqualTo(30.minutes)
            assertThat(content.tracks?.streams).hasSize(2)

            viewModel.onAction(TvMovieAction.ToggleFavorite)
            viewModel.onAction(TvMovieAction.ToggleWatched)
            viewModel.onAction(TvMovieAction.ChooseSubtitle(2))
            runCurrent()

            assertThat(userData.calls).containsExactly("favorite:m1:true", "played:m1:true").inOrder()
            assertThat(choices.choices.value[id]?.subtitleIndex).isEqualTo(2)
        }

    @Test
    fun `a movie not in the library is not found`() =
        test {
            assertThat(viewModel().uiState.value).isEqualTo(TvMovieUiState.Error(MqError.NotFound))
        }
}
